package com.ultimateimprovments.module;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.enchantment.AOEEnchantment;
import com.ultimateimprovments.enchantment.AOEEnchantmentListener;
import com.ultimateimprovments.enchantment.AOEEnchantmentSyncListener;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Module: AoE (Area of Effect) Enchantment.
 * <p>
 * The enchantment is a REAL data-driven enchantment ({@code minecraft:aoe},
 * registered by the UI-Datapack) plus a PDC mirror failsafe:
 * <ul>
 *   <li>Items detected with the charm have their level mirrored into PDC
 *       ({@code ui:aoe_level}) by {@link AOEEnchantmentSyncListener}.</li>
 *   <li>If the datapack crashes, {@link AOEEnchantment#getLevel} falls back to
 *       PDC — enchanted and legacy PDC items keep working.</li>
 *   <li>When the datapack returns, PDC-only items are re-enchanted automatically.</li>
 * </ul>
 * Breaks blocks of the same type in a radius = enchantment level.
 * Max level: 255. Works on: pickaxe, shovel, axe, hoe.
 * Sneaking disables AoE for precise mining.
 */
public class AOEEnchantmentModule extends PluginModule {

    public AOEEnchantmentModule() {
        super("AOEEnchantment", "enchantment/aoe", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;

        // ─── 1. Register the block break listener ───
        main.getServer().getPluginManager().registerEvents(new AOEEnchantmentListener(), main);

        // ─── 2. Register the PDC failsafe sync listener + periodic scan ───
        AOEEnchantmentSyncListener.register(main);

        ConsoleLogger.info("[AoE] Enchantment module initialized.");
        ConsoleLogger.info("[AoE] Max level: 255 | Radius = level | Tools: pickaxe, shovel, axe, hoe");
        ConsoleLogger.info("[AoE] Sneak to disable AoE for precise mining");
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        // Nothing to clean up
    }
}
