package com.ultimateimprovments.mechanics.features.blocks;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.mechanics.features.structure.StructureIntegrityManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EnderChestManager implements Listener {

    private static boolean enabled = true;
    private static double explosionChance = 0.001;
    private static double explosionPower = 10.0;
    private static double damage = 1;

    private static final Set<UUID> enderseeViewers = ConcurrentHashMap.newKeySet();
    private static final Random RANDOM = new Random();
    private static final Map<UUID, Location> lastOpenedChest = new ConcurrentHashMap<>();

    public static void init(Main plugin) {
        reloadConfig();
        plugin.getServer().getPluginManager().registerEvents(new EnderChestManager(), plugin);
    }

    public static void addEnderseeViewer(UUID uuid) {
        enderseeViewers.add(uuid);
    }

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.enderchest");
        if (cfg == null) return;
        enabled = cfg.getBoolean("enabled", true);
        explosionChance = cfg.getDouble("explosion_chance", 0.001);
        explosionPower = cfg.getDouble("explosion_power", 10.0);
        damage = cfg.getDouble("damage", 1);

        ConsoleLogger.info("[EnderChest] Config loaded: damage=" + damage
                + " explosionChance=" + explosionChance
                + " explosionPower=" + explosionPower);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnderChestInteract(PlayerInteractEvent e) {
        if (!enabled) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.ENDER_CHEST) return;

        Player player = e.getPlayer();
        Location loc = e.getClickedBlock().getLocation();

        // Deal 1 damage on every open (configurable via features.enderchest.damage)
        if (damage > 0) {
            double before = player.getHealth();
            player.damage(damage);
            double after = player.getHealth();
            ConsoleLogger.info("[EnderChest] " + player.getName()
                    + " health: " + String.format("%.1f", before)
                    + " → " + String.format("%.1f", after)
                    + " (damage=" + damage + ")");
        }

        // Structure Integrity — add stress
        StructureIntegrityManager sim = StructureIntegrityManager.getInstance();
        if (sim != null) {
            sim.onEnderChestInteract(loc);
        }

        lastOpenedChest.put(player.getUniqueId(), loc);

        // Random explosion check (0.1% chance by default)
        double roll = RANDOM.nextDouble();
        if (roll >= explosionChance) return;

        // Explosion triggered — destroy the chest, create explosion, damage player
        explodeChest(player, loc, roll);
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnderChestClose(InventoryCloseEvent e) {
        if (!enabled) return;
        if (e.getInventory().getType() != InventoryType.ENDER_CHEST) return;
        if (!(e.getPlayer() instanceof Player player)) return;

        if (enderseeViewers.remove(player.getUniqueId())) {
            lastOpenedChest.remove(player.getUniqueId());
            return;
        }

        // Deal 1 damage on close too
        if (damage > 0) {
            player.damage(damage);
        }

        StructureIntegrityManager sim = StructureIntegrityManager.getInstance();
        if (sim != null) {
            Location chestLoc = lastOpenedChest.remove(player.getUniqueId());
            if (chestLoc != null) {
                sim.onEnderChestInteract(chestLoc);
            }
        }
    }

    /**
     * Explodes the ender chest (same as 0% integrity explosion):
     * - Destroys the chest block
     * - Creates an explosion with configured power
     * - Deals massive damage to the player
     * - Awards the "blowed_by_echest" advancement
     */
    private static void explodeChest(Player player, Location loc, double roll) {
        // Destroy the chest block
        loc.getBlock().setType(Material.AIR);

        // Create explosion (with block damage)
        if (explosionPower > 0) {
            loc.getWorld().createExplosion(loc, (float) explosionPower, false, true);
        }

        // Award advancement
        try {
            var adv = Bukkit.getAdvancement(new NamespacedKey("minecraft", "datapack/blowed_by_echest"));
            if (adv != null) {
                var progress = player.getAdvancementProgress(adv);
                if (!progress.isDone()) {
                    progress.awardCriteria("1");
                }
            }
        } catch (Exception ignored) {}

        // Log the explosion
        ConsoleLogger.warn("[EnderChest] " + player.getName()
                + " opened an ender chest at "
                + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()
                + " and it EXPLODED! (roll=" + String.format("%.6f", roll)
                + " < chance=" + explosionChance + ")");
    }
}
