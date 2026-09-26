package com.ultimateimprovments.chat;

import com.ultimateimprovments.command.CommandOutcomeTracker;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.Broadcast;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;

/**
 * CmdLogger — logs player commands to chat when enabled ({@code /ui cmdlog <on|off>}).
 * <p>
 * The toggle is persisted in the DB ({@code cmdlog_meta} table), so it survives
 * restarts and {@code /ui reload}. Default is <b>off</b>.
 * <p>
 * Every command produces one chat line:
 * <pre>
 * [UI] &lt;player&gt; uses /command
 * </pre>
 * <ul>
 *   <li><b>/ui commands</b> — the real outcome is delivered by Core's
 *       {@link CommandOutcomeTracker} (the /ui dispatcher never throws for
 *       usage/permission failures, so exceptions alone could not detect them).
 *       A failing dispatch adds a second line:
 *       <pre>
 *     with error: unknown subcommand: foo
 *     but don't have permission
 *       </pre></li>
 *   <li><b>other commands</b> — just the "uses" line: what the player typed,
 *       without outcome guessing.</li>
 * </ul>
 */
public final class CmdLogger implements Listener {

    private static final String DB_KEY = "enabled";

    private static CmdLogger instance;

    private final Main plugin;
    private volatile boolean enabled = false;

    private CmdLogger(Main plugin) {
        this.plugin = plugin;
    }

    /** Initializes the logger: loads state from DB and registers the listeners. */
    public static CmdLogger init(Main plugin) {
        // A re-enable (e.g. after /ui reload) must not leave the old instance's
        // listeners behind — they were registered under UI-Core's plugin handle,
        // so UIChat's own HandlerList.unregisterAll(this) does not remove them.
        if (instance != null) {
            org.bukkit.event.HandlerList.unregisterAll(instance);
        }
        instance = new CmdLogger(plugin);
        instance.loadFromDb();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);

        // /ui commands report their real outcome through the Core-side tracker
        // (no permission, unknown subcommand, subcommand-reported errors).
        CommandOutcomeTracker.setListener(
                (sender, command, outcome, detail) -> {
                    CmdLogger logger = instance;
                    if (logger != null) logger.handleUiOutcome(sender, command, outcome, detail);
                });

        return instance;
    }

    /** Unregisters listeners and detaches the /ui outcome listener. */
    public static void shutdown() {
        if (instance != null) {
            org.bukkit.event.HandlerList.unregisterAll(instance);
            instance = null;
        }
        CommandOutcomeTracker.clearListener();
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
    // 🔍 COMMAND LOGGING
    // =========================

    /** Logs every command as "uses ..." at MONITOR (after cancel checks). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();
        String command = extractCommand(event.getMessage());
        if (command == null) return;

        // Don't log our own toggle command — avoid a feedback loop
        if (isOwnToggle(command)) return;

        // /ui commands: the "uses" line here; the real outcome (error / no
        // permission) arrives via handleUiOutcome from the Core-side tracker.
        // Other commands: just the "uses" line — what the player typed.
        log(player, command);
    }

    /**
     * Receives the real outcome of a /ui dispatch from
     * {@link CommandOutcomeTracker} and adds the failure detail line.
     * For SUCCESS nothing is added — the command went through.
     */
    private void handleUiOutcome(org.bukkit.command.CommandSender sender, String command,
                                 CommandOutcomeTracker.Outcome outcome, String detail) {
        if (!enabled) return;
        if (!(sender instanceof Player player)) return;

        String line2 = switch (outcome) {
            case NO_PERMISSION -> "<red>but don't have permission";
            case UNKNOWN_COMMAND -> "<red>with error: <white>unknown subcommand: <yellow>" + escape(detail);
            case FAILED, ERROR -> "<red>with error: <white>" + escape(detail);
            case SUCCESS -> null;
        };
        if (line2 != null) {
            Broadcast.sendEmbedded(line2);
        }
    }

    // =========================
    // 📣 CHAT OUTPUT
    // =========================

    private void log(Player player, String command) {
        // line1 gets the [UI] prefix via Broadcast.send; failure continuations
        // are rendered without the prefix (sendEmbedded).
        String line1 = "<yellow>" + escape(player.getName())
                + " <white>uses <yellow>" + escape(command);
        Broadcast.send(line1);
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
