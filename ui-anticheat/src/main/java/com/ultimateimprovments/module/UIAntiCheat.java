package com.ultimateimprovments.module;

import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatListener;
import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.nms.PacketHandler;
import com.ultimateimprovments.command.SubCommandRegistry;
import com.ultimateimprovments.command.subcommands.AcStatsSubcommand;
import com.ultimateimprovments.command.subcommands.LegacySubCommandAdapter;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;

public final class UIAntiCheat extends JavaPlugin implements com.ultimateimprovments.core.SoftReloadable {

    private static UIAntiCheat instance;

    public static UIAntiCheat getInstance() { return instance; }

    @Override
    public void onEnable() {
        runStartup();
    }

    @Override
    public void onDisable() {
        ConsoleLogger.info("[AntiCheat] Disabling...");
        PacketHandler.shutdown();
        AntiCheatManager.shutdown();
        HandlerList.unregisterAll(this);
        ConsoleLogger.info("[AntiCheat] Disabled.");
    }

    /**
     * In-place reload (soft /ui reload): full private shutdown + startup path.
     * Never disables the plugin — on Paper that would close the JAR and
     * re-enabling does not reopen it ("zip file closed" zombie).
     */
    @Override
    public void softReload() {
        ConsoleLogger.info("[AntiCheat] Soft reload (in place)...");
        PacketHandler.shutdown();
        AntiCheatManager.shutdown();
        HandlerList.unregisterAll(this);
        runStartup();
    }

    private void runStartup() {
        instance = this;

        // Single config lives in UI-Core (Main.getInstance().getConfig()).
        boolean enabled = com.ultimateimprovments.core.Main.getInstance().getConfig()
                .getBoolean("anticheat.enabled", false);

        AntiCheatManager.init();
        AntiCheatManager acm = AntiCheatManager.getInstance();
        acm.setGlobalEnabled(enabled);

        ConsoleLogger.info("[AntiCheat] Enabling anti-cheat checks...");
        registerAllChecks(acm);
        int total = acm.getAllChecks().size();
        int active = (int) acm.getAllChecks().stream().filter(c -> c.isEnabled()).count();

        acm.startDecayTask();

        getServer().getPluginManager().registerEvents(new AntiCheatListener(), this);

        try {
            PacketHandler.init();
            if (PacketHandler.getInstance() == null) {
                throw new RuntimeException("PacketHandler.init() did not initialize instance");
            }
        } catch (Exception e) {
            throw new RuntimeException("[AntiCheat] CRITICAL: PacketHandler failed. " + e.getMessage(), e);
        }

        registerAcCommand();

        if (enabled) {
            ConsoleLogger.info("[AntiCheat] Enabled " + total + " checks (" + active + " active). Packet interception: ACTIVE.");
        } else {
            ConsoleLogger.info("[AntiCheat] Disabled " + total + " checks. Use /ui ac toggle on to enable.");
        }
    }

    public void reloadAntiCheatConfig() {
        reloadConfig();
        if (AntiCheatManager.getInstance() != null) {
            AntiCheatManager.getInstance().reloadAll();
        }
    }

    private void registerAllChecks(AntiCheatManager acm) {
        for (var check : com.ultimateimprovments.mechanics.security.anticheat.combat.CombatChecks.createAll()) {
            acm.registerCheck(check);
        }
        for (var check : com.ultimateimprovments.mechanics.security.anticheat.movement.MovementChecks.createAll()) {
            acm.registerCheck(check);
        }
        for (var check : com.ultimateimprovments.mechanics.security.anticheat.world.WorldChecks.createAll()) {
            acm.registerCheck(check);
        }
        for (var check : com.ultimateimprovments.mechanics.security.anticheat.misc.MiscChecks.createAll()) {
            acm.registerCheck(check);
        }
    }

    private void registerAcCommand() {
        try {
            SubCommandRegistry registry = SubCommandRegistry.getInstance();
            if (registry != null) {
                registry.register(LegacySubCommandAdapter.of("ac", AcStatsSubcommand::execute));
                ConsoleLogger.info("[AntiCheat] Registered /ui ac command.");
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[AntiCheat] Failed to register /ui ac: " + e.getMessage());
        }
    }
}
