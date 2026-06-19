package com.mcplugin.config;

import com.mcplugin.Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Проверяет целостность config.yml при старте плагина.
 * <p>
 * Выполняет две проверки:
 * <ol>
 *   <li><b>Наличие разделов</b> — если конфиг не содержит ВСЕХ ожидаемых ключей,
 *       он переименовывается в compromised-config.yml и создаётся новый.</li>
 *   <li><b>Валидация значений</b> — проверяет типы, диапазоны, пустые строки,
 *       недопустимые символы. При ошибках — предупреждение в лог.</li>
 * </ol>
 * <p>
 * Это защищает от:
 * - Случайного удаления важных разделов
 * - Повреждения файла при сбое
 * - Устаревших конфигов после обновления
 * - Пустых/неверных/опасных значений в конфиге
 */
public class ConfigIntegrityValidator {

    private static final String COMPROMISED_SUFFIX = "compromised-config.yml";

    /** Ожидаемые корневые разделы конфига (максимально полный список). */
    private static final String[] REQUIRED_SECTIONS = {
            "energy",
            "energy.generator",
            "energy.cable",
            "energy.cable.loss",
            "energy.cable.overload",
            "energy.cable.visual",
            "energy.cable.sound",
            "energy.cable.sound.smoke",
            "energy.cable.sound.lava",
            "energy.cable.sound.overload",
            "energy.battery",
            "energy.battery.smooth_charge",
            "energy.battery.visual",
            "energy.battery_drain",
            "energy.balancer",
            "codepanel",
            "codepanel.loading",
            "codepanel.colors",
            "codepanel.buttons",
            "codepanel.sounds",
            "codepanel.messages",
            "energy_crafting",
            "energy_crafting.messages",
            "reactor",
            "reactor.wear",
            "features",
            "features.antimatter",
            "features.attributes",
            "features.beacon",
            "features.blockdmg",
            "features.boostedcobweb",
            "features.deathbell",
            "features.dragonegg",
            "features.enderchest",
            "features.entitylocator",
            "features.glassbreak",
            "features.healthmeter",
            "features.itemskill",
            "features.magnet",
            "features.magnet.radius",
            "features.magnet.force",
            "features.magnet.force_curve",
            "features.magnet.distance_curve",
            "features.magnet.particles",
            "features.modeprotect",
            "features.shieldslowness",
            "features.terracotaspeed",
            "features.unbreakable_breaker",
            "features.unbreakable_breaker.blocks",
            "features.unbreakable_breaker.blocks.BEDROCK",
            "features.unbreakable_breaker.blocks.BEDROCK.damage",
            "features.unbreakable_breaker.blocks.BARRIER",
            "features.unbreakable_breaker.blocks.BARRIER.damage",
            "features.integrity",
            "features.integrity.gradient",
            "features.integrity.on_break",
            "features.integrity.logging",
            "features.integrity.anvil_repair",
            "features.integrity.anvil_repair.material_craft",
            "features.integrity.mending_xp",
            "features.integrity.combine",
            "features.integrity.xp_integrity",
            "features.integrity.low_integrity_warning",
            "features.integrity.unbreaking",
            "features.creative_item_validator",
            "features.shulker_protection",
            "features.leash",
            "features.container_trigger",
            "features.waypoint",
            "radiation",
            "auth",
            "auth.check_ip",
            "auth.check_duplicate_name",
            "auth.messages",
            "void_protection",
            "void_protection.target",
            "power",
            "power.actionbar",
            "power.bossbar",
            "power.countdown_sound",
            "power.countdown_sound.beep_speedup",
            "suicide",
            "suicide.sounds",
            "suicide.messages",
            "suicide.bossbar",
            "packet_guard",
            "redstone_guard",
            "emergency_entity_kill",
            "server_overload_warning",
            "chat_filter",
            "chat_filter.words",
            "chat_filter.regex_patterns",
            "vanish",
            "home",
            "changedimmension",
            "changedimmension.messages",
            "changedimmension.worlds"
    };

    // ========================================================================
    // ВАЛИДАЦИЯ ЗНАЧЕНИЙ
    // ========================================================================

    /** Ожидаемый тип значения в конфиге. */
    private enum ValueType {
        BOOLEAN,
        INT,
        DOUBLE,
        STRING,
        STRING_LIST,
        INT_LIST,
        DOUBLE_LIST
    }

    /**
     * Правило валидации для одного конфигурационного ключа.
     */
    private static class ConfigRule {
        final String key;
        final ValueType type;
        final double min;           // для чисел: минимальное значение
        final double max;           // для чисел: максимальное значение
        final boolean notEmpty;     // для строк: не пустая
        final boolean notBlank;     // для строк: не состоит из пробелов
        final String regex;         // для строк: обязательный regex
        final int maxLength;        // для строк: макс. длина
        final String description;   // человекочитаемое описание

        ConfigRule(String key, ValueType type, double min, double max,
                   boolean notEmpty, boolean notBlank, String regex,
                   int maxLength, String description) {
            this.key = key;
            this.type = type;
            this.min = min;
            this.max = max;
            this.notEmpty = notEmpty;
            this.notBlank = notBlank;
            this.regex = regex;
            this.maxLength = maxLength;
            this.description = description;
        }
    }

