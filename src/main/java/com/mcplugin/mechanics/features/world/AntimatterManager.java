package com.mcplugin.mechanics.features.world;

import com.mcplugin.core.Keys;
import com.mcplugin.core.Main;
import com.mcplugin.mechanics.environment.radiation.RadiationManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class AntimatterManager implements Listener {

    private static AntimatterManager instance;
    private static boolean enabled = true;
    private static int explosionRadius = 5;
    private static boolean breakBlocks = true;
    private static boolean setFire = true;
    private static final String ADVANCEMENT_KEY = "datapack/react_with_antimatter";

    public static void init(Main plugin) {
        instance = new AntimatterManager();
        reloadConfig();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
    }

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.antimatter");
        if (cfg == null) return;
        enabled = cfg.getBoolean("enabled", true);
        explosionRadius = cfg.getInt("explosion_radius", 5);
        breakBlocks = cfg.getBoolean("break_blocks", true);
        setFire = cfg.getBoolean("set_fire", true);
    }

    // =========================
    // CHECK IF ITEM IS ANTIMATTER
    // =========================
    private boolean isAntimatter(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(Keys.ANTIMATTER, PersistentDataType.BYTE);
    }

    // =========================
    // RIGHT CLICK — МГНОВЕННЫЙ ВЗРЫВ
    // Срабатывает СРАЗУ при ПКМ, удаляет колбу из руки
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onAntimatterInteract(PlayerInteractEvent e) {
        if (!enabled) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = e.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        boolean inMainHand = true;
        if (!isAntimatter(item)) {
            item = player.getInventory().getItemInOffHand();
            inMainHand = false;
        }
        if (!isAntimatter(item)) return;

        e.setCancelled(true);
        Location loc = player.getLocation();

        // =========================
        // 1. УДАЛЯЕМ КОЛБУ ИЗ РУКИ (1 шт.)
        // =========================
        if (inMainHand) {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            mainHand.setAmount(mainHand.getAmount() - 1);
            player.getInventory().setItemInMainHand(mainHand.getAmount() > 0 ? mainHand : null);
        } else {
            ItemStack offHand = player.getInventory().getItemInOffHand();
            offHand.setAmount(offHand.getAmount() - 1);
            player.getInventory().setItemInOffHand(offHand.getAmount() > 0 ? offHand : null);
        }

        // =========================
        // 2. ВЗРЫВ (визуальный крипер + настоящий взрыв)
        // =========================
        loc.getWorld().spawn(loc, Creeper.class, c -> {
            c.setMaxFuseTicks(1);
            c.setFuseTicks(1);
            c.setExplosionRadius(0);
            c.setIgnited(true);
        });

        loc.getWorld().createExplosion(loc, explosionRadius, setFire, breakBlocks);
        loc.getWorld().spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, loc, 64, 0, 0, 0, 0.1);

        // =========================
        // 3. РАДИАЦИЯ ПО ДИСТАНЦИИ
        //    В эпицентре: смертельная доза (6400+)
        //    На краю взрыва: 0
        //    Линейная интерполяция
        // =========================
        int maxRad = 6400;
        double radius = explosionRadius;
        for (Player nearby : Bukkit.getOnlinePlayers()) {
            if (!nearby.getWorld().equals(loc.getWorld())) continue;
            double dist = nearby.getLocation().distance(loc);
            if (dist > radius) continue;
            int rad = (int) Math.round(maxRad * (1.0 - dist / radius));
            if (rad > 0) {
                RadiationManager.addRadiation(nearby, rad);
            }
        }

        // =========================
        // 4. ДОСТИЖЕНИЕ: react_with_antimatter
        // =========================
        grantAdvancement(player, ADVANCEMENT_KEY);
    }

    // =========================
    // DROP — защита от выпадения (старая логика)
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onAntimatterDrop(PlayerDropItemEvent e) {
        if (!enabled) return;

        ItemStack item = e.getItemDrop().getItemStack();
        if (!isAntimatter(item)) return;

        Location loc = e.getItemDrop().getLocation();
        e.getItemDrop().remove();

        // Визуальный крипер + взрыв
        loc.getWorld().spawn(loc, Creeper.class, c -> {
            c.setMaxFuseTicks(1);
            c.setFuseTicks(1);
            c.setExplosionRadius(0);
            c.setIgnited(true);
        });
        loc.getWorld().createExplosion(loc, explosionRadius, setFire, breakBlocks);
        loc.getWorld().spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, loc, 64, 0, 0, 0, 0.1);

        // Радиация по дистанции
        int maxRad = 6400;
        double radius = explosionRadius;
        for (Player nearby : Bukkit.getOnlinePlayers()) {
            if (!nearby.getWorld().equals(loc.getWorld())) continue;
            double dist = nearby.getLocation().distance(loc);
            if (dist > radius) continue;
            int rad = (int) Math.round(maxRad * (1.0 - dist / radius));
            if (rad > 0) {
                RadiationManager.addRadiation(nearby, rad);
            }
        }

        // Достижение для ближайшего игрока
        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Player nearby : Bukkit.getOnlinePlayers()) {
            if (!nearby.getWorld().equals(loc.getWorld())) continue;
            double dist = nearby.getLocation().distanceSquared(loc);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = nearby;
            }
        }
        if (nearest != null && Math.sqrt(nearestDist) <= radius) {
            grantAdvancement(nearest, ADVANCEMENT_KEY);
        }
    }

    // =========================
    // GRANT ADVANCEMENT HELPER
    // =========================
    private void grantAdvancement(Player player, String advancementKey) {
        try {
            Advancement advancement = Bukkit.getAdvancement(
                    new org.bukkit.NamespacedKey("minecraft", advancementKey));
            if (advancement != null) {
                var progress = player.getAdvancementProgress(advancement);
                if (!progress.isDone()) {
                    progress.awardCriteria("1");
                }
            }
        } catch (Exception ignored) {}
    }
}
