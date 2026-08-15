package com.ultimateimprovments.core;

import com.ultimateimprovments.command.PluginReloadCommand;
import com.ultimateimprovments.command.SubCommandRegistry;
import com.ultimateimprovments.core.TaskManager;
import com.ultimateimprovments.listener.FishingListener;
import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.whitelist.OpWhitelistManager;
import com.ultimateimprovments.display.TabManager;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.mechanics.security.check.CheckManager;
import com.ultimateimprovments.structure.StructureChunkTracker;
import com.ultimateimprovments.structure.StructureMarker;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;

/**
 * PluginShutdown — the shutdown nesting doll of UltimateImprovments.
 * <p>
 * Called from {@link Main#onDisable()}.
 * Stops modules, saves data, cleans up state — in the right order.
 */
public class PluginShutdown {

    private final Main plugin;
    private boolean shutdownPerformed = false;

    public PluginShutdown(Main plugin) {
        this.plugin = plugin;
    }

    // ==========================================================================
    // 🛑 SHUTDOWN — the root of the nesting doll
    // ==========================================================================

    public void shutdownPlugin() {
        // Guard: prevents double shutdown (from /ui reload + PlugMan onDisable)
        if (shutdownPerformed) {
            ConsoleLogger.info("[Shutdown] Already performed, skipping.");
            return;
        }
        shutdownPerformed = true;

        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  UltimateImprovments — Shutting down...");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("");

        shutdownModules();
        savePersistentData();
        stopBackgroundTasks();
        cleanupPluginState();

        printBanner();
        ConsoleLogger.info("[PLUGIN] Disabled");
    }

    /**
     * Prints a text banner with the DISABLED status when the plugin shuts down.
     */
    private void printBanner() {
        ConsoleLogger.info("");
        ConsoleLogger.info("==================================================");
        ConsoleLogger.info("  UltimateImprovments v" + plugin.getDescription().getVersion());
        ConsoleLogger.raw("<white>  Status: </white><red>DISABLED</red>");
        ConsoleLogger.info("==================================================");
        ConsoleLogger.info("");
    }

    // ==========================================================================
    // 📦 PHASE 1: MODULE SHUTDOWN
    // ==========================================================================

    private void shutdownModules() {
        var mm = ModuleManager.getInstance();
        if (mm != null) {
            mm.shutdownAll();
        }
        ConsoleLogger.info("[Shutdown] Modules shut down.");
    }

    // ==========================================================================
    // 💾 PHASE 2: DATA SAVING
    // ==========================================================================

    private void savePersistentData() {
        StructureChunkTracker.save();
        // Save all structure data to the DB on shutdown (the DB is the source of truth)
        StructureMarker.saveAll();
        ConsoleLogger.info("[Shutdown] Persistent data saved.");
    }

    // ==========================================================================
    // ⏹ PHASE 3: STOP BACKGROUND TASKS
    // ==========================================================================

    private void stopBackgroundTasks() {
        com.ultimateimprovments.server.AccessListCheckTask.stop();

        // ⛔ Cancel ALL plugin tasks (sync and async).
        // Critical for /ui reload: some modules start repeating tasks
        // with an empty onDisable (or without cancelling the old task in init()),
        // which DUPLICATES tasks after every reload. cancelTasks() kills
        // everything at once, and at startup the modules create fresh tasks anew.
        Bukkit.getScheduler().cancelTasks(plugin);

        // cancelTasks() does not clear the internal task field of BukkitRunnable,
        // so for singletons (FishingListener) a repeated runTaskTimer()
        // would fail with "Already scheduled" → the module would not start.
        // Reset explicitly, regardless of the module shutdown order.
        TaskManager.resetBukkitRunnableTask(FishingListener.getInstance());
        ConsoleLogger.info("[Shutdown] Background tasks stopped.");
    }

    // ==========================================================================
    // 🧹 PHASE 4: STATE CLEANUP
    // ==========================================================================

    private void cleanupPluginState() {
        // Unregister ALL event listeners for this plugin — critical for /ui reload;
        // otherwise on repeated registerEvents() the old listeners remain and events fire twice
        HandlerList.unregisterAll(plugin);

        OpWhitelistManager.shutdown();
        TabManager.resetListenerState();
        CheckManager.shutdown();

        // GitHub 2FA HTTP server — close the port on shutdown/reload
        com.ultimateimprovments.mechanics.security.auth.GithubAuthServer.shutdown();

        // Reset the startup flag so the guard does not falsely trigger on the next start
        PluginStartup.resetStartupFlag();

        // Reset the subcommand registry and init flag for a correct /ui reload
        SubCommandRegistry.reset();
        PluginReloadCommand.reset();

        ConsoleLogger.info("[Shutdown] Plugin state cleaned up.");
    }
}
