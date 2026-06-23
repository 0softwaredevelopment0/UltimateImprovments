package com.mcplugin.mechanics.features.blocks;

import com.mcplugin.infrastructure.core.Main;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Set;

public class GlassBreakManager implements Listener {

    private static final Set<Material> GLASS = Set.of(
            Material.GLASS,
            Material.GLASS_PANE,
            Material.WHITE_STAINED_GLASS,
            Material.ORANGE_STAINED_GLASS,
            Material.MAGENTA_STAINED_GLASS,
            Material.LIGHT_BLUE_STAINED_GLASS,
            Material.YELLOW_STAINED_GLASS,
            Material.LIME_STAINED_GLASS,
            Material.PINK_STAINED_GLASS,
            Material.GRAY_STAINED_GLASS,
            Material.LIGHT_GRAY_STAINED_GLASS,
            Material.CYAN_STAINED_GLASS,
            Material.PURPLE_STAINED_GLASS,
            Material.BLUE_STAINED_GLASS,
            Material.BROWN_STAINED_GLASS,
            Material.GREEN_STAINED_GLASS,
            Material.RED_STAINED_GLASS,
            Material.BLACK_STAINED_GLASS,
            Material.WHITE_STAINED_GLASS_PANE,
            Material.ORANGE_STAINED_GLASS_PANE,
            Material.MAGENTA_STAINED_GLASS_PANE,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE,
            Material.LIME_STAINED_GLASS_PANE,
            Material.PINK_STAINED_GLASS_PANE,
            Material.GRAY_STAINED_GLASS_PANE,
            Material.LIGHT_GRAY_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS_PANE,
            Material.BROWN_STAINED_GLASS_PANE,
            Material.GREEN_STAINED_GLASS_PANE,
            Material.RED_STAINED_GLASS_PANE,
            Material.BLACK_STAINED_GLASS_PANE
    );

    private static GlassBreakManager instance;
    private static boolean enabled = true;
    private static int damage = 5;

    public static void init(Main plugin) {
        instance = new GlassBreakManager();
        reloadConfig();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
    }

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.glassbreak");
        if (cfg == null) return;
        enabled = cfg.getBoolean("enabled", true);
        damage = cfg.getInt("damage", 5);
    }

    @EventHandler
    public void onGlassBreak(BlockBreakEvent e) {
        if (!enabled) return;
        if (!GLASS.contains(e.getBlock().getType())) return;

        var hand = e.getPlayer().getInventory().getItemInMainHand();
        if (hand.getType() != Material.AIR) return;

        e.getPlayer().damage(damage);
        e.getPlayer().sendActionBar("§cНе ломайте стекло рукой!");
    }
}
