package com.ultimateimprovments.module;

import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.nms.PacketHandler;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * AntiCheatModule — the modular anti-cheat system.
 * <p>
 * Initializes AntiCheatManager and registers all checks.
 * Checks are split into 4 categories: COMBAT, MOVEMENT, WORLD, MISC.
 */
public class AntiCheatModule extends PluginModule {

    public AntiCheatModule() {
        super("AntiCheat", "mechanics/security/anticheat", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        boolean enabled = plugin.getConfig().getBoolean("anticheat.enabled", false);

        AntiCheatManager.init();
        AntiCheatManager acm = AntiCheatManager.getInstance();

        // Set enabled from config (so /ui ac toggle on can enable it)
        acm.setGlobalEnabled(enabled);

        // Register all checks (grouped log: one line for the whole anti-cheat)
        ConsoleLogger.info("[AntiCheat] Enabling anti-cheat checks...");
        registerAllChecks(acm);
        int total = acm.getAllChecks().size();
        int active = (int) acm.getAllChecks().stream().filter(c -> c.isEnabled()).count();

        // Start VL decay task
        acm.startDecayTask();

        // Register join/quit listener for PlayerData management
        plugin.getServer().getPluginManager().registerEvents(
                new com.ultimateimprovments.mechanics.security.anticheat.AntiCheatListener(), plugin);

        // Initialize NMS packet interception (injects ChannelDuplexHandler into Netty pipeline)
        // MUST succeed — the anti-cheat works ONLY with NMS interception
        try {
            PacketHandler.init();
            if (PacketHandler.getInstance() == null) {
                throw new RuntimeException("PacketHandler.init() did not initialize instance");
            }
        } catch (Exception e) {
            throw new RuntimeException("[AntiCheat] CRITICAL: PacketHandler failed to initialize. "
                    + "AntiCheat REQUIRES Netty packet interception. Error: " + e.getMessage(), e);
        }

        if (enabled) {
            ConsoleLogger.info("[AntiCheat] Enabled " + total + " checks (" + active + " active). Packet interception: ACTIVE.");
        } else {
            ConsoleLogger.info("[AntiCheat] Disabled " + total + " checks (config). Use /ui ac toggle on to enable.");
        }
    }

    private void registerAllChecks(AntiCheatManager acm) {
        // COMBAT checks
        for (var check : com.ultimateimprovments.mechanics.security.anticheat.combat.CombatChecks.createAll()) {
            acm.registerCheck(check);
        }

        // MOVEMENT checks
        for (var check : com.ultimateimprovments.mechanics.security.anticheat.movement.MovementChecks.createAll()) {
            acm.registerCheck(check);
        }

        // WORLD checks
        for (var check : com.ultimateimprovments.mechanics.security.anticheat.world.WorldChecks.createAll()) {
            acm.registerCheck(check);
        }

        // MISC checks
        for (var check : com.ultimateimprovments.mechanics.security.anticheat.misc.MiscChecks.createAll()) {
            acm.registerCheck(check);
        }
    }

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        if (AntiCheatManager.getInstance() != null) {
            AntiCheatManager.getInstance().reloadAll();
        }
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        ConsoleLogger.info("[AntiCheat] Disabling anti-cheat checks...");
        PacketHandler.shutdown();
        AntiCheatManager.shutdown();
        ConsoleLogger.info("[AntiCheat] Anti-cheat disabled.");
    }
}
