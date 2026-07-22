package com.ultimateimprovements.hook;

import com.ultimateimprovements.util.PlaceholderResolver;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI Expansion для UltimateImprovements.
 *
 * <p>Регистрирует все наши плейсхолдеры в PAPI под идентификатором {@code ui}.
 * Если PAPI установлен, внешние плагины (TAB, scoreboard-плагины, Discord-интеграции)
 * могут писать {@code %ui_player_ping%}, {@code %ui_online%},
 * {@code %ui_tps_5m%} и т.д. — и получать те же значения, что видит наш плагин.
 *
 * <p><b>Список имён:</b> единый для внутреннего и внешнего резолвера, берётся из
 * {@link PlaceholderResolver#getBuiltinNames()} + динамические шаблоны (tps_* mspt_* и т.п.).
 *
 * <p><b>Если PAPI не установлен:</b> этот класс НЕ регистрируется (см. {@link PluginStartup}),
 * а внутренний {@link PlaceholderResolver#resolve(String, Player)} продолжает работать
 * для собственных строк плагина.
 *
 * <p>Все запросы делегируются в {@link PlaceholderResolver#resolveInternal(String, Player)} —
 * внутренний резолв БЕЗ шага PAPI, чтобы избежать рекурсии PAPI → resolve → PAPI.
 */
public class UIPlaceholderExpansion extends PlaceholderExpansion {

    @Override
    public @NotNull String getIdentifier() {
        return PlaceholderResolver.getPapiIdentifier(); // "ui"
    }

    @Override
    public @NotNull String getAuthor() {
        return "UltimateImprovements";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true; // не выгружать при /reload
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    /**
     * PAPI вызывает этот метод для каждого {@code %mcplugin_<params>%}.
     * <p>{@code params} = всё, что после {@code mcplugin_}. Например,
     * {@code %mcplugin_player_ping%} → onRequest(offline, "player_ping").
     */
    @Override
    public @Nullable String onRequest(OfflinePlayer offline, @NotNull String params) {
        if (params == null || params.isEmpty()) return null;

        // PAPI всегда передаёт OfflinePlayer. Если игрок онлайн — берём Player;
        // иначе Player=null (статические плейсхолдеры и серверные значения всё равно работают).
        Player online = (offline != null && offline.isOnline()) ? offline.getPlayer() : null;

        // 1. Быстрый путь: точное совпадение имени в BUILTIN
        String direct = PlaceholderResolver.resolveBuiltin(online, params);
        if (direct != null) return direct;

        // 2. Динамические шаблоны (%tps_5m%, %ping_5m_all%, %copy:"x"%, ...)
        //    Делаем безопасный wrapper: НЕ вызываем resolvePapi, чтобы не было рекурсии.
        String wrapped = "%" + params + "%";
        String resolved = PlaceholderResolver.resolveInternal(wrapped, online);

        // Если ничего не изменилось — этот плейсхолдер не наш. Пусть PAPI вернёт null.
        if (resolved.equals(wrapped)) return null;
        if (resolved.isEmpty()) return null;
        return resolved;
    }
}
