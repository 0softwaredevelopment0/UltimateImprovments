package com.ultimateimprovments.broadcast;

import com.ultimateimprovments.util.AlertBroadcast;
import com.ultimateimprovments.util.PlaceholderResolver;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 📡 Одно разобранное условие авто-броадкаста.
 * <p>
 * Условие задаётся строкой вида {@code "<имя>=<значение>"}, например
 * {@code is-op=true} или {@code height-above=25}. Условия, у которых есть
 * оператор сравнения, используют суффиксы {@code -is} / {@code -above} / {@code -below}.
 * <p>
 * Два вида условий:
 * <ul>
 *   <li><b>Игровые</b> ({@code is-op}, {@code is-gamemode}, {@code height-*},
 *       {@code health-*}, {@code hunger-*}, {@code is-alert}, {@code is-group},
 *       {@code xp-lvl-*}) — проверяются отдельно для каждого онлайн-игрока.</li>
 *   <li><b>Глобальные</b> ({@code online-*}) — проверяются один раз за цикл
 *       против общего количества игроков на сервере, не зависят от конкретного игрока.</li>
 * </ul>
 * <p>
 * Однотипные условия (одинаковое имя) с разными значениями = ИЛИ.
 * Условия разных имён = И. Дубликаты «имя=значение» отсекаются с
 * предупреждением в консоли на этапе парсинга.
 */
public final class BroadcastCondition {

    /** Оператор сравнения числовых условий. */
    public enum Op { IS, ABOVE, BELOW }

    /** Паттерн условий с оператором: height-above=25, xp-lvl-below=10 и т.д. */
    private static final Pattern PREFIXED =
            Pattern.compile("^(height|health|hunger|online|xp-lvl)-(is|above|below)$");

    private final String name;        // нормализованное имя, напр. "height-above", "is-op"
    private final Op op;              // IS для простых (boolean/строковых) условий
    private final String rawValue;    // значение как строка из конфига (нижний регистр)
    private final boolean global;     // true для online-* (серверное условие)
    private final boolean boolValue;  // для is-op / is-alert
    private final int intValue;       // для height / health / hunger / xp-lvl / online
    private final GameMode gameMode;  // для is-gamemode

    private BroadcastCondition(String name, Op op, String rawValue, boolean global,
                               boolean boolValue, int intValue, GameMode gameMode) {
        this.name = name;
        this.op = op;
        this.rawValue = rawValue;
        this.global = global;
        this.boolValue = boolValue;
        this.intValue = intValue;
        this.gameMode = gameMode;
    }

