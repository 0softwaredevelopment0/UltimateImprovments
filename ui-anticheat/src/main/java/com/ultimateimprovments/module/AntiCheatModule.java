package com.ultimateimprovments.module;

import com.ultimateimprovments.config.YamlDuplicateCleaner;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.nms.PacketHandler;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;

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

        // Diagnostics: check whether config.yml has duplicate anticheat: sections
        checkForDuplicates(plugin);

        // 🔧 Automatically clean up duplicate anticheat: sections
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (configFile.exists() && YamlDuplicateCleaner.cleanDuplicates(configFile, "config.yml")) {
            plugin.reloadConfig();
            enabled = plugin.getConfig().getBoolean("anticheat.enabled", false);
            ConsoleLogger.info("[AntiCheat] Config cleaned, re-read: anticheat.enabled = " + enabled);
        }

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

    /**
     * Checks config.yml for duplicate "anticheat:" root sections.
     * The old ConfigRepairManager (before the fix) could create duplicates,
     * causing SnakeYAML to take the last occurrence, ignoring the user's edits.
     */
    private void checkForDuplicates(JavaPlugin plugin) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) return;

        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Count lines where "anticheat:" is at the start of the line (possibly indented)
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
