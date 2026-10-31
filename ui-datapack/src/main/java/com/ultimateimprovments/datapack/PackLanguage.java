package com.ultimateimprovments.datapack;

import com.moandjiezana.toml.Toml;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

import java.io.File;
import java.util.Locale;

/**
 * PackLanguage — the single source of truth for the bundled datapack's language.
 * <p>
 * Two very different pieces have to agree on it: {@link DatapackPackBootstrap}
 * (picks {@code UI-Datapack-en} or {@code UI-Datapack-ru}) and
 * {@link EnchantmentRegistryBootstrap} (picks the enchantment display names, see
 * there for why the language is baked in at bootstrap time). If they diverged, a
 * Russian server would render English names for enchantments that came from the
 * Russian pack, so the resolution lives in one place only.
 * <p>
 * Resolution order: explicit {@code datapack.lang} ({@code "en"}/{@code "ru"}) wins,
 * anything else ({@code "auto"}, blank, unknown) follows {@code messages.lang} from
 * {@code UI-Core.toml}, falling back to {@link #DEFAULT_LANG}.
 */
final class PackLanguage {

    static final String EN = "en";
    static final String RU = "ru";

    /** Language used when nothing is configured and UI-Core has no preference yet. */
    static final String DEFAULT_LANG = EN;

    private PackLanguage() {}

    /**
     * Resolves the language: an explicit {@code datapack.lang} ("en"/"ru") wins,
     * anything else ("auto", blank, unknown) follows {@code messages.lang} from
     * {@code UI-Core.toml}, falling back to {@link #DEFAULT_LANG}.
     */
    static String resolve(String configured, File shared, ComponentLogger log) {
        String wanted = normalize(configured);
        if (wanted != null) return wanted;

        if (configured != null && !configured.isBlank()
                && !"auto".equalsIgnoreCase(configured.trim())) {
            log.warn("Unknown datapack.lang '" + configured
                    + "' (expected 'en', 'ru' or 'auto') — following messages.lang instead.");
        }
        String global = readCoreLanguage(shared);
        return global == null ? DEFAULT_LANG : global;
    }

    /** Reads {@code messages.lang} straight from {@code configs/UI-Core.toml} (may not exist yet). */
    static String readCoreLanguage(File shared) {
        File core = new File(new File(shared, "configs"), "UI-Core.toml");
        if (!core.isFile()) return null;
        try {
            Toml messages = new Toml().read(core).getTable("messages");
            return messages == null ? null : normalize(messages.getString("lang"));
        } catch (Exception ignored) {
            return null;
        }
    }

    /** @return "en"/"ru" for a valid language value, otherwise {@code null} */
    static String normalize(String lang) {
        if (lang == null) return null;
        String value = lang.trim().toLowerCase(Locale.ROOT);
        return EN.equals(value) || RU.equals(value) ? value : null;
    }
}
