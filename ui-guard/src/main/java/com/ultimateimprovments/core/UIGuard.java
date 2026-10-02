package com.ultimateimprovments.core;

import com.ultimateimprovments.command.SubCommandRegistry;
import com.ultimateimprovments.command.subcommands.CheckSubcommand;
import com.ultimateimprovments.command.subcommands.CodePaneSubcommand;
import com.ultimateimprovments.command.subcommands.ConsoleSubcommand;
import com.ultimateimprovments.command.subcommands.LegacySubCommandAdapter;
import com.ultimateimprovments.command.subcommands.MaintSubcommand;
import com.ultimateimprovments.command.subcommands.RedstoneSubcommand;
import com.ultimateimprovments.command.subcommands.ServerSubcommand;
import com.ultimateimprovments.command.subcommands.SudoSubcommand;
import com.ultimateimprovments.command.subcommands.StressTestSubcommand;
import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

/**
 * UIGuard — the server-guard addon (packet/proxy/redstone guard, bot protection,
 * server overload). Modules are declared in {@link GuardModules}.
 */
public class UIGuard extends JavaPlugin implements SoftReloadable {

    private static final Set<String> OWNED_MODULES = Set.of(
            "RedstoneGuard", "PacketGuard", "ProxyServer", "BotProtection", "ServerOverload",
            "Check", "CodePanel", "Sudo", "Maintenance", "ConsoleLockdown", "ServerLockdown",
            "StressTest");

    private static UIGuard instance;

    public static UIGuard getInstance() { return instance; }

    @Override
    public void onEnable() {
        runStartup();
    }

    @Override
    public void onDisable() {
        ConsoleLogger.info("[UI-Guard] Disabling...");
        HandlerList.unregisterAll(this);
        ModuleManager mm = ModuleManager.getInstance();
        if (mm != null) {
            mm.shutdownAll();
        }
        instance = null;
        ConsoleLogger.success("[UI-Guard] Disabled!");
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
        ConsoleLogger.info("[UI-Guard] Soft reload (in place)...");
        HandlerList.unregisterAll(this);
        runStartup();
    }

    private void runStartup() {
        instance = this;

        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  UI-Guard v" + getPluginMeta().getVersion());
        ConsoleLogger.info("===========================================");

        ModuleManager mm = ModuleManager.getInstance();
        if (mm == null) {
            ConsoleLogger.error("[UI-Guard] UI-Core not loaded! Disabling...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        GuardModules.registerAll(mm);
        mm.initAll();

        registerCommands();
        reportModuleStats(mm);

        ConsoleLogger.success("[UI-Guard] Server guard enabled!");
    }

    /** Registers the addon's {@code /ui} subcommands (static utilities). */
    private void registerCommands() {
        try {
            SubCommandRegistry registry = SubCommandRegistry.getInstance();
            registry.register(LegacySubCommandAdapter.of("redstone",
                    RedstoneSubcommand::execute,
                    (s, a) -> RedstoneSubcommand.tabComplete(a)));
            registry.register(LegacySubCommandAdapter.of("check", CheckSubcommand::execute));
            registry.register(LegacySubCommandAdapter.of("uncheck",
                    (s, a) -> { CheckSubcommand.uncheck(s, a); return true; }));
            registry.register(LegacySubCommandAdapter.of("codepane",
                    CodePaneSubcommand::execute,
                    (s, a) -> CodePaneSubcommand.tabComplete(a)));
            registry.register(LegacySubCommandAdapter.of("maint",
                    MaintSubcommand::execute,
                    (s, a) -> MaintSubcommand.tabComplete(a)));
            registry.register(LegacySubCommandAdapter.of("console",
                    ConsoleSubcommand::execute,
                    (s, a) -> ConsoleSubcommand.tabComplete(a)));
            registry.register(LegacySubCommandAdapter.of("server",
                    ServerSubcommand::execute,
                    (s, a) -> ServerSubcommand.tabComplete(a)));
            registry.register(LegacySubCommandAdapter.of("sudo",
                    SudoSubcommand::execute,
                    (s, a) -> SudoSubcommand.tabComplete(a)));
            registry.register(LegacySubCommandAdapter.of("stresstest",
                    StressTestSubcommand::execute,
                    (s, a) -> StressTestSubcommand.tabComplete(a)));
        } catch (Exception e) {
            ConsoleLogger.warn("[UI-Guard] Failed to register commands: " + e.getMessage());
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
            ConsoleLogger.warn("[UI-Guard] Module stats report failed: " + t.getMessage());
        }
    }
}
