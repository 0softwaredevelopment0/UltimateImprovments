package com.mcplugin.features.antimatter;

import com.mcplugin.Keys;
import com.mcplugin.Main;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Creeper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class AntimatterManager implements Listener {

    private static AntimatterManager instance;
    private static boolean enabled = true;
    private static int explosionRadius = 5;
    private static boolean breakBlocks = true;
    private static boolean setFire = true;

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

    @EventHandler
    public void onAntimatterDrop(PlayerDropItemEvent e) {
        if (!enabled) return;

        ItemStack item = e.getItemDrop().getItemStack();
        if (!item.hasItemMeta()) return;
        var meta = item.getItemMeta();
        if (meta == null) return;
        if (!meta.getPersistentDataContainer().has(Keys.ANTIMATTER, PersistentDataType.BYTE)) return;

        // НЕ отменяем дроп — предмет уже удалён из инвентаря автоматически
        // Просто берём его позицию и удаляем сущность бутылька
        Location loc = e.getItemDrop().getLocation();

        // Удаляем сущность бутылька
        e.getItemDrop().remove();

        // ==========================================
        // 1. Крипер — визуальная сущность (радиус 0, только визуал)
        // ==========================================
        loc.getWorld().spawn(loc, Creeper.class, c -> {
            c.setMaxFuseTicks(1);
            c.setFuseTicks(1);
            c.setExplosionRadius(0); // только визуальный эффект крипера
            c.setIgnited(true);
        });

        // ==========================================
        // 2. Взрыв строго по настройкам из конфига
        //    explosion_radius — integer (читается cfg.getInt)
        //    break_blocks, set_fire — boolean из config.yml
        // ==========================================
        loc.getWorld().createExplosion(loc, explosionRadius, setFire, breakBlocks);

        // Визуальный дым
        loc.getWorld().spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, loc, 64, 0, 0, 0, 0.1);
    }
}
