package com.ultimateimprovments.chat;

import com.destroystokyo.paper.event.server.ServerExceptionEvent;
import com.destroystokyo.paper.exception.ServerCommandException;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.Broadcast;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * CmdLogger — logs player commands to chat when enabled ({@code /ui cmdlog <on|off>}).
 * <p>
 * The toggle is persisted in the DB ({@code cmdlog_meta} table), so it survives
 * restarts and {@code /ui reload}. Default is <b>off</b>.
 * <p>
 * Each logged command produces two chat lines:
 * <pre>
 * [UI] &lt;player&gt; uses /command
 *     with error: &lt;error text&gt;            — command threw (ServerCommandException)
 *     but don't have permission (perm)   — CommandMap permission check
 *     and it runs correctly.            — no error, next tick
 * </pre>
 * The outcome is detected via {@link PlayerCommandPreprocessEvent} (permission is
 * known upfront) and Paper's {@link ServerExceptionEvent} (command exceptions
 * carry the sender + root cause). Successful commands are confirmed one tick later,
 * once the exception path had a chance to fire.
 */
public final class CmdLogger implements Listener {

    private static final String DB_KEY = "enabled";

    private static CmdLogger instance;

    private final Main plugin;
    private volatile boolean enabled = false;

    /** Commands awaiting their outcome (error may still fire): player → FIFO queue. */
    private final Map<UUID, Deque<String>> pending = new HashMap<>();

    private CmdLogger(Main plugin) {
        this.plugin = plugin;
    }

    /** Initializes the logger: loads state from DB and registers the listeners. */
    public static CmdLogger init(Main plugin) {
        instance = new CmdLogger(plugin);
        instance.loadFromDb();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
        return instance;
    }

    public static CmdLogger getInstance() {
        return instance;
    }

    public static boolean isEnabled() {
        return instance != null && instance.enabled;
    }

    public static void setEnabled(boolean enabled) {
        if (instance == null) return;
        instance.enabled = enabled;
        instance.saveToDb(enabled);
    }

    // =========================
    // 💾 DB PERSISTENCE
    // =========================

    private void loadFromDb() {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT value FROM cmdlog_meta WHERE key = ?")) {
            st.setString(1, DB_KEY);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    enabled = "true".equalsIgnoreCase(rs.getString(1));
                }
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[CmdLog] Failed to load state from DB: " + e.getMessage());
        }
    }

    private void saveToDb(boolean enabled) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "UPDATE cmdlog_meta SET value = ? WHERE key = ?")) {
            st.setString(1, enabled ? "true" : "false");
            st.setString(2, DB_KEY);
            st.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.warn("[CmdLog] Failed to save state to DB: " + e.getMessage());
        }
    }

    // =========================
    // 🔍 OUTCOME DETECTION
    // =========================

    /**
     * Runs at MONITOR, right before the command is dispatched.
     * <ul>
     *   <li>missing CommandMap permission → "no permission" (known upfront);</li>
     *   <li>otherwise the command is queued and, if no exception fires before the
     *       next tick, it is logged as "runs correctly".</li>
     * </ul>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();
        String command = extractCommand(event.getMessage());
        if (command == null) return;

        // Don't log our own toggle command — avoid a feedback loop
        if (isOwnToggle(command)) return;

        String label = command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);

        // No-permission outcome is knowable upfront via the CommandMap permission node
        // (strip a possible namespace prefix: /minecraft:gamemode → gamemode)
        String lookup = label.contains(":") ? label.substring(label.indexOf(':') + 1) : label;
        Command cmdObj = Bukkit.getCommandMap().getCommand(lookup);
        if (cmdObj != null) {
            String perm = cmdObj.getPermission();
            if (perm != null && !perm.isEmpty() && !player.hasPermission(perm)) {
                log(player, command, Outcome.NO_PERMISSION, perm);
                return;
            }
        }

        // Otherwise the outcome is error (ServerCommandException) or success (next tick)
        UUID uuid = player.getUniqueId();
        pending.computeIfAbsent(uuid, k -> new ArrayDeque<>()).addLast(command);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Deque<String> deque = pending.get(uuid);
            if (deque != null && !deque.isEmpty() && deque.peekFirst().equals(command)) {
                deque.pollFirst();
                if (deque.isEmpty()) pending.remove(uuid);
                log(player, command, Outcome.SUCCESS, null);
            }
        }, 1L);
    }

    /**
     * Paper fires this synchronously when a command throws. The exception carries
     * the sender and the root cause, so we can report the exact error text.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerException(ServerExceptionEvent event) {
        if (!enabled) return;
        if (!(event.getException() instanceof ServerCommandException scx)) return;

        CommandSender sender = scx.getCommandSender();
        if (!(sender instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        Deque<String> deque = pending.get(uuid);
        if (deque == null || deque.isEmpty()) return;

        String command = deque.pollFirst();
        if (deque.isEmpty()) pending.remove(uuid);

        log(player, command, Outcome.ERROR, rootMessage(scx));
    }

    /** The deepest non-empty message from the exception chain. */
    private static String rootMessage(ServerCommandException scx) {
        String message = null;
        Throwable cause = scx.getCause();
        while (cause != null) {
            if (cause.getMessage() != null && !cause.getMessage().isBlank()) {
                message = cause.getMessage();
            }
            cause = cause.getCause();
        }
        return message != null ? message : scx.getClass().getSimpleName();
    }

    // =========================
    // 📣 CHAT OUTPUT
    // =========================

    private enum Outcome { ERROR, NO_PERMISSION, SUCCESS }

    private void log(Player player, String command, Outcome outcome, String detail) {
        // line1 gets the [UI] prefix via Broadcast.send; line2 is a continuation
        // line rendered without the prefix (sendEmbedded).
        String line1 = "<yellow>" + escape(player.getName())
                + " <white>uses <yellow>" + escape(command);
        String line2 = switch (outcome) {
            case ERROR -> "<red>with error: <white>" + escape(detail);
            case NO_PERMISSION -> "<red>but don't have permission <dark_gray>(<white>"
                    + escape(detail) + "<dark_gray>)";
            case SUCCESS -> "<white>and it runs correctly.";
        };
        Broadcast.send(line1);
        Broadcast.sendEmbedded(line2);
    }

    // =========================
    // 🔧 HELPERS
    // =========================

    /** "/cmd args" → "cmd args"; null if there is no command. */
    private static String extractCommand(String message) {
        if (message == null) return null;
        String cmd = message.strip();
        if (cmd.isEmpty() || cmd.charAt(0) != '/') return null;
        cmd = cmd.substring(1).strip();
        return cmd.isEmpty() ? null : cmd;
    }

    /** True if this is {@code /ui cmdlog ...} (or the /ultimateimprovments alias). */
    private static boolean isOwnToggle(String command) {
        String[] parts = command.split("\\s+");
        String root = parts[0].toLowerCase(Locale.ROOT);
        if (!root.equals("ui") && !root.equals("ultimateimprovments")) return false;
        if (parts.length < 2) return false;
        return parts[1].toLowerCase(Locale.ROOT).equals("cmdlog");
    }

    /** Escapes MiniMessage-sensitive characters so the raw command/error text is shown as-is. */
    private static String escape(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("<", "\\<")
                .replace(">", "\\>")
                .replace("\u00A7", "");
    }
}
