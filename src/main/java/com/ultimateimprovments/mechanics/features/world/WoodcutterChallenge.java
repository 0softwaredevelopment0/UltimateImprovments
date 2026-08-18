package com.ultimateimprovments.mechanics.features.world;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Timed advancement challenge: <b>The Woodcutter at Full Throttle</b> —
 * break 7,200 wood blocks (any log/stem) within 1 hour to earn the
 * {@code ui:datapack/woodcutter_at_full_throttle} achievement.
 * <p>
 * Started via {@code /ui advancement start woodcutter}. The challenge is
 * timer-based so the server is not loaded constantly: per-break work is just
 * a counter increment (a single map lookup), and a cheap 1-second sweep only
 * iterates players with an active challenge (showing progress / expiring).
 * <p>
 * Counting covers manual breaks ({@link BlockBreakEvent}) and blocks broken by
 * the TreeCapitator and AoE enchants (they use {@code breakNaturally} directly
 * without firing an event, so those listeners call {@link #countBroken}).
 */
public final class WoodcutterChallenge implements Listener {

    /** The datapack advancement key (parent: {@code ui:datapack/start}). */
    private static final NamespacedKey ADVANCEMENT = new NamespacedKey("ui", "datapack/woodcutter_at_full_throttle");

    /** Challenge duration: 1 hour in milliseconds. */
    private static final long DURATION_MS = 3_600_000L;

    /** Wood blocks to break for the achievement. */
    private static final int GOAL = 7_200;

    /** Active challenges: player UUID → state. One challenge per player at a time. */
    private static final Map<UUID, ChallengeState> ACTIVE = new ConcurrentHashMap<>();

    private WoodcutterChallenge() {}

    private static final class ChallengeState {
        final long startTime = System.currentTimeMillis();
        int count = 0;
    }

    // =========================
    // COMMAND ENTRY
    // =========================

    /** Starts a timed challenge by its name (only "woodcutter" is available for now). */
    public static void start(Player player, String name) {
        if (!"woodcutter".equalsIgnoreCase(name)) {
            player.sendMessage(MessageUtil.parse(
                    "<red>Unknown achievement: <white>" + name + "</white>. Available: <white>woodcutter, teleport</white>"));
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!TimedChallengeLock.tryAcquire(uuid)) {
            player.sendMessage(MessageUtil.parse(
                    "<red>✖ <white>You already have an active challenge! Finish the current one first.</white>"));
            return;
        }

        ACTIVE.put(uuid, new ChallengeState());
        player.sendMessage(MessageUtil.parse(
                "<gold>⏱ <white>Challenge <yellow>The Woodcutter at Full Throttle</yellow> started!</white>\n"
                + "<gray>Сломай <white>7 200</white> wooden blocks in <white>1 hour</white>. "));
    }

    /** Cancels the player's active challenge, if any. Returns true if one was stopped. */
    public static boolean stop(Player player) {
        ChallengeState state = ACTIVE.remove(player.getUniqueId());
        TimedChallengeLock.release(player.getUniqueId());
        if (state == null) return false;
        player.sendMessage(MessageUtil.parse(
                "<yellow>⏹ <white>The challenge has been canceled. Broken: <yellow>" + state.count + "</yellow>/7200.</white>"));
        return true;
    }

    // =========================
    // COUNTING (called from listeners + enchants)
    // =========================

    /** Counts one broken wood block toward the player's active challenge. Cheap: one map lookup. */
    public static void countBroken(Player player, Material type) {
        if (player == null || type == null) return;
        if (!isWood(type)) return;

        ChallengeState state = ACTIVE.get(player.getUniqueId());
        if (state == null) return;

        state.count++;
        if (state.count >= GOAL) {
            ACTIVE.remove(player.getUniqueId());
            TimedChallengeLock.release(player.getUniqueId());
            grant(player, state.count);
        }
    }

    /** Manual block breaks (the block the player actually clicked). */
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        countBroken(event.getPlayer(), event.getBlock().getType());
    }

    private static boolean isWood(Material type) {
        String name = type.name();
        return name.endsWith("_LOG") || name.endsWith("_STEM"); // incl. STRIPPED_*
    }

    // =========================
    // SWEEP (20 ticks) — progress bar + expiry, only over active challenges
    // =========================

    private static void sweep() {
        long now = System.currentTimeMillis();

        ACTIVE.entrySet().removeIf(entry -> {
            ChallengeState state = entry.getValue();
            Player player = Bukkit.getPlayer(entry.getKey());
            long elapsed = now - state.startTime;

            if (elapsed >= DURATION_MS) {
                TimedChallengeLock.release(entry.getKey());
                if (player != null) {
                    player.sendMessage(MessageUtil.parse(
                            "<red>✖ <white>Time's up! You broke: <yellow>" + state.count
                            + "</yellow>/7200 wooden blocks.</white>"));
                }
                return true;
            }

            if (player != null) {
                int percent = (int) (state.count * 100.0 / GOAL);
                long leftSec = (DURATION_MS - elapsed) / 1000;
                player.sendActionBar(MessageUtil.parse(
                        "<gold>🪓 <white>Wooden: <yellow>" + state.count + "</yellow>/7200</white> "
                        + "<gray>(" + percent + "%) · remaining " + (leftSec / 60) + "м " + (leftSec % 60) + "с</gray>"));
            }
            return false;
        });
    }

    // =========================
    // REWARD
    // =========================

    /** Awards the achievement and notifies the player. Idempotent. */
    private static void grant(Player player, int broken) {
        try {
            Advancement adv = Bukkit.getAdvancement(ADVANCEMENT);
            if (adv != null) {
                var progress = player.getAdvancementProgress(adv);
                if (!progress.isDone()) {
                    progress.awardCriteria("1");
                }
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[WoodcutterChallenge] Award error for " + player.getName() + ": " + e.getMessage());
        }
        player.sendMessage(MessageUtil.parse(
                "<green>✔ <white>Challenge completed! <yellow>The Woodcutter at Full Throttle</yellow> reached "
                + "(" + broken + " wooden blocks).</white>"));
    }

    // =========================
    // REGISTRATION
    // =========================

    public static void register(Main plugin) {
        plugin.getServer().getPluginManager().registerEvents(new WoodcutterChallenge(), plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, WoodcutterChallenge::sweep, 20L, 20L);
        ConsoleLogger.info("[WoodcutterChallenge] Registered (timed challenge: The Woodcutter at Full Throttle).");
    }
}
