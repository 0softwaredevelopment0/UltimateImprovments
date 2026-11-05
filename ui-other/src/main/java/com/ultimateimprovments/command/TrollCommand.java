package com.ultimateimprovments.command;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.SoundUtil;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Fake commands to troll hackers.
 * <p>
 * Registered as regular commands {@code /forceop} and {@code /crash}
 * (without the /ui prefix) and do NOTHING real:
 * <ul>
 *   <li><b>/forceop</b> — sends the player fake messages as if the server
 *       granted them OP. By default real OP is NOT granted (setOp is never
 *       called); {@code troll.forceop.grant_real_op} can turn the fake into a
 *       genuine grant, which is off by default on purpose.
 *       {@code troll.forceop.delay_ticks} postpones the fake answer (default
 *       1 tick, 0 = immediately) so the command looks like it is doing work.</li>
 *   <li><b>/crash</b> — by default the fake crash only: stops ALL server→client
 *       packets for the player ({@code troll.crash.freeze_client}: a discarding
 *       netty handler in their pipeline), so their world freezes exactly like
 *       during a real server crash, then after {@code troll.crash.delay_seconds}
 *       kicks them with the {@code troll.crash.kick_message} text (multi-line
 *       list, default {@code <white>Server closed}). No chat text is sent: during
 *       the blackout nothing reaches the client, so the kick is the only thing it
 *       ever sees.
 *       <b>DANGEROUS</b>: {@code troll.crash.real_shutdown = true} turns the fake
 *       into a REAL shutdown — the server actually stops or restarts
 *       ({@code troll.crash.shutdown_mode}), the blackout and the fake kick are
 *       skipped, and every player is disconnected by the server itself.</li>
 * </ul>
 * All settings (enable, messages, sounds, delay, permissions) are in config.yml
 * under the {@code troll:} section. Works only for players.
 */
public class TrollCommand implements CommandExecutor, TabCompleter {

