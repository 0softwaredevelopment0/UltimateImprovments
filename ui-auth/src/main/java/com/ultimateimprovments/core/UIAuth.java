package com.ultimateimprovments.core;

import com.ultimateimprovments.command.SubCommandRegistry;
import com.ultimateimprovments.command.subcommands.AuthSubcommand;
import com.ultimateimprovments.command.subcommands.LegacySubCommandAdapter;
import com.ultimateimprovments.mechanics.security.auth.AuthDialogHandler;
import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * UIAuth — the player-authentication addon.
 * <p>
 * Owns the auth system: login/2FA flow, freeze listeners, DB, the auth dialog
 * and the {@code /ui auth} command. Its module is declared in
 * {@link AuthModules}. {@code AuthPlayerState} publishes the pending-auth state
 * to {@code CoreHooks} so unrelated addons can respect the freeze.
 */
public class UIAuth extends JavaPlugin {

    /** Module path prefix owned by this addon. */
    private static final String PATH_PREFIX = "mechanics/security/auth";

    private static UIAuth instance;

    public static UIAuth getInstance() { return instance; }

    @Override
    public void onEnable() {
        instance = this;

        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  UI-Auth v" + getPluginMeta().getVersion());
        ConsoleLogger.info("===========================================");

        ModuleManager mm = ModuleManager.getInstance();
        if (mm == null) {
            ConsoleLogger.error("[UI-Auth] UI-Core not loaded! Disabling...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        AuthModules.registerAll(mm);
        mm.initAll();

        // Dialog handler for the auth screen (submit/cancel buttons).
        AuthDialogHandler.register(this);
        registerAuthCommand();
        reportModuleStats(mm);

        ConsoleLogger.success("[UI-Auth] Authentication enabled!");
    }

    @Override
    public void onDisable() {
        ConsoleLogger.info("[UI-Auth] Disabling...");
        HandlerList.unregisterAll(this);
        ModuleManager mm = ModuleManager.getInstance();
        if (mm != null) {
            mm.shutdownAll();
        }
        instance = null;
        ConsoleLogger.success("[UI-Auth] Disabled!");
    }

    /**
     * Registers {@code /ui auth}. The subcommand is a static utility, so the
     * owning addon registers it here.
     */
    private void registerAuthCommand() {
        try {
            SubCommandRegistry.getInstance().register(
                    LegacySubCommandAdapter.of("auth", AuthSubcommand::execute));
        } catch (Exception e) {
            ConsoleLogger.warn("[UI-Auth] Failed to register /ui auth: " + e.getMessage());
        }
    }

    /** Feeds only this addon's module counters/failures into AddonRegistry. */
    private void reportModuleStats(ModuleManager mm) {
        try {
            int total = 0;
            int loaded = 0;
            for (var m : mm.getModules()) {
                if (!m.getModulePath().startsWith(PATH_PREFIX)) continue;
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
            ConsoleLogger.warn("[UI-Auth] Module stats report failed: " + t.getMessage());
        }
    }
}
