package com.ultimateimprovments.util;

import org.bukkit.entity.Player;

import java.util.Set;
import java.util.function.BiFunction;

/**
 * Тонкий фасад над {@link PlaceholderResolver} — единая точка входа для кода,
 * которому нужен общий реестр встроенных плейсхолдеров UltimateImprovments.
 *
 * <p>Все имена и значения определяются в {@link PlaceholderResolver}.
 * Этот класс просто прокидывает вызовы, чтобы код, использующий реестр,
 * оставался единообразным внутри плагина и снаружи через PAPI.
 *
 * <p><b>Формат:</b> {@code %<name>%} (например {@code %player_ping%}, {@code %online%}).
 */
public final class PlaceholderRegistry {

    private PlaceholderRegistry() {}

    /** Резолвер для имени плейсхолдера без обрамляющих {@code %}. */
    public static BiFunction<Player, String, String> get(String name) {
        return PlaceholderResolver.getBuiltin(name);
    }

    /** true, если имя известно реестру. */
    public static boolean contains(String name) {
        return PlaceholderResolver.getBuiltin(name) != null;
    }

    /** Все поддерживаемые имена статических плейсхолдеров. */
    public static Set<String> names() {
        return PlaceholderResolver.getBuiltinNames();
    }

    /** Идентификатор, по которому PAPI Expansion цепляется: {@code ui}. */
    public static String getIdentifier() {
        return PlaceholderResolver.getPapiIdentifier();
    }
}
