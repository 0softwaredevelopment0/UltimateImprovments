package com.ultimateimprovments.core;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.command.SubCommandRegistry;
import com.ultimateimprovments.command.subcommands.LegacySubCommandAdapter;
import com.ultimateimprovments.mechanics.features.omniscanner.AdminMenuGUI;
import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

/**
 * UIItems — the custom-items addon (crafting recipes, item tools, particle
 * accelerator, scanner items, omniscanner + admin menu).
 * Modules are declared in {@link ItemsModules}.
 */
public class UIItems extends JavaPlugin implements SoftReloadable {

    private static final Set<String> OWNED_MODULES = Set.of(
            "Crafting", "EntityLocator", "Waypoint", "Antimatter", "ExpBottleUpgrade",
            "Notes", "UnbreakableBreaker", "ParticleAccelerator", "ItemEnchantListeners",
            "Omniscanner");

    private static UIItems instance;

    public static UIItems getInstance() { return instance; }

    @Override
    public void onEnable() {
        runStartup();
    }

    @Override
    public void onDisable() {
        ConsoleLogger.info("[UI-Items] Disabling...");
        HandlerList.unregisterAll(this);
        ModuleManager mm = ModuleManager.getInstance();
        if (mm != null) {
            mm.shutdownAll();
        }
        instance = null;
        ConsoleLogger.success("[UI-Items] Disabled!");
    }

    /**
     * In-place reload (soft /ui reload): private cleanup + the same startup
     * path as onEnable. Never disables the plugin — on Paper that would close
     * the JAR and re-enabling does not reopen it ("zip file closed" zombie).
     * Re-registering this addon's modules REPLACES the old instances in the
     * shared ModuleManager (see ModuleManager.register).
     */
    @Override
    public void softReload() {
        ConsoleLogger.info("[UI-Items] Soft reload (in place)...");
        HandlerList.unregisterAll(this);
        runStartup();
    }

    private void runStartup() {
        instance = this;

        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  UI-Items v" + getPluginMeta().getVersion());
        ConsoleLogger.info("===========================================");

        ModuleManager mm = ModuleManager.getInstance();
        if (mm == null) {
            ConsoleLogger.error("[UI-Items] UI-Core not loaded! Disabling...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ItemsModules.registerAll(mm);
        mm.initAll();

        registerCommands();
        reportModuleStats(mm);

        ConsoleLogger.success("[UI-Items] Custom items enabled!");
    }

    /** Registers the addon's {@code /ui} subcommands. */
    private void registerCommands() {
        try {
            SubCommandRegistry registry = SubCommandRegistry.getInstance();
            registry.register(LegacySubCommandAdapter.of("menu", (s, a) -> {
                if (!(s instanceof Player p)) return false;
                if (!p.hasPermission("ui.command.menu")) {
                    CommandErrors.noPermission(p);
                    return true;
                }
                AdminMenuGUI.open(p);
                return true;
            }));
        } catch (Exception e) {
            ConsoleLogger.warn("[UI-Items] Failed to register commands: " + e.getMessage());
        }
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
            ConsoleLogger.warn("[UI-Items] Module stats report failed: " + t.getMessage());
        }
    }
}
