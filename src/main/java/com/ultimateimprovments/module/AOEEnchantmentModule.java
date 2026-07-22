package com.ultimateimprovments.module;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.enchantment.AOEEnchantment;
import com.ultimateimprovments.enchantment.AOEEnchantmentListener;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Module: AoE (Area of Effect) Enchantment.
 * <p>
 * Регистрирует кастомное зачарование через PDC и слушатель событий.
 * <p>
 * Зачарование ломает блоки того же типа в радиусе = уровень зачарования.
 * Макс. уровень: 255. Работает на всех инструментах.
 * При шифте (Sneak) AoE отключается для точного копания.
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

        ConsoleLogger.info("[AoE] Enchantment module initialized.");
        ConsoleLogger.info("[AoE] Max level: 255 | Radius = level | Tools: pickaxe, shovel, axe, hoe");
        ConsoleLogger.info("[AoE] Sneak to disable AoE for precise mining");
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        // Nothing to clean up
    }
}