    // =========================
    // ФАБРИЧНЫЕ МЕТОДЫ
    // =========================

    private static ConfigRule bool(String key) {
        return new ConfigRule(key, ValueType.BOOLEAN, 0, 0, false, false, null, 0, "");
    }

    private static ConfigRule integer(String key, int min, int max) {
        return new ConfigRule(key, ValueType.INT, min, max, false, false, null, 0, "");
    }

    private static ConfigRule integer(String key, int min, int max, String desc) {
        return new ConfigRule(key, ValueType.INT, min, max, false, false, null, 0, desc);
    }

    private static ConfigRule dbl(String key, double min, double max) {
        return new ConfigRule(key, ValueType.DOUBLE, min, max, false, false, null, 0, "");
    }

    private static ConfigRule dbl(String key, double min, double max, String desc) {
        return new ConfigRule(key, ValueType.DOUBLE, min, max, false, false, null, 0, desc);
    }

    private static ConfigRule string(String key, boolean notEmpty, int maxLength) {
        return new ConfigRule(key, ValueType.STRING, 0, 0, notEmpty, false, null, maxLength, "");
    }

    private static ConfigRule string(String key, boolean notEmpty, int maxLength, String regex) {
        return new ConfigRule(key, ValueType.STRING, 0, 0, notEmpty, false, regex, maxLength, "");
    }

    private static ConfigRule notBlank(String key, int maxLength) {
        return new ConfigRule(key, ValueType.STRING, 0, 0, true, true, null, maxLength, "");
    }

    private static ConfigRule stringList(String key) {
        return new ConfigRule(key, ValueType.STRING_LIST, 0, 0, false, false, null, 0, "");
    }

    private static ConfigRule intList(String key) {
        return new ConfigRule(key, ValueType.INT_LIST, 0, 0, false, false, null, 0, "");
    }

    // =========================
    // ПРАВИЛА ВАЛИДАЦИИ
    // =========================

