package com.mcplugin.module;

import com.mcplugin.core.Main;
import com.mcplugin.util.ConsoleLogger;
import com.mcplugin.energy.machines.assembler.AssemblerManager;
import com.mcplugin.energy.machines.assembler.AssemblerTask;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Модуль сборщика предметов — CRAFTER + рамка наверху.
 * Собирается shift+ПКМ и авто-крафтит предметы раз в 2 тика.
 */
public class AssemblerModule extends PluginModule {

    private BukkitTask assemblerTask;

    public AssemblerModule() {
        super("Assembler", "energy/machines/assembler", true);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;

        AssemblerManager.init();

        // Запускаем таск авто-крафта раз в 2 тика
        // ⚠ Paper 1.21.4+: BukkitRunnable нельзя передавать в Scheduler.runTaskTimer()
        assemblerTask = new AssemblerTask().runTaskTimer(main, 40L, 2L);

        ConsoleLogger.info("[AssemblerModule] ✔ Assembler system initialized.");
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        if (assemblerTask != null) {
            assemblerTask.cancel();
            assemblerTask = null;
        }
        AssemblerManager.shutdown();
    }
}
