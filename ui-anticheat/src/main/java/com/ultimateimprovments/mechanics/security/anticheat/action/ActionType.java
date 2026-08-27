package com.ultimateimprovments.mechanics.security.anticheat.action;

/**
 * Actions taken when the violation threshold is reached.
 */
public enum ActionType {
    /** Log only */
    LOG,
    /** Notify administrators */
    NOTIFY,
    /** Roll the player back to the last valid position */
    SETBACK,
    /** Kick from the server */
    KICK,
    /** Ban (via PunishmentManager) */
    BAN
}