    /**
     * Разбирает одну запись условия из строки конфига.
     *
     * @param section  имя секции (для сообщений об ошибках)
     * @param entry    запись вида "имя=значение" (уже обрезанная)
     * @param warnings накопитель предупреждений (может быть null)
     * @return готовое условие или {@code null}, если запись некорректна (уже предупреждено)
     */
    public static BroadcastCondition parse(String section, String entry, List<String> warnings) {
        int eq = entry.indexOf('=');
        if (eq <= 0) {
            warn(warnings, "Section '" + section + "': invalid condition '" + entry
                    + "' — expected \"name=value\", ignored");
            return null;
        }
        String left = entry.substring(0, eq).trim().toLowerCase(Locale.ROOT);
        String value = entry.substring(eq + 1).trim().toLowerCase(Locale.ROOT);
        if (left.isEmpty() || value.isEmpty()) {
            warn(warnings, "Section '" + section + "': invalid condition '" + entry
                    + "' — name and value cannot be empty, ignored");
            return null;
        }

        // Условия с оператором: height-above=25, xp-lvl-is=10 ...
        Matcher m = PREFIXED.matcher(left);
        if (m.matches()) {
            String base = m.group(1);
            Op op = Op.valueOf(m.group(2).toUpperCase(Locale.ROOT));
            boolean global = "online".equals(base);
            int intValue;
            try {
                intValue = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                warn(warnings, "Section '" + section + "': condition '" + left
                        + "' requires an integer value, got '" + value + "', ignored");
                return null;
            }
            if (global && intValue < 0) {
                warn(warnings, "Section '" + section + "': condition 'online-*' requires value >= 0, got '"
                        + value + "', ignored");
                return null;
            }
            return new BroadcastCondition(left, op, value, global, false, intValue, null);
        }

        // Простые условия: is-op, is-gamemode, is-alert, is-group
        Op op = Op.IS;
        switch (left) {
            case "is-op":
            case "is-alert": {
                if (!value.equals("true") && !value.equals("false")) {
                    warn(warnings, "Section '" + section + "': condition '" + left
                            + "' requires true/false, got '" + value + "', ignored");
                    return null;
                }
                return new BroadcastCondition(left, op, value, false,
                        Boolean.parseBoolean(value), 0, null);
            }
            case "is-gamemode": {
                GameMode gm;
                try {
                    gm = GameMode.valueOf(value.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    warn(warnings, "Section '" + section + "': condition 'is-gamemode' requires one of "
                            + "survival/creative/spectator/adventure, got '" + value + "', ignored");
                    return null;
                }
                return new BroadcastCondition(left, op, value, false, false, 0, gm);
            }
            case "is-group": {
                return new BroadcastCondition(left, op, value, false, false, 0, null);
            }
            default:
                warn(warnings, "Section '" + section + "': unknown condition '" + left + "', ignored");
                return null;
        }
    }

    /**
     * Проверяет условие для конкретного игрока. Не вызывать для глобальных условий
     * (online-*) — для них есть {@link #matchesGlobal()}.
     */
    public boolean matches(Player player) {
        if (player == null) return false;
        switch (name) {
            case "is-op":
                return player.isOp() == boolValue;
            case "is-alert":
                return AlertBroadcast.hasAlertPermission(player) == boolValue;
            case "is-gamemode":
                return player.getGameMode() == gameMode;
            case "is-group": {
                // Основная группа LuckPerms (как плейсхолдер %group%)
                String group = PlaceholderResolver.resolveBuiltin(player, "group");
                if (group != null && !group.isEmpty() && group.equalsIgnoreCase(rawValue)) {
                    return true;
                }
                // Fallback: LuckPerms выдаёт право group.<имя> для основной И унаследованных
                // групп, поэтому проверка покрывает и наследование.
                return player.hasPermission("group." + rawValue);
            }
            case "height":
                return compareInt(player.getLocation().getBlockY());
            case "health":
                return switch (op) {
                    // IS сравнивает ЦЕЛУЮ часть здоровья (19.5 → 19),
                    // ABOVE/BELOW — сырое значение (19.5 > 18 → true).
                    case IS -> (int) player.getHealth() == intValue;
                    case ABOVE -> player.getHealth() > intValue;
                    case BELOW -> player.getHealth() < intValue;
                };
            case "hunger":
                return compareInt(player.getFoodLevel());
            case "xp-lvl":
                return compareInt(player.getLevel());
            default:
                return false;
        }
    }

    /**
     * Проверяет глобальное (серверное) условие {@code online-*}.
     * Для всех остальных условий возвращает {@code false}.
     */
    public boolean matchesGlobal() {
        if (!global) return false;
        return compareInt(Bukkit.getOnlinePlayers().size());
    }

    /** @return имя условия (нормализованное), напр. "height-above" */
    public String getName() {
        return name;
    }

    /** @return сырое значение условия (нижний регистр) */
    public String getRawValue() {
        return rawValue;
    }

    /** @return true если условие глобальное (online-*) и не зависит от игрока */
    public boolean isGlobal() {
        return global;
    }

    /** Ключ для дедупликации: имя=значение (регистронезависимо). */
    public String dedupeKey() {
        return name + "=" + rawValue;
    }

    private boolean compareInt(int actual) {
        return switch (op) {
            case IS -> actual == intValue;
            case ABOVE -> actual > intValue;
            case BELOW -> actual < intValue;
        };
    }

    private static void warn(List<String> warnings, String message) {
        if (warnings != null) {
            warnings.add(message);
        }
    }
}
