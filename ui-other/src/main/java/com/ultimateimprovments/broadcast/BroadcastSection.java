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
 * 📢 One auto-broadcast section from the config.
 * <p>
 * A section is a "channel" with its own interval, conditions and message set:
 * <ul>
 *   <li>{@code cooldown_ticks} — interval between sends in ticks (20 ticks = 1 sec);</li>
 *   <li>{@code conditions} — the conditions string (see {@link BroadcastCondition});</li>
 *   <li>{@code messages} — MiniMessage-format messages, sent in rotation.</li>
 * </ul>
 * <p>
 * Conditions of the same kind (same name) with different values are OR:
 * the player only needs to match any of them. Conditions of different names are AND:
 * all must be met. "name=value" duplicates are discarded at parse time
 * with a console warning (only the first occurrence is used).
 */
public final class BroadcastSection {

    private final String name;
    private final int cooldownTicks;
    private final List<String> messages = new ArrayList<>();
    /** Conditions grouped by name (inside a group — OR, between groups — AND). */
    private final Map<String, List<BroadcastCondition>> conditionGroups = new LinkedHashMap<>();

    private int accumulatedTicks;
    private int messageIndex;

    private BroadcastSection(String name, int cooldownTicks, List<String> messages) {
        this.name = name;
        this.cooldownTicks = cooldownTicks;
        this.messages.addAll(messages);
    }

    /**
     * Parses a section from the config.
     *
     * @param section the config section (e.g. {@code auto_broadcast.sections.example})
     * @return the ready section or {@code null} if the section is invalid (already warned to the console)
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

        // Parse the conditions string: "a=1, b=2, c=3"
        String conditions = section.getString("conditions", "");
        if (conditions != null && !conditions.trim().isEmpty()) {
            List<String> warnings = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String part : conditions.split(",")) {
                String entry = part.trim();
                if (entry.isEmpty()) continue; // stray commas/spaces — no warning
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

    /** @return the section name from the config */
    public String getName() {
        return name;
    }

    /** @return the send interval in ticks */
    public int getCooldownTicks() {
        return cooldownTicks;
    }

    /** @return the number of messages in the section */
    public int getMessageCount() {
        return messages.size();
    }

    /** Accumulate ticks (called every manager tick). */
    public void accumulate(int ticks) {
        accumulatedTicks += ticks;
    }

    /**
     * @return true when it is time to send the message (accumulated >= cooldown_ticks).
     *         On fire the accumulated ticks are reset.
     */
    public boolean isReady() {
        if (accumulatedTicks < cooldownTicks) return false;
        accumulatedTicks -= cooldownTicks;
        return true;
    }

    /** Next message in rotation. */
    public String nextMessage() {
        String message = messages.get(messageIndex % messages.size());
        messageIndex++;
        return message;
    }

    /**
     * Checks all global conditions (online-*) of the section.
     *
     * @return true if there are no global conditions or all of them are met
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
     * Checks all game conditions of the section for a specific player.
     * No game conditions → always true (the message goes to everyone).
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
