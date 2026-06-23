package com.mcplugin.infrastructure.config;

import java.util.List;

/**
 * Все правила валидации для config.yml.
 * <p>
 * Извлечено из {@link ConfigIntegrityValidator} для уменьшения размера класса.
 */
final class ConfigRules {

    private ConfigRules() {}

    enum ValueType { BOOLEAN, INT, DOUBLE, STRING, STRING_LIST, INT_LIST, DOUBLE_LIST }

    static class Rule {
        final String key;
        final ValueType type;
        final double min;
        final double max;
        final boolean notEmpty;
        final boolean notBlank;
        final String regex;
        final int maxLength;
        final String description;

        Rule(String key, ValueType type, double min, double max,
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
    static Rule bool(String key) {
        return new Rule(key, ValueType.BOOLEAN, 0, 0, false, false, null, 0, "");
    }

    static Rule integer(String key, int min, int max) {
        return new Rule(key, ValueType.INT, min, max, false, false, null, 0, "");
    }

    static Rule integer(String key, int min, int max, String desc) {
        return new Rule(key, ValueType.INT, min, max, false, false, null, 0, desc);
    }

    static Rule dbl(String key, double min, double max) {
        return new Rule(key, ValueType.DOUBLE, min, max, false, false, null, 0, "");
    }

    static Rule dbl(String key, double min, double max, String desc) {
        return new Rule(key, ValueType.DOUBLE, min, max, false, false, null, 0, desc);
    }

    static Rule string(String key, boolean notEmpty, int maxLength) {
        return new Rule(key, ValueType.STRING, 0, 0, notEmpty, false, null, maxLength, "");
    }

    static Rule notBlank(String key, int maxLength) {
        return new Rule(key, ValueType.STRING, 0, 0, true, true, null, maxLength, "");
    }

    static Rule stringList(String key) {
        return new Rule(key, ValueType.STRING_LIST, 0, 0, false, false, null, 0, "");
    }

    static Rule intList(String key) {
        return new Rule(key, ValueType.INT_LIST, 0, 0, false, false, null, 0, "");
    }

    // =========================
    // ПРАВИЛА ВАЛИДАЦИИ
    // =========================
    static final List<Rule> ALL = List.of(
            bool("energy.generator.enabled"),
            integer("energy.generator.energy_per_fuel", 0, Integer.MAX_VALUE, "Энергия за тик горения"),
            integer("energy.generator.fuel_burn_ticks", 1, 72000, "Длительность горения топлива"),
            bool("energy.generator.log"),

            bool("energy.cable.enabled"),
            integer("energy.cable.max_energy", 0, Integer.MAX_VALUE, "Максимальная энергия кабеля (0 = не хранит)"),

            bool("energy.cable.blink.enabled"),
            integer("energy.cable.blink.off_ticks", 1, 200),
            integer("energy.cable.blink.on_ticks", 1, 200),

            integer("energy.battery.max_energy", 1, Integer.MAX_VALUE, "Макс. энергия батареи"),
            integer("energy.battery.discharge_per_tick", 0, Integer.MAX_VALUE),

            bool("energy.battery.smooth_charge.enabled"),
            dbl("energy.battery.smooth_charge.charge_multiplier", 0, 1000),
            dbl("energy.battery.smooth_charge.discharge_multiplier", 0, 1000),

            bool("energy.battery.visual.particles_enabled"),

            bool("energy.battery_drain.enabled"),
            integer("energy.battery_drain.transfer_per_tick", 0, Integer.MAX_VALUE),

            bool("energy.balancer.enabled"),
            integer("energy.balancer.max_transfer", 0, Integer.MAX_VALUE),

            integer("codepanel.max_length", 1, 256, "Макс. длина пароля кодовой панели"),
            integer("codepanel.keyboard_offset", 0, 10),
            integer("codepanel.enter_cooldown", 0, 3600),
            integer("codepanel.loading.step_ticks", 1, 200),
            integer("codepanel.loading.step", 1, 1000),
            integer("codepanel.loading.delay_after", 0, 2000),

            bool("energy_crafting.enabled"),
            integer("energy_crafting.energy_per_craft", 0, Integer.MAX_VALUE),
            dbl("energy_crafting.workbench_search_radius", 0, 100),

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
            integer("reactor.meltdown_explosion_radius", 0, 500, "Радиус взрыва реактора"),
            integer("reactor.recipe_time_max", 1, 100000),

            bool("reactor.wear.enabled"),
            integer("reactor.wear.interval_normal", 1, 72000),
            integer("reactor.wear.interval_degradation", 1, 72000),
            integer("reactor.wear.chat_countdown", 1, 3600),
            integer("reactor.wear.final_meltdown_start_at", 0, 3600),
            integer("reactor.wear.final_meltdown_duration", 1, 3600),

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
            bool("features.dragonegg.enabled"),
            integer("features.dragonegg.interval_ticks", 1, Integer.MAX_VALUE),
            dbl("features.dragonegg.spawn_chance", 0, 1, "Шанс спавна (0.0 — 1.0)"),
            bool("features.enderchest.enabled"),
            dbl("features.enderchest.explosion_chance", 0, 1, "Шанс взрыва (0.0 — 1.0)"),
            integer("features.enderchest.explosion_radius", 0, 100),
            integer("features.enderchest.damage", 0, Integer.MAX_VALUE),
            bool("features.entitylocator.enabled"),
            integer("features.entitylocator.scan_radius", 1, 1000),
            integer("features.entitylocator.interval_ticks", 1, 72000),
            bool("features.glassbreak.enabled"),
            integer("features.glassbreak.damage", 0, Integer.MAX_VALUE),
            bool("features.healthmeter.enabled"),
            integer("features.healthmeter.scan_radius", 1, 100),
            integer("features.healthmeter.interval_ticks", 1, 72000),
            bool("features.itemskill.enabled"),
            integer("features.itemskill.item_limit", 1, Integer.MAX_VALUE),
            integer("features.itemskill.interval_ticks", 1, 72000),
            bool("features.itemskill.warn_operators"),

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

            bool("features.modeprotect.enabled"),
            bool("features.shieldslowness.enabled"),
            integer("features.shieldslowness.slowness_amplifier", 0, 255),
            integer("features.shieldslowness.slowness_duration", 0, 72000),
            bool("features.terracotaspeed.enabled"),
            integer("features.terracotaspeed.speed_amplifier", 0, 255),
            integer("features.terracotaspeed.interval_ticks", 1, 72000),

            bool("features.unbreakable_breaker.enabled"),
            integer("features.unbreakable_breaker.hit_interval_ticks", 1, 200),

            bool("features.integrity.enabled"),
            integer("features.integrity.interval_ticks", 1, 72000),
            dbl("features.integrity.cost_multiplier", 0, 1000),
            bool("features.integrity.on_break.play_sound"),
            bool("features.integrity.on_break.send_message"),
            dbl("features.integrity.on_break.sound_volume", 0, 2),
            dbl("features.integrity.on_break.sound_pitch", 0.5, 2),
            bool("features.integrity.logging.log_init"),
            bool("features.integrity.logging.log_break"),
            bool("features.integrity.logging.log_errors"),
            bool("features.integrity.anvil_repair.enabled"),
            dbl("features.integrity.anvil_repair.integrity_multiplier", 0, 100),
            bool("features.integrity.anvil_repair.combine_enabled"),
            dbl("features.integrity.anvil_repair.combine_bonus", 0, 100),
            bool("features.integrity.anvil_repair.material_craft.enabled"),
            dbl("features.integrity.anvil_repair.material_craft.integrity_per_material", 0, 1000),
            bool("features.integrity.mending_xp.enabled"),
            dbl("features.integrity.mending_xp.integrity_multiplier", 0, 1000),
            bool("features.integrity.combine.enabled"),
            dbl("features.integrity.combine.loss_rate", 0, 100),
            bool("features.integrity.xp_integrity.enabled"),
            dbl("features.integrity.xp_integrity.integrity_per_xp", 0, 1000),
            bool("features.integrity.low_integrity_warning.enabled"),
            intList("features.integrity.low_integrity_warning.thresholds"),
            bool("features.integrity.unbreaking.enabled"),

            bool("features.creative_item_validator.enabled"),
            integer("features.creative_item_validator.max_item_bytes", 1, Integer.MAX_VALUE),
            integer("features.creative_item_validator.max_pdc_keys", 0, 10000),
            integer("features.creative_item_validator.max_lore_lines", 0, 10000),
            integer("features.creative_item_validator.max_lore_chars", 0, 100000),
            integer("features.creative_item_validator.max_name_chars", 0, 10000),
            integer("features.creative_item_validator.max_enchantments", 0, 10000),

            bool("features.shulker_protection.enabled"),
            bool("features.leash.enabled"),
            integer("features.leash.max_distance", 1, 1000, "Макс. дистанция поводка"),
            integer("features.leash.pull_back_interval", 1, 200, "Интервал подтягивания (тики)"),
            bool("features.leash.prevent_break"),
            bool("features.leash.hard_stop"),
            bool("features.container_trigger.enabled"),
            integer("features.container_trigger.interval_ticks", 1, 200, "Интервал проверки контейнера (тики)"),
            bool("features.waypoint.enabled"),
            integer("features.waypoint.interval_ticks", 1, 72000),

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

            bool("auth.enabled"),
            integer("auth.min_password_length", 8, 32, "Мин. длина пароля"),
            integer("auth.max_password_length", 1, 32, "Макс. длина пароля"),
            integer("auth.session_duration_minutes", 0, Integer.MAX_VALUE),
            bool("auth.check_ip.enabled"),
            integer("auth.request_cooldown_seconds", 0, 3600),
            integer("auth.max_accounts_per_ip", 0, 1000),
            bool("auth.check_duplicate_name.enabled"),

            bool("void_protection.enabled"),

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

            integer("suicide.countdown_duration", 1, 3600),
            integer("suicide.cooldown_seconds", 0, 36000),
            integer("suicide.confirm_timeout", 1, 3600),

            bool("packet_guard.enabled"),
            integer("packet_guard.max_packet_size", 1, Integer.MAX_VALUE, "Макс. размер пакета"),
            bool("packet_guard.log"),
            bool("packet_guard.log_inject"),

            bool("redstone_guard.enabled"),
            dbl("redstone_guard.mspt_threshold", 0, 10000),
            integer("redstone_guard.global_iterations_limit", 0, Integer.MAX_VALUE),
            integer("redstone_guard.chunk_iterations_limit", 0, Integer.MAX_VALUE),
            integer("redstone_guard.block_duration_seconds", 1, 3600),
            integer("redstone_guard.chunks_per_tick", 0, 1000),

            bool("emergency_entity_kill.enabled"),
            integer("emergency_entity_kill.entity_limit", 1, Integer.MAX_VALUE),
            dbl("emergency_entity_kill.instant_kill_mspt", 0, 10000),
            dbl("emergency_entity_kill.overload_mspt_threshold", 0, 10000),
            integer("emergency_entity_kill.max_overload_accumulation", 1, Integer.MAX_VALUE),
            integer("emergency_entity_kill.overload_increment_per_tick", 1, Integer.MAX_VALUE),
            bool("emergency_entity_kill.remove_plasma_on_overload"),
            dbl("emergency_entity_kill.plasma_removal_mspt", 0, 10000),
            bool("emergency_entity_kill.log"),

            bool("server_overload_warning.enabled"),
            dbl("server_overload_warning.high_mspt", 0, 10000),
            dbl("server_overload_warning.critical_mspt", 0, 10000),
            integer("server_overload_warning.notify_cooldown_seconds", 0, 36000),

            bool("chat_filter.enabled"),
            stringList("chat_filter.words"),
            stringList("chat_filter.regex_patterns"),

            integer("home.max_homes", 1, 1000),
            integer("home.name_min_length", 1, 100),
            integer("home.name_max_length", 1, 100),

            integer("changedimmension.cooldown_seconds", 0, 36000),

            notBlank("codepanel.empty_char", 10),
            notBlank("void_protection.target.world", 256),
            notBlank("changedimmension.default_world", 256)
    );
}
