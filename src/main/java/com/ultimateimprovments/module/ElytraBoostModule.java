package com.ultimateimprovments.module;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.features.items.ChestplateFlightListener;
import com.ultimateimprovments.mechanics.features.items.NetheriteUpgradeListener;
import com.ultimateimprovments.mechanics.features.items.TotemChargeListener;
import com.ultimateimprovments.mechanics.features.player.ElytraBoostManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль ElytraBoost — нажатие пробела во время полёта на элитрах
 * запускает фейерверк из инвентаря для ускорения.
 * <p>
 * Не essential — можно отключить без потери основной функциональности.
 */
public class ElytraBoostModule extends PluginModule {

    public ElytraBoostModule() {
        super("ElytraBoost", "mechanics/features/elytra_boost", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;
        ElytraBoostManager.init(main);
        main.getServer().getPluginManager().registerEvents(new ChestplateFlightListener(), main);
        main.getServer().getPluginManager().registerEvents(new NetheriteUpgradeListener(), main);
        main.getServer().getPluginManager().registerEvents(new TotemChargeListener(), main);
        TotemChargeListener.startPeriodicLoreCheck();
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        // Nothing to clean up
    }
}
