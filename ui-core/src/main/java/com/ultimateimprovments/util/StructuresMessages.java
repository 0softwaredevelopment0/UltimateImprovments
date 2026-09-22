package com.ultimateimprovments.util;

/**
 * Localized messages for the multi-block structures system (structure assembly,
 * NBT matching diagnostics, reactor/lightning rod flow).
 * <p>
 * All strings live in config.yml under {@code messages.structures.*} (RU tab) and
 * {@code messages_en.structures.*} (EN tab). The active section is chosen by
 * {@code messages.lang} ("en" or "ru") via {@link MessageUtil#raw(String, String)}.
 * <p>
 * Every method takes a hardcoded fallback so the code keeps working even when
 * the config section is missing entirely (fresh installs before config repair).
 */
public final class StructuresMessages {

    private StructuresMessages() {}

    /**
     * Raw message for a {@code structures.*} key (MiniMessage format, not parsed).
     *
     * @param key  key WITHOUT the {@code structures.} prefix, e.g. {@code fix_place}
     * @param def  fallback when the key is missing in both language sections
     */
    public static String get(String key, String def) {
        return MessageUtil.raw("structures." + key, def);
    }

    /**
     * Localized display name of a structure template (e.g. {@code darkfusionreactor}
     * → "Реактор тёмного синтеза" / "Dark Fusion Reactor"). Falls back to the raw
     * template name when {@code structures.names.<name>} is not configured.
     */
    public static String structureName(String templateName) {
        return get("names." + templateName, templateName);
    }
}
