package com.ultimateimprovments.mechanics.protection;

import java.util.Locale;

/**
 * Formatting of fractional protection points for chat, GUI and holograms.
 * <p>
 * Points became doubles in {@code 1.8.5-beta.16} (the 1.1 price curve is smooth, whole
 * numbers would round it away), so every user-facing place must print them the same way:
 * {@code 2} for whole values, {@code 1.21} for fractions, never {@code 1.2100000000000002}.
 */
public final class ProtectionFormat {

    private ProtectionFormat() {}

    /**
     * @param value points or a price
     * @return «0», «2», «1.21», «1.1» — at most two decimals, trailing zeros stripped
     */
    public static String points(double value) {
        if (!Double.isFinite(value)) return "∞";
        double rounded = Math.round(value * 100.0) / 100.0;
        if (rounded == Math.rint(rounded) && Math.abs(rounded) < 1.0e15) {
            return String.valueOf((long) rounded);
        }
        String text = String.format(Locale.ROOT, "%.2f", rounded);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
