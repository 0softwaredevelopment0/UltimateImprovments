package com.ultimateimprovments.core;

import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

/**
 * UIPlayer — the player-features addon (armor/trim effects, attributes, vanish,
 * elytra boost, leash, mode-protect, shield slowness, join-invulnerable).
 * Modules are declared in {@link PlayerModules}.
 */
public class UIPlayer extends JavaPlugin {

    private static final Set<String> OWNED_MODULES = Set.of(
            "ArmorEffects", "ArmorTrimEffects", "Attributes", "JoinInvulnerableReset",
            "ModeProtect", "ShieldSlowness", "Vanish", "Leash", "ElytraBoost");

    private static UIPlayer instance;

    public static UIPlayer getInstance() { return instance; }

    @Override
    public void onEnable() {
        instance = this;

        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  UI-Player v" + getDescription().getVersion());
        ConsoleLogger.info("===========================================");

        ModuleManager mm = ModuleManager.getInstance();
        if (mm == null) {
            ConsoleLogger.error("[UI-Player] UI-Core not loaded! Disabling...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PlayerModules.registerAll(mm);
        mm.initAll();
        reportModuleStats(mm);

        ConsoleLogger.success("[UI-Player] Player features enabled!");
    }

    @Override
    public void onDisable() {
        ConsoleLogger.info("[UI-Player] Disabling...");
        HandlerList.unregisterAll(this);
        ModuleManager mm = ModuleManager.getInstance();
        if (mm != null) {
            mm.shutdownAll();
        }
        instance = null;
        ConsoleLogger.success("[UI-Player] Disabled!");
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
            ConsoleLogger.warn("[UI-Player] Module stats report failed: " + t.getMessage());
        }
    }
}
