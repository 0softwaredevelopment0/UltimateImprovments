package com.ultimateimprovments.mechanics.features.world;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;

/**
 * A Netherite King — grants the {@code ui:datapack/a_netherite_king} achievement
 * when a player has at least one netherite block in their inventory.
 * <p>
 * A cheap 1-second sweep (20 ticks) checks every online player's inventory;
 * the achievement is awarded once ({@code awardCriteria} is idempotent) and
 * already-done players are skipped, so the server is not loaded.
 */
public final class NetheriteKingListener {

    /** The datapack advancement key (parent: {@code ui:datapack/let_me_teleport}). */
    private static final NamespacedKey ADVANCEMENT = new NamespacedKey("ui", "datapack/a_netherite_king");

    /** Sweep interval: 20 ticks (1s). */
    private static final long SWEEP_INTERVAL_TICKS = 20L;

    private NetheriteKingListener() {}

    /** One sweep tick: grant the achievement to every player holding a netherite block. */
    private static void sweep() {
        Advancement adv = Bukkit.getAdvancement(ADVANCEMENT);
        if (adv == null) return; // datapack not loaded yet

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                var progress = player.getAdvancementProgress(adv);
                if (progress.isDone()) continue;

                if (player.getInventory().contains(Material.NETHERITE_BLOCK)) {
                    progress.awardCriteria("1");
                }
            } catch (Exception e) {
                ConsoleLogger.warn("[NetheriteKing] Sweep error for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    /** Starts the periodic inventory sweep. */
    public static void register(Main plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, NetheriteKingListener::sweep,
                SWEEP_INTERVAL_TICKS, SWEEP_INTERVAL_TICKS);
        ConsoleLogger.info("[NetheriteKing] Listener registered (netherite block check every 1s).");
    }
}