    /** Fallback kick text when {@code troll.crash.kick_message} is empty. */
    private static final String DEFAULT_KICK_MESSAGE = "<white>Server closed";

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommandErrors.playerOnly(sender);
            return true;
        }

        FileConfiguration cfg = Main.getInstance().getConfig();
        if (!cfg.getBoolean("troll.enabled", true)) {
            CommandErrors.moduleDisabled(player, "troll.enabled");
            return true;
        }

        return switch (cmd.getName().toLowerCase()) {
            case "forceop" -> handleForceOp(player);
            case "crash" -> handleCrash(player);
            default -> false;
        };
    }

    // =========================
    // /forceop — fake OP grant
    // =========================
    private boolean handleForceOp(Player player) {
        FileConfiguration cfg = Main.getInstance().getConfig();
        if (!cfg.getBoolean("troll.forceop.enabled", true)) return true;

        if (!checkPermission(player, cfg.getString("troll.forceop.permission", ""))) return true;

        boolean grantRealOp = cfg.getBoolean("troll.forceop.grant_real_op", false);
        int delayTicks = Math.max(0, cfg.getInt("troll.forceop.delay_ticks", 1));

        if (cfg.getBoolean("troll.forceop.log_to_console", true)) {
            ConsoleLogger.info("[Troll] " + player.getName() + " used /forceop ("
                    + (grantRealOp ? "REAL OP GRANTED — grant_real_op is enabled" : "fake OP — no OP granted")
                    + ", delay " + delayTicks + " ticks).");
        }

        // The fake grant runs after a short delay so the command looks like it is
        // doing some work server-side (default 1 tick, 0 = no delay at all).
        Runnable fakeGrant = () -> {
            if (!player.isOnline()) return;

            // Fake messages — look like the server granted OP
            for (String msg : getMessageList(cfg, "troll.forceop.messages")) {
                if (msg == null || msg.isEmpty()) continue;
                player.sendMessage(MessageUtil.parse(msg.replace("%player%", player.getName()), player));
            }

            // ActionBar (optional)
            String actionbar = cfg.getString("troll.forceop.actionbar", "");
            if (actionbar != null && !actionbar.isEmpty()) {
                player.sendActionBar(MessageUtil.parse(actionbar.replace("%player%", player.getName()), player));
            }

            // Sound (optional)
            Sound sound = SoundUtil.getSound(cfg.getString("troll.forceop.sound", ""));
            SoundUtil.playSound(player, sound, 1.0f, 1.0f);

            // Opt-in real grant (off by default — this is the dangerous branch).
            if (grantRealOp) {
                player.setOp(true);
                ConsoleLogger.warn("[Troll] " + player.getName()
                        + " was granted REAL operator status by /forceop (troll.forceop.grant_real_op = true).");
            }
        };

        if (delayTicks == 0) {
            fakeGrant.run();
        } else {
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), fakeGrant, delayTicks);
        }
        return true;
    }

    // =========================
    // /crash — fake server crash + packet blackout + kick
    // =========================
    private boolean handleCrash(Player player) {
        FileConfiguration cfg = Main.getInstance().getConfig();
        if (!cfg.getBoolean("troll.crash.enabled", true)) return true;

        if (!checkPermission(player, cfg.getString("troll.crash.permission", ""))) return true;

        // Real shutdown mode: nothing is imitated any more — the server actually
        // goes down, so neither the packet blackout nor the fake kick makes sense.
        if (cfg.getBoolean("troll.crash.real_shutdown", false)) {
            return handleRealShutdown(player, cfg);
        }

        // Blackout FIRST: the server stops sending ANY packets to this client
        // (their pipeline gets a discarding outbound handler), so everything on
        // their screen freezes exactly like during a real server crash — no
        // poisoned packets, no camera tricks. While the blackout lasts nothing
        // reaches the client, so chat text would be pointless: the kick below is
        // the only thing the player ever sees.
        boolean blackout = cfg.getBoolean("troll.crash.freeze_client", true);
        if (blackout) {
            TrollFreeze.startBlackout(player);
        }

        // Sound (optional — player.sendSound would also be swallowed by the
        // blackout, but the sound packet may already be queued)
        Sound sound = SoundUtil.getSound(cfg.getString("troll.crash.sound", ""));
        SoundUtil.playSound(player, sound, 1.0f, 1.0f);

        int delaySeconds = Math.max(0, cfg.getInt("troll.crash.delay_seconds", 3));
        final String kickMessage = kickMessageRaw(cfg);

        if (cfg.getBoolean("troll.crash.log_to_console", true)) {
            ConsoleLogger.info("[Troll] " + player.getName()
                    + " used /crash (fake crash" + (blackout ? ", packet blackout" : "")
                    + " — kicking in " + delaySeconds + "s).");
        }

        // Lift the blackout, then kick after delay seconds. A client that saw the
        // blackout has a fully frozen world until the disconnect arrives; the kick
        // text is the authentic vanilla "Server closed".
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    if (blackout) {
                        TrollFreeze.stopBlackout(player);
                    }
                    // The kick is the only text the client sees — placeholders
                    // such as %player% still resolve against the player object.
                    player.kick(MessageUtil.parse(kickMessage, player));
                }
            }
        }.runTaskLater(Main.getInstance(), delaySeconds * 20L);

        return true;
    }

    // =========================
    // /crash with real_shutdown = true — take the server down for real
    // =========================
    /**
     * Real shutdown branch ({@code troll.crash.real_shutdown = true}).
     * <p>
     * Nothing is imitated: no packet blackout, no fake kick — the server itself
     * disconnects every player when it stops/restarts. The actual power action
     * goes through {@link PowerManager#executeDirect(boolean)}, the same pipeline
     * {@code /ui power} uses (broadcast, one-tick delay, save + shutdown or
     * restart), so launch scripts and {@code spigot.yml restart-script} keep
     * working exactly as they do for a normal restart.
     */
    private boolean handleRealShutdown(Player player, FileConfiguration cfg) {
        String rawMode = cfg.getString("troll.crash.shutdown_mode", "stop");
        String mode = rawMode == null ? "stop" : rawMode.trim().toLowerCase(Locale.ROOT);

        boolean restart;
        if ("restart".equals(mode)) {
            restart = true;
        } else {
            if (!"stop".equals(mode)) {
                ConsoleLogger.warn("[Troll] Unknown troll.crash.shutdown_mode '" + rawMode
                        + "' — falling back to 'stop'.");
            }
            restart = false;
        }

        if (cfg.getBoolean("troll.crash.log_to_console", true)) {
            ConsoleLogger.warn("[Troll] " + player.getName()
                    + " used /crash with troll.crash.real_shutdown = true — the server will "
                    + (restart ? "restart" : "stop") + " for real.");
        }

        PowerManager power = PowerManager.getInstance();
        if (power != null) {
            power.executeDirect(restart);
        } else {
            // Should not happen (PowerManager is initialised on enable), but a
            // troll command must never silently do nothing when asked for this.
            ConsoleLogger.warn("[Troll] PowerManager is not initialised — shutting the server down directly.");
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                if (restart) {
                    Bukkit.getServer().restart();
                } else {
                    Bukkit.getServer().shutdown();
                }
            }, 20L);
        }
        return true;
    }

    /**
     * Reads the multi-line kick text: either a list of lines (joined with
     * newlines) or a single string. Falls back to the vanilla
     * {@code <white>Server closed} when the result is empty.
     */
    private static String kickMessageRaw(FileConfiguration cfg) {
        List<String> lines = getMessageList(cfg, "troll.crash.kick_message");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (line == null) continue;
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(line);
        }
        return sb.toString().isBlank() ? DEFAULT_KICK_MESSAGE : sb.toString();
    }

    /**
     * Checks the command permission. Empty permission = available to everyone.
     */
    private boolean checkPermission(Player player, String permission) {
        if (permission == null || permission.isEmpty()) return true;
        if (player.hasPermission(permission)) return true;
        CommandErrors.noPermission(player, permission);
        return false;
    }

    /**
     * Reads the message list, accepting either a YAML list or a single string.
     */
    private static List<String> getMessageList(FileConfiguration cfg, String path) {
        if (cfg.isString(path)) {
            return List.of(cfg.getString(path, ""));
        }
        return cfg.getStringList(path);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        return Collections.emptyList();
    }
}
