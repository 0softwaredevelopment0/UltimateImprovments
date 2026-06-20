package com.mcplugin.config;

import com.mcplugin.Main;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Проверяет целостность config.yml при старте плагина.
 * <p>
 * Выполняет две проверки:
 * <ol>
 *   <li><b>Наличие разделов</b> — если конфиг не содержит ВСЕХ ожидаемых ключей,
 *       он переименовывается в compromised-config.yml и создаётся новый.</li>
 *   <li><b>Валидация значений</b> — проверяет типы, диапазоны, пустые строки,
 *       недопустимые символы. При ошибках — предупреждение в лог (делегировано в {@link ConfigValueValidator}).</li>
 * </ol>
 * <p>
 * Правила валидации определены в {@link ConfigRules}.
 */
public class ConfigIntegrityValidator {

    private static final String COMPROMISED_SUFFIX = "compromised-config.yml";

    /** Ожидаемые корневые разделы конфига. */
    private static final String[] REQUIRED_SECTIONS = {
            "energy", "energy.generator", "energy.cable",
            "energy.cable.loss", "energy.cable.overload", "energy.cable.visual",
            "energy.cable.sound", "energy.cable.sound.smoke", "energy.cable.sound.lava",
            "energy.cable.sound.overload", "energy.battery", "energy.battery.smooth_charge",
            "energy.battery.visual", "energy.battery_drain", "energy.balancer",
            "codepanel", "codepanel.loading", "codepanel.colors", "codepanel.buttons",
            "codepanel.sounds", "codepanel.messages",
            "energy_crafting", "energy_crafting.messages",
            "reactor", "reactor.wear",
            "features", "features.antimatter", "features.attributes", "features.beacon",
            "features.blockdmg", "features.boostedcobweb", "features.deathbell",
            "features.dragonegg", "features.enderchest", "features.entitylocator",
            "features.glassbreak", "features.healthmeter", "features.itemskill",
            "features.magnet", "features.magnet.radius", "features.magnet.force",
            "features.magnet.force_curve", "features.magnet.distance_curve", "features.magnet.particles",
            "features.modeprotect", "features.shieldslowness", "features.terracotaspeed",
            "features.unbreakable_breaker", "features.unbreakable_breaker.blocks",
            "features.unbreakable_breaker.blocks.BEDROCK", "features.unbreakable_breaker.blocks.BEDROCK.damage",
            "features.unbreakable_breaker.blocks.BARRIER", "features.unbreakable_breaker.blocks.BARRIER.damage",
            "features.integrity", "features.integrity.gradient", "features.integrity.on_break",
            "features.integrity.logging", "features.integrity.anvil_repair",
            "features.integrity.anvil_repair.material_craft", "features.integrity.mending_xp",
            "features.integrity.combine", "features.integrity.xp_integrity",
            "features.integrity.low_integrity_warning", "features.integrity.unbreaking",
            "features.creative_item_validator", "features.shulker_protection",
            "features.leash", "features.container_trigger", "features.waypoint",
            "radiation", "auth", "auth.check_ip", "auth.check_duplicate_name", "auth.messages",
            "void_protection", "void_protection.target",
            "power", "power.actionbar", "power.bossbar", "power.countdown_sound",
            "power.countdown_sound.beep_speedup",
            "suicide", "suicide.sounds", "suicide.messages", "suicide.bossbar",
            "packet_guard", "redstone_guard", "emergency_entity_kill", "server_overload_warning",
            "chat_filter", "chat_filter.words", "chat_filter.regex_patterns",
            "vanish", "home", "changedimmension", "changedimmension.messages", "changedimmension.worlds"
    };

    /**
     * Проверяет конфиг. Если есть проблемы с разделами — чинит.
     * Если есть проблемы со значениями — предупреждает.
     */
    public static void validate(Main plugin) {
        FileConfiguration config = plugin.getConfig();

        // ЭТАП 1: Проверка разделов
        List<String> missing = findMissingSections(config);
        if (!missing.isEmpty()) {
            handleMissingSections(plugin, config, missing);
            config = plugin.getConfig();
        }

        // ЭТАП 2: Валидация значений (делегировано в ConfigValueValidator)
        ConfigValueValidator.validateValues(plugin, config);
    }

    private static List<String> findMissingSections(FileConfiguration config) {
        List<String> missing = new ArrayList<>();
        for (String section : REQUIRED_SECTIONS) {
            if (!config.isSet(section)) missing.add(section);
        }
        return missing;
    }

    private static void handleMissingSections(Main plugin, FileConfiguration config, List<String> missing) {
        plugin.getLogger().warning("");
        plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        plugin.getLogger().warning("!  CONFIG INTEGRITY CHECK FAILED!                       !");
        plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        plugin.getLogger().warning("!  Missing " + missing.size() + " section(s):");
        for (int i = 0; i < Math.min(missing.size(), 10); i++) {
            plugin.getLogger().warning("!    - " + missing.get(i));
        }
        if (missing.size() > 10)
            plugin.getLogger().warning("!    ... and " + (missing.size() - 10) + " more");
        plugin.getLogger().warning("!                                                       !");
        plugin.getLogger().warning("!  Renaming current config to compromised-config.yml     !");
        plugin.getLogger().warning("!  and creating a fresh config.yml from resources.       !");
        plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        plugin.getLogger().warning("");

        File configFile = new File(plugin.getDataFolder(), "config.yml");
        File compromisedFile = new File(plugin.getDataFolder(), COMPROMISED_SUFFIX);
        try {
            if (compromisedFile.exists()) compromisedFile.delete();
            if (configFile.exists()) {
                Files.move(configFile.toPath(), compromisedFile.toPath());
                plugin.getLogger().info("[ConfigValidator] Renamed config.yml \u2192 " + COMPROMISED_SUFFIX);
            }
            plugin.saveDefaultConfig();
            plugin.reloadConfig();
            plugin.getLogger().info("[ConfigValidator] \u2713 Fresh config.yml created from resources.");
        } catch (IOException e) {
            plugin.getLogger().severe("[ConfigValidator] Failed to rename config: " + e.getMessage());
        }
    }
}
