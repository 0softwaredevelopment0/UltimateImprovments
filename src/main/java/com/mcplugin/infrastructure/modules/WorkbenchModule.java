package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.energy.machines.workbench.EnergyCraftingListener;
import com.mcplugin.energy.machines.workbench.EnergyWorkbenchManager;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class WorkbenchModule extends PluginModule {

    private EnergyCraftingListener craftingListener;
    private BukkitTask lockTask;

    public WorkbenchModule() { super("Energy Workbench", "energy/machines/workbench", true); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;
        EnergyWorkbenchManager.init();
        craftingListener = new EnergyCraftingListener();
        main.getServer().getPluginManager().registerEvents(craftingListener, main);
        main.getServer().getPluginManager().registerEvents(new EnergyWorkbenchManager.RedstoneListener(), main);

        // Блокируем авто-крафт CRAFTER по редстоуну каждый тик
        lockTask = Bukkit.getScheduler().runTaskTimer(main, () -> {
            EnergyWorkbenchManager.maintainLocks();
        }, 0L, 1L);

        // Заряжаем буферы Assembler'ов от соседних кабелей каждые 2 тика
        Bukkit.getScheduler().runTaskTimer(main, () -> {
            EnergyWorkbenchManager.chargeAllBuffers();
        }, 0L, 2L);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        if (craftingListener != null) {
            HandlerList.unregisterAll(craftingListener);
            craftingListener = null;
        }
        if (lockTask != null) {
            lockTask.cancel();
            lockTask = null;
        }
    }
}
