package com.ultimateimprovments.core;

import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

/**
 * UIDisplay — the UI (tab list / scoreboard / boss bar) addon.
 * Modules are declared in {@link DisplayModules}. Vanish state is read through
 * {@code CoreHooks.isVanished}, so this addon does not depend on the player addon.
 */
public class UIDisplay extends JavaPlugin {

    private static final Set<String> OWNED_MODULES = Set.of("Tab", "Scoreboard", "BossBar");

    private static UIDisplay instance;

    public static UIDisplay getInstance() { return instance; }

    @Override
    public void onEnable() {
        instance = this;

        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  UI-Display v" + getPluginMeta().getVersion());
        ConsoleLogger.info("===========================================");

        ModuleManager mm = ModuleManager.getInstance();
        if (mm == null) {
            ConsoleLogger.error("[UI-Display] UI-Core not loaded! Disabling...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        DisplayModules.registerAll(mm);
        mm.initAll();
        reportModuleStats(mm);

        ConsoleLogger.success("[UI-Display] Tab / scoreboard / boss bar enabled!");
    }

    @Override
    public void onDisable() {
        ConsoleLogger.info("[UI-Display] Disabling...");
        HandlerList.unregisterAll(this);
        ModuleManager mm = ModuleManager.getInstance();
        if (mm != null) {
            mm.shutdownAll();
        }
        instance = null;
        ConsoleLogger.success("[UI-Display] Disabled!");
    }

    /** Feeds only this addon's module counters/failures into AddonRegistry. */
    private void reportModuleStats(ModuleManager mm) {
        try {
            int total = 0;
            int loaded = 0;
            for (var m : mm.getModules()) {
                if (!OWNED_MODULES.contains(m.getName())) continue;
                total++;
                if (m.isEnabled()) {
                    loaded++;
                } else {
                    com.ultimateimprovments.addon.AddonRegistry.reportError(getName(),
                            "module " + m.getName() + " failed: " + m.getDisableReason());
                }
            }
            com.ultimateimprovments.addon.AddonRegistry.reportModules(getName(), loaded, total);
        } catch (Throwable t) {
            ConsoleLogger.warn("[UI-Display] Module stats report failed: " + t.getMessage());
        }
    }
}
