package com.ultimateimprovments.command.clan;

import java.util.ArrayList;
import java.util.List;

/**
 * ClanRoles — чистая (без Bukkit) логика иерархии ролей клана:
 * member (0) → moderator (1) → organizer (2) → leader (3).
 *
 * <p>Все константы ролей живут здесь (единый источник истины),
 * {@link ClanDatabase} ссылается на них. Класс не имеет зависимостей
 * на Bukkit — покрыт юнит-тестами.</p>
 */
public final class ClanRoles {

    /** Роли. */
    public static final String ROLE_MEMBER = "member";
    public static final String ROLE_MODERATOR = "moderator";
    public static final String ROLE_ORGANIZER = "organizer";
    public static final String ROLE_LEADER = "leader";

    /** Веса ролей. */
    public static final int W_MEMBER = 0;
    public static final int W_MODERATOR = 1;
    public static final int W_ORGANIZER = 2;
    public static final int W_LEADER = 3;

    private ClanRoles() {}

    /** Вес роли; неизвестные роли считаются member (0). */
    public static int roleWeight(String role) {
        if (ROLE_LEADER.equals(role)) return W_LEADER;
        if (ROLE_ORGANIZER.equals(role)) return W_ORGANIZER;
        if (ROLE_MODERATOR.equals(role)) return W_MODERATOR;
        return W_MEMBER;
    }

    /** True, если роль не ниже minWeight. */
    public static boolean hasRole(String role, int minWeight) {
        return roleWeight(role) >= minWeight;
    }

    public static boolean isLeader(String role) {
        return ROLE_LEADER.equals(role);
    }

    /** Можно ли актору кикнуть цель: роль актора строго выше роли цели. */
    public static boolean canKick(String actorRole, String targetRole) {
        return roleWeight(actorRole) > roleWeight(targetRole);
    }

    /** Можно ли актору выдать newRole: роль строго ниже роли актора. */
    public static boolean canGrantRole(String actorRole, String newRole) {
        return roleWeight(actorRole) > roleWeight(newRole);
    }

    /** Роли, которые актор может выдать (строго ниже его уровня), от низшей к высшей. */
    public static List<String> grantableRoles(String actorRole) {
        int w = roleWeight(actorRole);
        List<String> out = new ArrayList<>();
        if (w > W_MEMBER) out.add(ROLE_MEMBER);
        if (w > W_MODERATOR) out.add(ROLE_MODERATOR);
        if (w > W_ORGANIZER) out.add(ROLE_ORGANIZER);
        return out;
    }
}
