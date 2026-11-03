package com.ultimateimprovments.mechanics.protection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Points are doubles since {@code 1.8.5-beta.16}; every user-facing place goes through
 * {@link ProtectionFormat#points(double)}, so the output must never leak float noise
 * («1.2100000000000002») and must stay short in the GUI.
 */
class ProtectionFormatTest {

    @Test
    void wholeValuesHaveNoDecimals() {
        assertEquals("0", ProtectionFormat.points(0.0));
        assertEquals("2", ProtectionFormat.points(2.0));
        assertEquals("160", ProtectionFormat.points(160.0));
        assertEquals("-3", ProtectionFormat.points(-3.0));
    }

    @Test
    void fractionalPricesKeepAtMostTwoDecimals() {
        assertEquals("1.1", ProtectionFormat.points(1.1));
        assertEquals("1.21", ProtectionFormat.points(1.21));
        assertEquals("1.33", ProtectionFormat.points(1.331));
        assertEquals("2.59", ProtectionFormat.points(2.5937424601000003));
    }

    @Test
    void floatNoiseIsRoundedAway() {
        assertEquals("30", ProtectionFormat.points(0.1 * 300));
        assertEquals("158.79", ProtectionFormat.points(160.0 - 1.21));
    }

    @Test
    void nonFiniteAndHugeValuesDoNotCrash() {
        assertEquals("∞", ProtectionFormat.points(Double.NaN));
        assertEquals("∞", ProtectionFormat.points(Double.POSITIVE_INFINITY));
        // MAX_COST (Integer.MAX_VALUE) is far beyond the long-safe formatting branch.
        assertEquals("2147483647", ProtectionFormat.points(ProtectionBlock.MAX_COST));
    }
}
