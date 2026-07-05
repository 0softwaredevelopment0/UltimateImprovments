package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.config.YamlDuplicateCleaner;
import com.mcplugin.infrastructure.util.ConsoleLogger;
import com.mcplugin.mechanics.security.anticheat.AntiCheatManager;
import com.mcplugin.mechanics.security.anticheat.nms.PacketHandler;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;

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
        boolean enabled = plugin.getConfig().getBoolean("anticheat.enabled", false);

        // Диагностика: проверяем, есть ли в config.yml дубликаты anticheat: секции
        checkForDuplicates(plugin);

        // 🔧 Автоматически чистим дубликаты anticheat: секций
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (configFile.exists() && YamlDuplicateCleaner.cleanDuplicates(configFile, "config.yml")) {
            plugin.reloadConfig();
            enabled = plugin.getConfig().getBoolean("anticheat.enabled", false);
            ConsoleLogger.info("[AntiCheat] Config cleaned, re-read: anticheat.enabled = " + enabled);
        }

        AntiCheatManager.init();
        AntiCheatManager acm = AntiCheatManager.getInstance();

        // Устанавливаем enabled из конфига (чтобы /mp ac toggle on мог включить)
        acm.setGlobalEnabled(enabled);

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

        if (enabled) {
            ConsoleLogger.info("[AntiCheat] All checks registered and active. Packet interception: ACTIVE.");
        } else {
            ConsoleLogger.info("[AntiCheat] Initialized but DISABLED (config). Use /mp ac toggle on to enable.");
        }
    }

    /**
     * Проверяет config.yml на дубликаты root-секции "anticheat:".
     * Старый ConfigRepairManager (до фикса) мог насоздавать дубликатов,
     * из-за чего SnakeYAML берёт последнее вхождение, игнорируя правки пользователя.
     */
    private void checkForDuplicates(JavaPlugin plugin) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) return;

        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Считаем строки, где "anticheat:" в начале строки (возможно с отступом)
                if (line.matches("^\\s*anticheat:\\s*$")) {
                    count++;
                }
            }
        } catch (IOException e) {
            // ignore
        }

        if (count > 1) {
            ConsoleLogger.warn("[AntiCheat] ⚠ Found " + count + " duplicate 'anticheat:' sections in config.yml!");
            ConsoleLogger.warn("[AntiCheat] ⚠ SnakeYAML uses the LAST one. Your edits to earlier sections are ignored.");
            ConsoleLogger.warn("[AntiCheat] ⚠ Restart the server with the latest plugin version to auto-clean duplicates.");
        }
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