    private static final List<ConfigRule> RULES = List.of(

            // ===== ENERGY / GENERATOR =====
            bool("energy.generator.enabled"),
            integer("energy.generator.energy_per_fuel", 0, Integer.MAX_VALUE,
                    "Энергия за тик горения"),
            integer("energy.generator.fuel_burn_ticks", 1, 72000,
                    "Длительность горения топлива"),
            bool("energy.generator.log"),

            // ===== ENERGY / CABLE =====
            bool("energy.cable.enabled"),
            integer("energy.cable.loss_per_tick", 0, Integer.MAX_VALUE,
                    "Потери энергии в тик"),
            integer("energy.cable.max_energy", 1, Integer.MAX_VALUE,
                    "Максимальная энергия кабеля"),

            // ===== CABLE / LOSS =====
            integer("energy.cable.loss.lightning_rod", 0, Integer.MAX_VALUE),
            integer("energy.cable.loss.chiseled_copper", 0, Integer.MAX_VALUE),
            integer("energy.cable.loss.copper_grate", 0, Integer.MAX_VALUE),

            // ===== CABLE / OVERLOAD =====
            bool("energy.cable.overload.enabled"),
            bool("energy.cable.overload.break_blocks"),
            bool("energy.cable.overload.set_fire"),
            dbl("energy.cable.overload.explosion_power", 0, 100,
                    "Сила взрыва (макс 100)"),

            // ===== CABLE / VISUAL =====
            integer("energy.cable.visual.smoke_threshold", 0, Integer.MAX_VALUE),
            integer("energy.cable.visual.lava_threshold", 0, Integer.MAX_VALUE),

            // ===== CABLE / SOUND =====
            bool("energy.cable.sound.enabled"),
            integer("energy.cable.sound.smoke_interval", 1, 1200),
            integer("energy.cable.sound.lava_interval", 1, 1200),
            integer("energy.cable.sound.overload_interval", 1, 1200),

            // ===== CABLE / SOUND / SMOKE =====
            dbl("energy.cable.sound.smoke.volume", 0, 2, "Громкость звука (0-2)"),
            dbl("energy.cable.sound.smoke.pitch_min", 0, 2),
            dbl("energy.cable.sound.smoke.pitch_max", 0, 2),

            // ===== CABLE / SOUND / LAVA =====
            dbl("energy.cable.sound.lava.volume", 0, 2),
            dbl("energy.cable.sound.lava.pitch_min", 0, 2),
            dbl("energy.cable.sound.lava.pitch_max", 0, 2),

            // ===== CABLE / SOUND / OVERLOAD =====
            dbl("energy.cable.sound.overload.volume", 0, 2),
            dbl("energy.cable.sound.overload.pitch", 0, 2),

            // ===== ENERGY / BATTERY =====
            integer("energy.battery.max_energy", 1, Integer.MAX_VALUE,
                    "Макс. энергия батареи"),
            integer("energy.battery.discharge_per_tick", 0, Integer.MAX_VALUE),

            // ===== BATTERY / SMOOTH CHARGE =====
            bool("energy.battery.smooth_charge.enabled"),
            dbl("energy.battery.smooth_charge.charge_multiplier", 0, 1000),
            dbl("energy.battery.smooth_charge.discharge_multiplier", 0, 1000),

            // ===== BATTERY / VISUAL =====
            bool("energy.battery.visual.particles_enabled"),

            // ===== BATTERY DRAIN =====
            bool("energy.battery_drain.enabled"),
            integer("energy.battery_drain.transfer_per_tick", 0, Integer.MAX_VALUE),

            // ===== BALANCER =====
            bool("energy.balancer.enabled"),
            bool("energy.balancer.include_batteries"),
            integer("energy.balancer.max_transfer", 0, Integer.MAX_VALUE),

            // ===== CODEPANEL =====
            integer("codepanel.max_length", 1, 256,
                    "Макс. длина пароля кодовой панели"),
            integer("codepanel.keyboard_offset", 0, 10),
            integer("codepanel.enter_cooldown", 0, 3600),

            // ===== CODEPANEL / LOADING =====
            integer("codepanel.loading.step_ticks", 1, 200),
            integer("codepanel.loading.step", 1, 1000),
            integer("codepanel.loading.delay_after", 0, 2000),

            // ===== ENERGY CRAFTING =====
            bool("energy_crafting.enabled"),
            integer("energy_crafting.energy_per_craft", 0, Integer.MAX_VALUE),
            dbl("energy_crafting.workbench_search_radius", 0, 100),

            // ===== REACTOR =====
            bool("reactor.enabled"),
            integer("reactor.temp_decay_rate", 0, 1000),
            integer("reactor.core_temp_max", 1, 100000),
            integer("reactor.core_temp_min", -10000, 0),
            integer("reactor.core_temp_cool_min", -10000, 0),
            integer("reactor.heat_rate", 0, 1000),
            integer("reactor.cool_rate", 0, 1000),
            integer("reactor.core_press_reduce_rate", 0, 1000),
            integer("reactor.case_temp_heat_rate", 0, 1000),
            integer("reactor.case_temp_max", 1, 100000),
            integer("reactor.case_temp_cool_rate", 0, 1000),
            integer("reactor.case_temp_cool_min", -10000, 0),
            integer("reactor.case_temp_decay_rate", 0, 1000),
            integer("reactor.case_press_heat_rate", 0, 1000),
            integer("reactor.case_press_max", 1, 100000),
            integer("reactor.case_press_decay_rate", 0, 1000),
            integer("reactor.shell_integrity_decay_temp", 0, 100000),
            integer("reactor.shell_int_decay_rate", 0, 1000),
            integer("reactor.shell_int_recovery_temp_max", 0, 100000),
            integer("reactor.shell_int_recovery_rate", 0, 1000),
            integer("reactor.case_integrity_decay_press", 0, 100000),
            integer("reactor.case_integrity_decay_temp", 0, 100000),
            integer("reactor.case_int_decay_press_rate", 0, 1000),
            integer("reactor.case_int_decay_temp_rate", 0, 1000),
            integer("reactor.case_int_recovery_press_max", 0, 100000),
            integer("reactor.case_int_recovery_temp_max", 0, 100000),
            integer("reactor.case_int_recovery_rate", 0, 1000),
            integer("reactor.meltdown_explosion_radius", 0, 500,
                    "Радиус взрыва реактора"),
            integer("reactor.recipe_time_max", 1, 100000),

            // ===== REACTOR / WEAR =====
            bool("reactor.wear.enabled"),
            integer("reactor.wear.interval_normal", 1, 72000),
            integer("reactor.wear.interval_degradation", 1, 72000),
            integer("reactor.wear.chat_countdown", 1, 3600),
            integer("reactor.wear.final_meltdown_start_at", 0, 3600),
            integer("reactor.wear.final_meltdown_duration", 1, 3600),

            // ===== FEATURES =====
            bool("features.antimatter.enabled"),
            integer("features.antimatter.explosion_radius", 0, 100),
            bool("features.antimatter.break_blocks"),
            bool("features.antimatter.set_fire"),

            bool("features.attributes.enabled"),
            dbl("features.attributes.attack_damage", 0, 10000),
            integer("features.attributes.interval_ticks", 1, 72000),

            bool("features.beacon.enabled"),
            integer("features.beacon.regeneration_amplifier", 0, 255),
            integer("features.beacon.resistance_amplifier", 0, 255),
            integer("features.beacon.interval_ticks", 1, 72000),

            bool("features.blockdmg.enabled"),
            integer("features.blockdmg.dripstone_damage", 0, Integer.MAX_VALUE),
            integer("features.blockdmg.endrod_damage", 0, Integer.MAX_VALUE),
            integer("features.blockdmg.interval_ticks", 1, 72000),

            bool("features.boostedcobweb.enabled"),
            integer("features.boostedcobweb.fatigue_amplifier", 0, 255),
            integer("features.boostedcobweb.weakness_amplifier", 0, 255),
            integer("features.boostedcobweb.slowness_amplifier", 0, 255),
            integer("features.boostedcobweb.slowfall_amplifier", 0, 255),
            integer("features.boostedcobweb.interval_ticks", 1, 72000),

            bool("features.deathbell.enabled"),
            bool("features.deathbell.lightning"),

            // ===== DRAGON EGG =====
            bool("features.dragonegg.enabled"),
            integer("features.dragonegg.interval_ticks", 1, Integer.MAX_VALUE),
            dbl("features.dragonegg.spawn_chance", 0, 1,
                    "Шанс спавна (0.0 — 1.0)"),

            // ===== ENDER CHEST =====
            bool("features.enderchest.enabled"),
            dbl("features.enderchest.explosion_chance", 0, 1,
                    "Шанс взрыва (0.0 — 1.0)"),
            integer("features.enderchest.explosion_radius", 0, 100),
            integer("features.enderchest.damage", 0, Integer.MAX_VALUE),

            // ===== ENTITY LOCATOR =====
            bool("features.entitylocator.enabled"),
            integer("features.entitylocator.scan_radius", 1, 1000),
            integer("features.entitylocator.interval_ticks", 1, 72000),

            // ===== GLASS BREAK =====
            bool("features.glassbreak.enabled"),
            integer("features.glassbreak.damage", 0, Integer.MAX_VALUE),

            // ===== HEALTH METER =====
            bool("features.healthmeter.enabled"),
            integer("features.healthmeter.scan_radius", 1, 100),
            integer("features.healthmeter.interval_ticks", 1, 72000),

            // ===== ITEM SKILL (антилаг) =====
            bool("features.itemskill.enabled"),
            integer("features.itemskill.item_limit", 1, Integer.MAX_VALUE),
            integer("features.itemskill.interval_ticks", 1, 72000),
            bool("features.itemskill.warn_operators"),

            // ===== MAGNET =====
            bool("features.magnet.enabled"),
            integer("features.magnet.radius.min", 1, 1000),
            integer("features.magnet.radius.max", 1, 1000),
            integer("features.magnet.interval_ticks", 1, 100),
            dbl("features.magnet.force.base", 0, 100),
            dbl("features.magnet.force.distance_multiplier", 0, 100),
            dbl("features.magnet.force.max", 0, 100),
            dbl("features.magnet.force.max_speed", 0, 100),
            dbl("features.magnet.item_y_boost", 0, 100),
            dbl("features.magnet.force_curve.power_exponent", 0, 10),
            dbl("features.magnet.force_curve.power_normalize", 1, 10000),
            integer("features.magnet.particles.center_max", 0, 10000),
            integer("features.magnet.particles.blocks_max", 0, 10000),
            integer("features.magnet.particles.crit_max", 0, 10000),
            integer("features.magnet.particles.portal_max", 0, 10000),

            // ===== MODE PROTECT =====
            bool("features.modeprotect.enabled"),

            // ===== SHIELD SLOWNESS =====
            bool("features.shieldslowness.enabled"),
            integer("features.shieldslowness.slowness_amplifier", 0, 255),
            integer("features.shieldslowness.slowness_duration", 0, 72000),

            // ===== TERRACOTA SPEED =====
            bool("features.terracotaspeed.enabled"),
            integer("features.terracotaspeed.speed_amplifier", 0, 255),
            integer("features.terracotaspeed.interval_ticks", 1, 72000),

            // ===== UNBREAKABLE BREAKER =====
            bool("features.unbreakable_breaker.enabled"),
            integer("features.unbreakable_breaker.hit_interval_ticks", 1, 200),

            // ===== INTEGRITY =====
            bool("features.integrity.enabled"),
            integer("features.integrity.interval_ticks", 1, 72000),
            dbl("features.integrity.cost_multiplier", 0, 1000),
            bool("features.integrity.on_break.play_sound"),
            bool("features.integrity.on_break.send_message"),
            dbl("features.integrity.on_break.sound_volume", 0, 2),
            dbl("features.integrity.on_break.sound_pitch", 0.5, 2),

            // ===== INTEGRITY / LOGGING =====
            bool("features.integrity.logging.log_init"),
            bool("features.integrity.logging.log_break"),
            bool("features.integrity.logging.log_errors"),

            // ===== INTEGRITY / ANVIL REPAIR =====
            bool("features.integrity.anvil_repair.enabled"),
            dbl("features.integrity.anvil_repair.integrity_multiplier", 0, 100),
            bool("features.integrity.anvil_repair.combine_enabled"),
            dbl("features.integrity.anvil_repair.combine_bonus", 0, 100),

            // ===== INTEGRITY / ANVIL REPAIR / MATERIAL CRAFT =====
            bool("features.integrity.anvil_repair.material_craft.enabled"),
            dbl("features.integrity.anvil_repair.material_craft.integrity_per_material", 0, 1000),

            // ===== INTEGRITY / MENDING XP =====
            bool("features.integrity.mending_xp.enabled"),
            dbl("features.integrity.mending_xp.integrity_multiplier", 0, 1000),

            // ===== INTEGRITY / COMBINE =====
            bool("features.integrity.combine.enabled"),
            dbl("features.integrity.combine.loss_rate", 0, 100),

            // ===== INTEGRITY / XP INTEGRITY =====
            bool("features.integrity.xp_integrity.enabled"),
            dbl("features.integrity.xp_integrity.integrity_per_xp", 0, 1000),

            // ===== INTEGRITY / LOW INTEGRITY WARNING =====
            bool("features.integrity.low_integrity_warning.enabled"),
            intList("features.integrity.low_integrity_warning.thresholds"),

            // ===== INTEGRITY / UNBREAKING =====
            bool("features.integrity.unbreaking.enabled"),

            // ===== CREATIVE ITEM VALIDATOR =====
            bool("features.creative_item_validator.enabled"),
            integer("features.creative_item_validator.max_item_bytes", 1, Integer.MAX_VALUE),
            integer("features.creative_item_validator.max_pdc_keys", 0, 10000),
            integer("features.creative_item_validator.max_lore_lines", 0, 10000),
            integer("features.creative_item_validator.max_lore_chars", 0, 100000),
            integer("features.creative_item_validator.max_name_chars", 0, 10000),
            integer("features.creative_item_validator.max_enchantments", 0, 10000),

            // ===== SHULKER PROTECTION =====
            bool("features.shulker_protection.enabled"),

            // ===== LEASH =====
            bool("features.leash.enabled"),
            integer("features.leash.max_distance", 1, 1000, "Макс. дистанция поводка"),
            integer("features.leash.pull_back_interval", 1, 200, "Интервал подтягивания (тики)"),
            bool("features.leash.prevent_break"),
            bool("features.leash.hard_stop"),

            // ===== CONTAINER TRIGGER =====
            bool("features.container_trigger.enabled"),
            integer("features.container_trigger.interval_ticks", 1, 200,
                    "Интервал проверки контейнера (тики)"),

            // ===== WAYPOINT =====
            bool("features.waypoint.enabled"),
            integer("features.waypoint.interval_ticks", 1, 72000),

            // ===== RADIATION =====
            bool("radiation.enabled"),
            integer("radiation.natural_decay", 0, Integer.MAX_VALUE),
            bool("radiation.effects_enabled"),
            bool("radiation.dosimeter_enabled"),
            integer("radiation.ancient_debris_radiation", 0, Integer.MAX_VALUE),
            integer("radiation.basalt_deltas_radiation", 0, Integer.MAX_VALUE),
            integer("radiation.end_radiation", 0, Integer.MAX_VALUE),
            integer("radiation.lead_shield_reduction", 0, Integer.MAX_VALUE),
            integer("radiation.antirad_reduction", 0, Integer.MAX_VALUE),
            integer("radiation.kill_reduction", 0, Integer.MAX_VALUE),
            bool("radiation.death_reset"),
            integer("radiation.mace_use_radiation", 0, Integer.MAX_VALUE),
            integer("radiation.trident_use_radiation", 0, Integer.MAX_VALUE),
            integer("radiation.elytra_use_radiation", 0, Integer.MAX_VALUE),
            integer("radiation.reactor_core_radiation", 0, Integer.MAX_VALUE),
            integer("radiation.reactor_pressure_radiation", 0, Integer.MAX_VALUE),
            integer("radiation.reactor_meltdown_close", 0, Integer.MAX_VALUE),
            integer("radiation.reactor_meltdown_far", 0, Integer.MAX_VALUE),

            // ===== AUTH =====
            bool("auth.enabled"),
            integer("auth.min_password_length", 8, 32,
                    "Мин. длина пароля"),
            integer("auth.max_password_length", 1, 32,
                    "Макс. длина пароля"),
            integer("auth.session_duration_minutes", 0, Integer.MAX_VALUE),
            bool("auth.check_ip.enabled"),
            integer("auth.request_cooldown_seconds", 0, 3600),
            integer("auth.max_accounts_per_ip", 0, 1000),
            bool("auth.check_duplicate_name.enabled"),

            // ===== VOID PROTECTION =====
            bool("void_protection.enabled"),

            // ===== POWER =====
            bool("power.intercept_commands"),
            integer("power.request_timeout", 1, 3600),
            integer("power.countdown_duration", 0, 3600),
            bool("power.actionbar.enabled"),
            bool("power.bossbar.enabled"),
            bool("power.countdown_sound.enabled"),
            dbl("power.countdown_sound.volume", 0, 2),
            dbl("power.countdown_sound.pitch_base", 0, 2),
            dbl("power.countdown_sound.pitch_max", 0, 2),
            bool("power.countdown_sound.beep_speedup.enabled"),
            integer("power.countdown_sound.beep_speedup.min_per_second", 0, 100),
            integer("power.countdown_sound.beep_speedup.max_per_second", 0, 100),

            // ===== SUICIDE =====
            integer("suicide.countdown_duration", 1, 3600),
            integer("suicide.cooldown_seconds", 0, 36000),
            integer("suicide.confirm_timeout", 1, 3600),

            // ===== PACKET GUARD =====
            bool("packet_guard.enabled"),
            integer("packet_guard.max_packet_size", 1, Integer.MAX_VALUE,
                    "Макс. размер пакета"),
            bool("packet_guard.log"),
            bool("packet_guard.log_inject"),

            // ===== REDSTONE GUARD =====
            bool("redstone_guard.enabled"),
            dbl("redstone_guard.mspt_threshold", 0, 10000),
            integer("redstone_guard.global_iterations_limit", 0, Integer.MAX_VALUE),
            integer("redstone_guard.chunk_iterations_limit", 0, Integer.MAX_VALUE),
            integer("redstone_guard.block_duration_seconds", 1, 3600),
            integer("redstone_guard.chunks_per_tick", 0, 1000),

            // ===== EMERGENCY ENTITY KILL =====
            bool("emergency_entity_kill.enabled"),
            integer("emergency_entity_kill.entity_limit", 1, Integer.MAX_VALUE),
            dbl("emergency_entity_kill.instant_kill_mspt", 0, 10000),
            dbl("emergency_entity_kill.overload_mspt_threshold", 0, 10000),
            integer("emergency_entity_kill.max_overload_accumulation", 1, Integer.MAX_VALUE),
            integer("emergency_entity_kill.overload_increment_per_tick", 1, Integer.MAX_VALUE),
            bool("emergency_entity_kill.remove_plasma_on_overload"),
            dbl("emergency_entity_kill.plasma_removal_mspt", 0, 10000),
            bool("emergency_entity_kill.log"),

            // ===== SERVER OVERLOAD WARNING =====
            bool("server_overload_warning.enabled"),
            dbl("server_overload_warning.high_mspt", 0, 10000),
            dbl("server_overload_warning.critical_mspt", 0, 10000),
            integer("server_overload_warning.notify_cooldown_seconds", 0, 36000),

            // ===== CHAT FILTER =====
            bool("chat_filter.enabled"),
            stringList("chat_filter.words"),
            stringList("chat_filter.regex_patterns"),

            // ===== HOME =====
            integer("home.max_homes", 1, 1000),
            integer("home.name_min_length", 1, 100),
            integer("home.name_max_length", 1, 100),

            // ===== CHANGEDIMMENSION =====
            integer("changedimmension.cooldown_seconds", 0, 36000),

            // ===== NOT-BLANK STRINGS (critical paths) =====
            notBlank("energy.cable.sound.smoke.sound", 256),
            notBlank("energy.cable.sound.lava.sound", 256),
            notBlank("energy.cable.sound.overload.sound", 256),
            notBlank("codepanel.empty_char", 10),
            notBlank("void_protection.target.world", 256),
            notBlank("changedimmension.default_world", 256)
    );

