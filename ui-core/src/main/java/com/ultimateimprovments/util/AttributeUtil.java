package com.ultimateimprovments.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;

import java.util.Locale;

/**
 * AttributeUtil — the single shared entry point for runtime attribute grants.
 * <p>
 * Every config-driven attribute value in the plugin goes through here, so the
 * "resolve the key → set the base value (and keep health sane on MAX_HEALTH
 * changes)" logic exists exactly once:
 * <ul>
 *   <li>{@link #resolve(String)} — {@code minecraft:waypoint_receive_range},
 *       {@code waypoint_receive_range} and {@code WAYPOINT_RECEIVE_RANGE} all
 *       resolve to the same {@link Attribute}. Resolution goes through
 *       {@link Registry#ATTRIBUTE}, so attributes added by newer Paper builds
 *       (and datapack-patched registries) work without a code change;</li>
 *   <li>{@link #applyBase(LivingEntity, Attribute, double)} — grants a value
 *       (sets the base value; item/effect modifiers stay on top of it);</li>
 *   <li>{@link #resetToDefault(LivingEntity, Attribute)} — restores the
 *       vanilla default value.</li>
 * </ul>
 * The methods are null-safe and never throw on bad input: an unknown attribute
 * key or an entity without that attribute simply returns {@code false}.
 */
public final class AttributeUtil {

    private AttributeUtil() {}

    // =========================
    // RESOLUTION
    // =========================

    /**
     * Resolves an attribute from its config representation.
     *
     * @param raw a namespaced key ({@code minecraft:waypoint_receive_range}),
     *            a bare key ({@code waypoint_receive_range}) or a legacy constant
     *            name ({@code WAYPOINT_RECEIVE_RANGE}); {@code null} when unknown
     */
    public static Attribute resolve(String raw) {
        if (raw == null) return null;
        String input = raw.trim();
        if (input.isEmpty()) return null;

        // 1) Namespaced key — the canonical form written by the config templates.
        if (input.indexOf(':') >= 0) {
            NamespacedKey key = NamespacedKey.fromString(input.toLowerCase(Locale.ROOT));
            return key == null ? null : Registry.ATTRIBUTE.get(key);
        }

        // 2) Bare key / legacy constant name — case-insensitive registry scan.
        String bare = input.toLowerCase(Locale.ROOT);
        for (Attribute attribute : Registry.ATTRIBUTE) {
            if (attribute.getKey().getKey().equals(bare)) return attribute;
        }
        return null;
    }

    /** @return the entity's instance of the attribute, or {@code null} when it has none. */
    public static AttributeInstance instance(LivingEntity entity, Attribute attribute) {
        if (entity == null || attribute == null) return null;
        return entity.getAttribute(attribute);
    }

    // =========================
    // GRANTS
    // =========================

    /**
     * Grants {@code value} as the attribute's base value.
     * <p>
     * MAX_HEALTH is clamped afterwards: raising the max never touches current
     * health, lowering it never leaves the entity above the new maximum.
     *
     * @return true when the value was applied
     */
    public static boolean applyBase(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = instance(entity, attribute);
        if (instance == null || Double.isNaN(value) || Double.isInfinite(value)) return false;

        instance.setBaseValue(value);
        clampHealth(entity, attribute);
        return true;
    }

    /**
     * Restores the attribute's vanilla default value (MAX_HEALTH clamping included).
     *
     * @return true when the value was reset
     */
    public static boolean resetToDefault(LivingEntity entity, Attribute attribute) {
        AttributeInstance instance = instance(entity, attribute);
        if (instance == null) return false;

        instance.setBaseValue(attribute.getDefaultValue());
        clampHealth(entity, attribute);
        return true;
    }

    /** Keeps current health within the (possibly lowered) max-health value. */
    private static void clampHealth(LivingEntity entity, Attribute attribute) {
        if (attribute != Attribute.MAX_HEALTH) return;
        AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) return;
        double max = maxHealth.getValue();
        if (entity.getHealth() > max) entity.setHealth(max);
    }
}
