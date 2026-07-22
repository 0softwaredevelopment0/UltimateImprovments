package com.ultimateimprovements.mechanics.security.anticheat.core;

/**
 * Результат проверки античита.
 */
public class CheckResult {

    private final boolean flagged;
    private final double vl;
    private final String message;
    private final String detail;

    private CheckResult(boolean flagged, double vl, String message, String detail) {
        this.flagged = flagged;
        this.vl = vl;
        this.message = message;
        this.detail = detail;
    }

    /** Проверка пройдена — нет нарушений. */
    public static CheckResult passed() {
        return new CheckResult(false, 0, null, null);
    }

    /** Нарушение обнаружено. */
    public static CheckResult flagged(double vl, String message) {
        return new CheckResult(true, vl, message, null);
    }

    /** Нарушение обнаружено с деталями. */
    public static CheckResult flagged(double vl, String message, String detail) {
        return new CheckResult(true, vl, message, detail);
    }

    public boolean isFlagged() { return flagged; }
    public double getVl() { return vl; }
    public String getMessage() { return message; }
    public String getDetail() { return detail; }

    @Override
    public String toString() {
        if (!flagged) return "CheckResult%PASSED%";
        return "CheckResult{FLAGGED vl=" + vl + " msg=" + message + "}";
    }
}