    // ========================================================================
    // ПУБЛИЧНЫЙ МЕТОД ВАЛИДАЦИИ
    // ========================================================================

    /**
     * Проверяет конфиг. Если есть проблемы с разделами — чинит.
     * Если есть проблемы со значениями — предупреждает.
     * Должен вызываться ДО инициализации любых модулей.
     */
    public static void validate(Main plugin) {
        FileConfiguration config = plugin.getConfig();

        // =========================
        // ЭТАП 1: ПРОВЕРКА РАЗДЕЛОВ
        // =========================
        List<String> missing = findMissingSections(config);

        if (!missing.isEmpty()) {
            handleMissingSections(plugin, config, missing);
            // После замены конфига — перезагружаем и заново проверяем
            config = plugin.getConfig();
        }

        // =========================
        // ЭТАП 2: ВАЛИДАЦИЯ ЗНАЧЕНИЙ
        // =========================
        validateValues(plugin, config);
    }

    // ========================================================================
    // ПРОВЕРКА РАЗДЕЛОВ
    // ========================================================================

    /** Находит отсутствующие разделы в конфиге. */
    private static List<String> findMissingSections(FileConfiguration config) {
        List<String> missing = new ArrayList<>();
        for (String section : REQUIRED_SECTIONS) {
            if (!config.isSet(section)) {
                missing.add(section);
            }
        }
        return missing;
    }

