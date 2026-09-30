package com.ultimateimprovments.core;

import com.ultimateimprovments.command.SubCommandRegistry;
import com.ultimateimprovments.command.subcommands.EnchantSubcommand;
import com.ultimateimprovments.command.subcommands.LegacySubCommandAdapter;
import com.ultimateimprovments.enchantment.EnchantModules;
import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * UIEnchant — the custom-enchantments addon.
 * <p>
 * Owns all {@code ui:*} enchantments (charms + curses): the datapack-backed
 * enchantment registry, the PDC failsafe mirrors, the effect engines and the
 * {@code /ui enchant} command. Its modules are declared in
 * {@link EnchantModules}. The datapack content itself lives in UI-Datapack
 * (soft-depend); its {@code DatapackGate} is consulted while the modules register.
 */
public class UIEnchant extends JavaPlugin implements SoftReloadable {

    /** Module path prefix owned by this addon. */
    private static final String PATH_PREFIX = "enchantment/";

    private static UIEnchant instance;

    public static UIEnchant getInstance() { return instance; }

    @Override
    public void onEnable() {
        runStartup();
    }

    @Override
    public void onDisable() {
        ConsoleLogger.info("[UI-Enchant] Disabling...");
        HandlerList.unregisterAll(this);
        ModuleManager mm = ModuleManager.getInstance();
        if (mm != null) {
            mm.shutdownAll();
        }
        instance = null;
        ConsoleLogger.success("[UI-Enchant] Disabled!");
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
        ConsoleLogger.info("[UI-Enchant] Soft reload (in place)...");
        HandlerList.unregisterAll(this);
        runStartup();
    }

    private void runStartup() {
        instance = this;

        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  UI-Enchant v" + getPluginMeta().getVersion());
        ConsoleLogger.info("===========================================");

        ModuleManager mm = ModuleManager.getInstance();
        if (mm == null) {
            ConsoleLogger.error("[UI-Enchant] UI-Core not loaded! Disabling...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        EnchantModules.registerAll(mm);
        mm.initAll();

        registerEnchantCommand();
        reportModuleStats(mm);

        ConsoleLogger.success("[UI-Enchant] All enchantments enabled!");
    }

    /**
     * Registers {@code /ui enchant}. The subcommand is a static utility
     * (not a scanned {@code SubCommand}), so the owning addon registers it here.
     */
    private void registerEnchantCommand() {
        try {
            SubCommandRegistry.getInstance().register(LegacySubCommandAdapter.of(
                    "enchant",
                    EnchantSubcommand::execute,
                    (s, a) -> EnchantSubcommand.tabComplete(s, a)));
        } catch (Exception e) {
            ConsoleLogger.warn("[UI-Enchant] Failed to register /ui enchant: " + e.getMessage());
        }
    }

    /**
     * Feeds only THIS addon's module counters + failures into AddonRegistry so
     * {@code /ui addon status UI-Enchant} and the load-error counters stay truthful.
     */
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
            ConsoleLogger.warn("[UI-Enchant] Module stats report failed: " + t.getMessage());
        }
    }
}
