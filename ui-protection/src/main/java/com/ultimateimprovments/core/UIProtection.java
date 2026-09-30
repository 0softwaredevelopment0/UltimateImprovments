package com.ultimateimprovments.core;

import com.ultimateimprovments.command.SubCommandRegistry;
import com.ultimateimprovments.command.subcommands.LegacySubCommandAdapter;
import com.ultimateimprovments.command.subcommands.ProtectionSubcommand;
import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

/**
 * UIProtection — the block/void protection addon.
 * <p>
 * Owns the ProtectionBlock system (crafting, GUI, DB, holograms) and the void
 * protection listener, plus the {@code /ui protection} command. Modules are
 * declared in {@link ProtectionModules}.
 */
public class UIProtection extends JavaPlugin implements SoftReloadable {

    /** Modules owned by this addon (path "infrastructure/listeners" is generic). */
    private static final Set<String> OWNED_MODULES = Set.of("Protection", "VoidProtection");

    private static UIProtection instance;

    public static UIProtection getInstance() { return instance; }

    @Override
    public void onEnable() {
        runStartup();
    }

    @Override
    public void onDisable() {
        ConsoleLogger.info("[UI-Protection] Disabling...");
        HandlerList.unregisterAll(this);
        ModuleManager mm = ModuleManager.getInstance();
        if (mm != null) {
            mm.shutdownAll();
        }
        instance = null;
        ConsoleLogger.success("[UI-Protection] Disabled!");
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
        ConsoleLogger.info("[UI-Protection] Soft reload (in place)...");
        HandlerList.unregisterAll(this);
        runStartup();
    }

    private void runStartup() {
        instance = this;

        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  UI-Protection v" + getPluginMeta().getVersion());
        ConsoleLogger.info("===========================================");

        ModuleManager mm = ModuleManager.getInstance();
        if (mm == null) {
            ConsoleLogger.error("[UI-Protection] UI-Core not loaded! Disabling...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ProtectionModules.registerAll(mm);
        mm.initAll();

        registerProtectionCommand();
        reportModuleStats(mm);

        ConsoleLogger.success("[UI-Protection] Protection enabled!");
    }

    /** Registers {@code /ui protection} (static utility subcommand). */
    private void registerProtectionCommand() {
        try {
            SubCommandRegistry.getInstance().register(LegacySubCommandAdapter.of("protection",
                    (s, a) -> { ProtectionSubcommand.execute(s, a); return true; }));
        } catch (Exception e) {
            ConsoleLogger.warn("[UI-Protection] Failed to register /ui protection: " + e.getMessage());
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
            ConsoleLogger.warn("[UI-Protection] Module stats report failed: " + t.getMessage());
        }
    }
}