    /** Обрабатывает отсутствующие разделы — переименовывает конфиг. */
    private static void handleMissingSections(Main plugin, FileConfiguration config, List<String> missing) {
        plugin.getLogger().warning("");
        plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        plugin.getLogger().warning("!  CONFIG INTEGRITY CHECK FAILED!                       !");
        plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        plugin.getLogger().warning("!  Missing " + missing.size() + " section(s):");
        for (int i = 0; i < Math.min(missing.size(), 10); i++) {
            plugin.getLogger().warning("!    - " + missing.get(i));
        }
        if (missing.size() > 10) {
            plugin.getLogger().warning("!    ... and " + (missing.size() - 10) + " more");
        }
        plugin.getLogger().warning("!                                                       !");
        plugin.getLogger().warning("!  Renaming current config to compromised-config.yml     !");
        plugin.getLogger().warning("!  and creating a fresh config.yml from resources.       !");
        plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        plugin.getLogger().warning("");

        File configFile = new File(plugin.getDataFolder(), "config.yml");
        File compromisedFile = new File(plugin.getDataFolder(), COMPROMISED_SUFFIX);

        try {
            if (compromisedFile.exists()) {
                compromisedFile.delete();
            }
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

    // ========================================================================
    // ВАЛИДАЦИЯ ЗНАЧЕНИЙ
    // ========================================================================

    /**
     * Проверяет все значения конфига по правилам.
     * Логирует предупреждения для некорректных значений.
     */
    private static void validateValues(Main plugin, FileConfiguration config) {
        int errors = 0;
        int warnings = 0;

        for (ConfigRule rule : RULES) {
            // Пропускаем если секция не задана (уже отработано в проверке разделов)
            if (!config.isSet(rule.key)) continue;

            ValidationResult result = checkValue(config, rule);
            switch (result.severity) {
                case ERROR -> {
                    errors++;
                    plugin.getLogger().warning("[ConfigValidator] \u26A0 [ERROR] " + rule.key + ": " + result.message);
                }
                case WARN -> {
                    warnings++;
                    plugin.getLogger().warning("[ConfigValidator] \u26A0 [WARN] " + rule.key + ": " + result.message);
                }
            }
        }

        // Итоговое сообщение
        if (errors > 0 || warnings > 0) {
            plugin.getLogger().warning("");
            plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            plugin.getLogger().warning("!  CONFIG VALUE VALIDATION COMPLETE                     !");
            plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            if (errors > 0) {
                plugin.getLogger().warning("!  " + errors + " error(s) found - fix these values!              !");
            }
            if (warnings > 0) {
                plugin.getLogger().warning("!  " + warnings + " warning(s) found - review recommended.         !");
            }
            plugin.getLogger().warning("!  Plugin will use defaults for invalid values.          !");
            plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            plugin.getLogger().warning("");
        } else {
            plugin.getLogger().info("[ConfigValidator] \u2713 All config values passed validation.");
        }
    }

    /** Результат проверки одного значения. */
    private enum Severity { OK, WARN, ERROR }

    private static class ValidationResult {
        static final ValidationResult OK = new ValidationResult(Severity.OK, "");

        final Severity severity;
        final String message;

        ValidationResult(Severity severity, String message) {
            this.severity = severity;
            this.message = message;
        }

        static ValidationResult error(String msg) { return new ValidationResult(Severity.ERROR, msg); }
        static ValidationResult warn(String msg) { return new ValidationResult(Severity.WARN, msg); }
    }

    /** Проверяет одно значение по правилу. */
    private static ValidationResult checkValue(FileConfiguration config, ConfigRule rule) {
        Object value = config.get(rule.key);

        // =========================
        // 1. ПРОВЕРКА НА NULL
        // =========================
        if (value == null) {
            // isSet уже проверил, но на всякий случай
            return ValidationResult.OK;
        }

        // =========================
        // 2. ПРОВЕРКА ТИПА + ЗНАЧЕНИЯ
        // =========================
        return switch (rule.type) {
            case BOOLEAN -> checkBoolean(rule.key, value);
            case INT -> checkInt(rule.key, value, rule);
            case DOUBLE -> checkDouble(rule.key, value, rule);
            case STRING -> checkString(rule.key, value, rule);
            case STRING_LIST -> checkStringList(rule.key, value, rule);
            case INT_LIST -> checkIntList(rule.key, value, rule);
            case DOUBLE_LIST -> checkDoubleList(rule.key, value, rule);
        };
    }

    // =========================
    // ТИП: BOOLEAN
    // =========================

    private static ValidationResult checkBoolean(String key, Object value) {
        if (value instanceof Boolean) return ValidationResult.OK;
        return ValidationResult.error("Ожидался boolean (true/false), получен " + typeName(value) + ": " + value);
    }

    // =========================
    // ТИП: INT
    // =========================

    private static ValidationResult checkInt(String key, Object value, ConfigRule rule) {
        if (!(value instanceof Number num)) {
            return ValidationResult.error("Ожидалось целое число, получен " + typeName(value) + ": " + value);
        }

        long longVal = num.longValue();

        // Проверка на дробную часть (если double c дробной частью)
        if (value instanceof Double || value instanceof Float) {
            double dVal = num.doubleValue();
            if (dVal != Math.floor(dVal)) {
                return ValidationResult.warn("Значение с плавающей точкой, будет округлено до " + (long) dVal);
            }
        }

        // Проверка переполнения int
        if (longVal < Integer.MIN_VALUE || longVal > Integer.MAX_VALUE) {
            return ValidationResult.error("Значение вне диапазона int: " + longVal);
        }

        int intVal = (int) longVal;

        // Проверка диапазона
        StringBuilder boundsMsg = new StringBuilder();
        if (intVal < rule.min) {
            boundsMsg.append("минимальное ").append(formatNum(rule.min));
        }
        if (intVal > rule.max) {
            if (!boundsMsg.isEmpty()) boundsMsg.append(", ");
            boundsMsg.append("максимальное ").append(formatNum(rule.max));
        }

        if (!boundsMsg.isEmpty()) {
            String desc = rule.description.isEmpty() ? "" : " (" + rule.description + ")";
            return ValidationResult.error("Значение " + intVal + " вне допустимого диапазона [" + formatNum(rule.min) + ".." + formatNum(rule.max) + "]" + desc + " — " + boundsMsg);
        }

        return ValidationResult.OK;
    }

    // =========================
    // ТИП: DOUBLE
    // =========================

    private static ValidationResult checkDouble(String key, Object value, ConfigRule rule) {
        if (!(value instanceof Number num)) {
            return ValidationResult.error("Ожидалось число с плавающей точкой, получен " + typeName(value) + ": " + value);
        }

        double dVal = num.doubleValue();

        // Проверка на бесконечность/NaN
        if (Double.isInfinite(dVal)) {
            return ValidationResult.error("Значение равно бесконечности (Infinity)");
        }
        if (Double.isNaN(dVal)) {
            return ValidationResult.error("Значение не является числом (NaN)");
        }

        // Проверка диапазона
        StringBuilder boundsMsg = new StringBuilder();
        if (dVal < rule.min) {
            boundsMsg.append("минимальное ").append(formatNum(rule.min));
        }
        if (dVal > rule.max) {
            if (!boundsMsg.isEmpty()) boundsMsg.append(", ");
            boundsMsg.append("максимальное ").append(formatNum(rule.max));
        }

        if (!boundsMsg.isEmpty()) {
            String desc = rule.description.isEmpty() ? "" : " (" + rule.description + ")";
            return ValidationResult.error("Значение " + dVal + " вне допустимого диапазона [" + formatNum(rule.min) + ".." + formatNum(rule.max) + "]" + desc);
        }

        return ValidationResult.OK;
    }

    // =========================
    // ТИП: STRING
    // =========================

    private static ValidationResult checkString(String key, Object value, ConfigRule rule) {
        if (!(value instanceof String str)) {
            return ValidationResult.error("Ожидалась строка, получен " + typeName(value) + ": " + value);
        }

        // Проверка на пустую строку
        if (rule.notEmpty && str.isEmpty()) {
            return ValidationResult.error("Строка пуста");
        }

        // Проверка на blank (только пробелы)
        if (rule.notBlank && str.trim().isEmpty()) {
            return ValidationResult.error("Строка состоит только из пробелов");
        }

        // Проверка на недопустимые символы (control chars: \u0000-\u001F, кроме \n \r \t)
        StringBuilder badChars = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c < 32 && c != '\n' && c != '\r' && c != '\t') {
                if (!badChars.isEmpty()) badChars.append(", ");
                badChars.append("U+").append(String.format("%04X", (int) c));
            }
        }
        if (!badChars.isEmpty()) {
            return ValidationResult.warn("Строка содержит недопустимые управляющие символы: " + badChars);
        }

        // Проверка максимальной длины
        if (rule.maxLength > 0 && str.length() > rule.maxLength) {
            return ValidationResult.error("Длина строки (" + str.length() + ") превышает максимум (" + rule.maxLength + ")");
        }

        // Проверка regex
        if (rule.regex != null && !rule.regex.isEmpty()) {
            try {
                if (!str.matches(rule.regex)) {
                    return ValidationResult.error("Строка не соответствует формату: " + rule.regex);
                }
            } catch (PatternSyntaxException e) {
                // Если regex кривой — пропускаем (проблемы в конфиге, а не в коде)
            }
        }

        return ValidationResult.OK;
    }

