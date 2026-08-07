package com.ultimateimprovments.broadcast;

import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 📢 Одна секция авто-броадкаста из конфига.
 * <p>
 * Секция — это «канал» со своим интервалом, условиями и набором сообщений:
 * <ul>
 *   <li>{@code cooldown_ticks} — интервал между отправками в тиках (20 тиков = 1 сек);</li>
 *   <li>{@code conditions} — строка условий (см. {@link BroadcastCondition});</li>
 *   <li>{@code messages} — сообщения в формате MiniMessage, отправляются по кругу.</li>
 * </ul>
 * <p>
 * Однотипные условия (одинаковое имя) с разными значениями — ИЛИ:
 * игроку достаточно совпадения с любым из них. Условия разных имён — И:
 * должны выполниться все. Дубликаты «имя=значение» отсекаются на этапе парсинга
 * с предупреждением в консоль (используется только первое вхождение).
 */
public final class BroadcastSection {

    private final String name;
    private final int cooldownTicks;
    private final List<String> messages = new ArrayList<>();
    /** Условия, сгруппированные по имени (внутри группы — ИЛИ, между группами — И). */
    private final Map<String, List<BroadcastCondition>> conditionGroups = new LinkedHashMap<>();

    private int accumulatedTicks;
    private int messageIndex;

    private BroadcastSection(String name, int cooldownTicks, List<String> messages) {
        this.name = name;
        this.cooldownTicks = cooldownTicks;
        this.messages.addAll(messages);
    }

    /**
     * Разбирает секцию из конфига.
     *
     * @param section секция конфига (например {@code auto_broadcast.sections.example})
     * @return готовая секция или {@code null}, если секция невалидна (уже предупреждено в консоль)
     */
    public static BroadcastSection parse(ConfigurationSection section) {
        String name = section.getName();
        int cooldown = section.getInt("cooldown_ticks", 1200);
        if (cooldown < 1) {
            ConsoleLogger.warn("[AutoBroadcast] Section '" + name
                    + "': cooldown_ticks must be >= 1, using default 1200");
            cooldown = 1200;
        }

        List<String> messages = section.getStringList("messages");
        if (messages.isEmpty()) {
            ConsoleLogger.warn("[AutoBroadcast] Section '" + name
                    + "': 'messages' is empty — section skipped");
            return null;
        }

        BroadcastSection parsed = new BroadcastSection(name, cooldown, messages);

        // Разбираем строку условий: "a=1, b=2, c=3"
        String conditions = section.getString("conditions", "");
        if (conditions != null && !conditions.trim().isEmpty()) {
            List<String> warnings = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String part : conditions.split(",")) {
                String entry = part.trim();
                if (entry.isEmpty()) continue; // лишние запятые/пробелы — без предупреждения
                BroadcastCondition condition = BroadcastCondition.parse(name, entry, warnings);
                if (condition == null) continue;
                if (!seen.add(condition.dedupeKey())) {
                    warnings.add("Section '" + name + "': duplicate condition '"
                            + condition.dedupeKey() + "' ignored (only the first one is used)");
                    continue;
                }
                parsed.conditionGroups
                        .computeIfAbsent(condition.getName(), k -> new ArrayList<>())
                        .add(condition);
            }
            for (String warning : warnings) {
                ConsoleLogger.warn("[AutoBroadcast] " + warning);
            }
        }
        return parsed;
    }

    /** @return имя секции из конфига */
    public String getName() {
        return name;
    }

    /** @return интервал отправки в тиках */
    public int getCooldownTicks() {
        return cooldownTicks;
    }

    /** @return количество сообщений в секции */
    public int getMessageCount() {
        return messages.size();
    }

    /** Накопить тики (вызывается каждый игровой тик-тик менеджера). */
    public void accumulate(int ticks) {
        accumulatedTicks += ticks;
    }

    /**
     * @return true, когда пришло время отправить сообщение (накоплено >= cooldown_ticks).
     *         При срабатывании накопленные тики сбрасываются.
     */
    public boolean isReady() {
        if (accumulatedTicks < cooldownTicks) return false;
        accumulatedTicks -= cooldownTicks;
        return true;
    }

    /** Следующее сообщение по кругу (ротация). */
    public String nextMessage() {
        String message = messages.get(messageIndex % messages.size());
        messageIndex++;
        return message;
    }

    /**
     * Проверяет все глобальные условия (online-*) секции.
     *
     * @return true, если глобальных условий нет или все они выполнены
     */
    public boolean matchesGlobal() {
        for (List<BroadcastCondition> group : conditionGroups.values()) {
            boolean hasGlobal = false;
            boolean any = false;
            for (BroadcastCondition condition : group) {
                if (!condition.isGlobal()) continue;
                hasGlobal = true;
                if (condition.matchesGlobal()) {
                    any = true;
                    break;
                }
            }
            if (hasGlobal && !any) return false;
        }
        return true;
    }

    /**
     * Проверяет все игровые условия секции для конкретного игрока.
     * Игровых условий нет → всегда true (сообщение идёт всем).
     */
    public boolean matches(Player player) {
        for (List<BroadcastCondition> group : conditionGroups.values()) {
            boolean hasPlayerCondition = false;
            boolean any = false;
            for (BroadcastCondition condition : group) {
                if (condition.isGlobal()) continue;
                hasPlayerCondition = true;
                if (condition.matches(player)) {
                    any = true;
                    break;
                }
            }
            if (hasPlayerCondition && !any) return false;
        }
        return true;
    }
}
