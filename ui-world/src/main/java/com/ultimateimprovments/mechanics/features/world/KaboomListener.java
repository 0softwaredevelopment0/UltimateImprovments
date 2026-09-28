package com.ultimateimprovments.mechanics.features.world;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Kaboom! — grants the {@code ui:datapack/kaboom} achievement when a
 * player deals 1,000 or more damage to a mob with a SINGLE mace hit.
 * <p>
 * {@code awardCriteria} is idempotent — the achievement is granted only once,
 * so further hits do nothing.
 */
public final class KaboomListener implements Listener {

    /** The datapack advancement key (parent: {@code ui:datapack/hit_hit_to_pieces}). */
    private static final NamespacedKey ADVANCEMENT = new NamespacedKey("ui", "datapack/kaboom");

    /** Single-hit damage required for the achievement (1000 or more counts). */
    private static final double REQUIRED_DAMAGE = 1000.0;

    private KaboomListener() {}

    /** Awards the achievement to the player. Idempotent. */
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
            ConsoleLogger.warn("[Kaboom] Award error for " + player.getName() + ": " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (victim instanceof Player) return; // mobs only
        if (!victim.isValid() || victim.isDead()) return;

        if (player.getInventory().getItemInMainHand().getType() != Material.MACE) return;

        // Single hit must deal 1000+ damage.
        if (event.getFinalDamage() >= REQUIRED_DAMAGE) {
            grant(player);
        }
    }

    /** Registers the damage listener. */
    public static void register(Main plugin) {
        plugin.getServer().getPluginManager().registerEvents(new KaboomListener(), plugin);
        ConsoleLogger.info("[Kaboom] Listener registered (single mace hit ≥ 1000 damage for Kaboom!).");
    }
}
