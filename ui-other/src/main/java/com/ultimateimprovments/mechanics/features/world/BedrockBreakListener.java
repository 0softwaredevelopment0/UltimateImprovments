package com.ultimateimprovments.mechanics.features.world;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Hit, hit, to pieces! — grants the {@code ui:datapack/hit_hit_to_pieces}
 * achievement when a player breaks at least one bedrock block.
 * <p>
 * Bedrock normally can't be broken in vanilla, but this server's
 * {@code UnbreakableBreaker} feature allows it. That feature destroys blocks
 * directly (no {@link BlockBreakEvent} is fired), so the award is also hooked
 * there via {@link #grant(Player)} — plus a regular event listener covers any
 * other path that breaks bedrock through a real event.
 * <p>
 * {@code awardCriteria} is idempotent — the achievement is granted only once.
 */
public final class BedrockBreakListener implements Listener {

    /** The datapack advancement key (parent: {@code ui:datapack/beyond_space}). */
    private static final NamespacedKey ADVANCEMENT = new NamespacedKey("ui", "datapack/hit_hit_to_pieces");

    private BedrockBreakListener() {}

    /** Awards the achievement to the player if they broke a bedrock block. Idempotent. */
    public static void grant(Player player) {
        if (player == null) return;
        try {
            Advancement adv = Bukkit.getAdvancement(ADVANCEMENT);
            if (adv == null) return; // datapack not loaded yet

            var progress = player.getAdvancementProgress(adv);
            if (!progress.isDone()) {
                progress.awardCriteria("1");
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[BedrockBreak] Award error for " + player.getName() + ": " + e.getMessage());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.BEDROCK) {
            grant(event.getPlayer());
        }
    }

    /**
     * Registers the event listener for any bedrock broken through a real
     * {@link BlockBreakEvent} (UnbreakableBreaker calls {@link #grant(Player)}
     * directly since it does not fire the event).
     */
    public static void register(Main plugin) {
        plugin.getServer().getPluginManager().registerEvents(new BedrockBreakListener(), plugin);
        ConsoleLogger.info("[BedrockBreak] Listener registered (Hit, hit, to pieces! — bedrock break check).");
    }
}