    // =========================
    // ТИП: STRING_LIST
    // =========================

    @SuppressWarnings("unchecked")
    private static ValidationResult checkStringList(String key, Object value, ConfigRule rule) {
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (!(item instanceof String)) {
                    return ValidationResult.warn("Элемент списка #" + i + " — ожидалась строка, получен " + typeName(item));
                }
                String str = (String) item;

                // Проверка контрольных символов
                StringBuilder badChars = new StringBuilder();
                for (int j = 0; j < str.length(); j++) {
                    char c = str.charAt(j);
                    if (c < 32 && c != '\n' && c != '\r' && c != '\t') {
                        if (!badChars.isEmpty()) badChars.append(", ");
                        badChars.append("U+").append(String.format("%04X", (int) c));
                    }
                }
                if (!badChars.isEmpty()) {
                    return ValidationResult.warn("Элемент списка #" + i + " содержит управляющие символы: " + badChars);
                }

                // Если ключ — regex_patterns, проверяем валидность regex
                if (key.endsWith("regex_patterns")) {
                    try {
                        Pattern.compile(str);
                    } catch (PatternSyntaxException e) {
                        return ValidationResult.error("Элемент списка #" + i + " — невалидный regex: " + e.getMessage());
                    }
                }
            }
            return ValidationResult.OK;
        }
        return ValidationResult.warn("Ожидался список строк, получен " + typeName(value));
    }

    // =========================
    // ТИП: INT_LIST
    // =========================

    @SuppressWarnings("unchecked")
    private static ValidationResult checkIntList(String key, Object value, ConfigRule rule) {
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (!(item instanceof Number)) {
                    return ValidationResult.warn("Элемент списка #" + i + " — ожидалось целое число, получен " + typeName(item));
                }
            }
            return ValidationResult.OK;
        }
        return ValidationResult.warn("Ожидался список целых чисел, получен " + typeName(value));
    }

    // =========================
    // ТИП: DOUBLE_LIST
    // =========================

    @SuppressWarnings("unchecked")
    private static ValidationResult checkDoubleList(String key, Object value, ConfigRule rule) {
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (!(item instanceof Number)) {
                    return ValidationResult.warn("Элемент списка #" + i + " — ожидалось число, получен " + typeName(item));
                }
            }
            return ValidationResult.OK;
        }
        return ValidationResult.warn("Ожидался список чисел, получен " + typeName(value));
    }

    // =========================
    // УТИЛИТЫ
    // =========================

    /** Возвращает человекочитаемое имя типа для объекта. */
    private static String typeName(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof Integer) return "int";
        if (value instanceof Long) return "long";
        if (value instanceof Double) return "double";
        if (value instanceof Float) return "float";
        if (value instanceof String) return "string";
        if (value instanceof List) return "list";
        if (value instanceof ConfigurationSection) return "section";
        return value.getClass().getSimpleName();
    }

    /** Форматирует число, убирая .0 у целых. */
    private static String formatNum(double d) {
        if (d == (long) d) return String.valueOf((long) d);
        return String.valueOf(d);
    }
}
