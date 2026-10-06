package com.ultimateimprovments.core;

import com.ultimateimprovments.command.SubCommandRegistry;
import com.ultimateimprovments.command.subcommands.EconomySubcommand;
import com.ultimateimprovments.command.subcommands.LegacySubCommandAdapter;
import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

/**
 * UIAdmin — the admin addon: economy, op management and the updater, plus their
 * {@code /ui} commands. Modules are declared in {@link AdminModules}.
 */
public class UIAdmin extends JavaPlugin implements SoftReloadable {

    private static final Set<String> OWNED_MODULES = Set.of("Economy");

    private static UIAdmin instance;

    public static UIAdmin getInstance() { return instance; }

    @Override
    public void onEnable() {
        runStartup();
    }

    @Override
    public void onDisable() {
        ConsoleLogger.info("[UI-Admin] Disabling...");
        HandlerList.unregisterAll(this);
        com.ultimateimprovments.op.OpManager.shutdown();
        com.ultimateimprovments.op.OpSelfSubcommand.shutdown();
        ModuleManager mm = ModuleManager.getInstance();
        if (mm != null) {
            mm.shutdownAll();
        }
        instance = null;
        ConsoleLogger.success("[UI-Admin] Disabled!");
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
        ConsoleLogger.info("[UI-Admin] Soft reload (in place)...");
        HandlerList.unregisterAll(this);
        com.ultimateimprovments.op.OpManager.shutdown();
        com.ultimateimprovments.op.OpSelfSubcommand.shutdown();
        runStartup();
    }

    private void runStartup() {
        instance = this;

        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  UI-Admin v" + getPluginMeta().getVersion());
        ConsoleLogger.info("===========================================");

        ModuleManager mm = ModuleManager.getInstance();
        if (mm == null) {
            ConsoleLogger.error("[UI-Admin] UI-Core not loaded! Disabling...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        AdminModules.registerAll(mm);
        mm.initAll();

        com.ultimateimprovments.op.OpManager.init();
        registerCommands();
        reportModuleStats(mm);

        ConsoleLogger.success("[UI-Admin] Economy / OP enabled!");
    }

    /** Registers the addon's {@code /ui} subcommands (static utilities). */
    private void registerCommands() {
        SubCommandRegistry reg = SubCommandRegistry.getInstance();
        try {
            reg.register(LegacySubCommandAdapter.of("money", EconomySubcommand::execute,
                    (s, a) -> EconomySubcommand.tabComplete(a)));
            reg.register(LegacySubCommandAdapter.of("op",
                    com.ultimateimprovments.op.OpSubcommand::execute,
                    (s, a) -> com.ultimateimprovments.op.OpSubcommand.tabComplete(a)));
            reg.register(LegacySubCommandAdapter.of("deop",
                    com.ultimateimprovments.op.DeopSubcommand::execute,
                    (s, a) -> com.ultimateimprovments.op.DeopSubcommand.tabComplete(a)));
            reg.register(LegacySubCommandAdapter.of("oplist",
                    com.ultimateimprovments.op.OpListSubcommand::execute,
                    (s, a) -> com.ultimateimprovments.op.OpListSubcommand.tabComplete(a)));
            reg.register(LegacySubCommandAdapter.of("opself",
                    com.ultimateimprovments.op.OpSelfSubcommand::execute,
                    (s, a) -> com.ultimateimprovments.op.OpSelfSubcommand.tabComplete(s, a)));
        } catch (Exception e) {
            ConsoleLogger.warn("[UI-Admin] Failed to register commands: " + e.getMessage());
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
            ConsoleLogger.warn("[UI-Admin] Module stats report failed: " + t.getMessage());
        }
    }
}
