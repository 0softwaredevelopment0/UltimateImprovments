package com.ultimateimprovments.module;

import com.ultimateimprovments.energy.storage.battery.BatteryManager;
import com.ultimateimprovments.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class BatteryMultiModule extends PluginModule {

    private org.bukkit.scheduler.BukkitTask tickTask;

    public BatteryMultiModule() { super("Battery Multi", "energy/storage/battery", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        BatteryManager.init();
        tickTask = Bukkit.getScheduler().runTaskTimer((Main) plugin, () -> {
            BatteryManager.tick();
        }, 0L, 1L);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        // Marker'ы сохраняются в world-файлах, save не нужен
    }
}
