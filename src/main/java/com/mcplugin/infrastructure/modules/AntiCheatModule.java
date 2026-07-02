package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.util.ConsoleLogger;
import com.mcplugin.mechanics.security.anticheat.AntiCheatManager;
import com.mcplugin.mechanics.security.anticheat.nms.PacketHandler;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * AntiCheatModule — модульная система античита.
 * <p>
 * Инициализирует AntiCheatManager и регистрирует все проверки.
 * Проверки разделены на 4 категории: COMBAT, MOVEMENT, WORLD, MISC.
 */
public class AntiCheatModule extends PluginModule {

    public AntiCheatModule() {
        super("AntiCheat", "mechanics/security/anticheat", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        if (!plugin.getConfig().getBoolean("anticheat.enabled", true)) {
            ConsoleLogger.info("[AntiCheat] Disabled in config.");
            return;
        }

        AntiCheatManager.init();
        AntiCheatManager acm = AntiCheatManager.getInstance();

        // Register all checks
        registerAllChecks(acm);

        // Start VL decay task
        acm.startDecayTask();

        // Register join/quit listener for PlayerData management
        plugin.getServer().getPluginManager().registerEvents(
                new com.mcplugin.mechanics.security.anticheat.AntiCheatListener(), plugin);

        // Initialize NMS packet interception (injects ChannelDuplexHandler into Netty pipeline)
        // MUST succeed — античит работает ТОЛЬКО с NMS перехватом
        try {
            PacketHandler.init();
            if (PacketHandler.getInstance() == null) {
                throw new RuntimeException("PacketHandler.init() did not initialize instance");
            }
        } catch (Exception e) {
            throw new RuntimeException("[AntiCheat] CRITICAL: PacketHandler failed to initialize. "
                    + "AntiCheat REQUIRES Netty packet interception. Error: " + e.getMessage(), e);
        }

        ConsoleLogger.info("[AntiCheat] All checks registered and active. Packet interception: ACTIVE.");
    }

    private void registerAllChecks(AntiCheatManager acm) {
        // COMBAT checks
        for (var check : com.mcplugin.mechanics.security.anticheat.combat.CombatChecks.createAll()) {
            acm.registerCheck(check);
        }

        // MOVEMENT checks
        for (var check : com.mcplugin.mechanics.security.anticheat.movement.MovementChecks.createAll()) {
            acm.registerCheck(check);
        }

        // WORLD checks
        for (var check : com.mcplugin.mechanics.security.anticheat.world.WorldChecks.createAll()) {
            acm.registerCheck(check);
        }

        // MISC checks
        for (var check : com.mcplugin.mechanics.security.anticheat.misc.MiscChecks.createAll()) {
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
        PacketHandler.shutdown();
        AntiCheatManager.shutdown();
    }
}
