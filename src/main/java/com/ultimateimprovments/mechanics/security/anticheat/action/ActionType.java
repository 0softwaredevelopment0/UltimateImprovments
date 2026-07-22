package com.ultimateimprovments.mechanics.security.anticheat.action;

/**
 * Действия при достижении порога нарушений.
 */
public enum ActionType {
    /** Только запись в лог */
    LOG,
    /** Уведомление администраторам */
    NOTIFY,
    /** Откат игрока на последнюю валидную позицию */
    SETBACK,
    /** Кик с сервера */
    KICK,
    /** Бан (через PunishmentManager) */
    BAN
}
