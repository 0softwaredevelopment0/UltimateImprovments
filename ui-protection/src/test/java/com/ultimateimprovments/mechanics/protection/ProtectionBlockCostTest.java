package com.ultimateimprovments.mechanics.protection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-math tests for the protection price curve.
 * <p>
 * {@link ProtectionBlock#scaledCost(int, int, double)} is static and takes the growth
 * factor explicitly, so the curve can be verified without a running server and without
 * touching {@link ProtectionConfig} (which reads the plugin config).
 */
class ProtectionBlockCostTest {

    private static final double EPS = 1e-9;

    /** First purchase always costs exactly the base price. */
    @Test
    void firstPurchaseCostsBase() {
        assertEquals(1.0, ProtectionBlock.scaledCost(1, 0, 1.1), EPS);
        assertEquals(25.0, ProtectionBlock.scaledCost(25, 0, 1.1), EPS);
        assertEquals(1.0, ProtectionBlock.scaledCost(1, 0, 2.0), EPS);
    }

    /** Default curve is smooth and NOT rounded: 1, 1.1, 1.21, 1.331 ... */
    @Test
    void defaultGrowthIsSmoothAndFractional() {
        assertEquals(1.1, ProtectionBlock.scaledCost(1, 1, 1.1), EPS);
        assertEquals(1.21, ProtectionBlock.scaledCost(1, 2, 1.1), EPS);
        assertEquals(1.331, ProtectionBlock.scaledCost(1, 3, 1.1), EPS);
        assertEquals(2.5937424601000003, ProtectionBlock.scaledCost(1, 10, 1.1), EPS);
        assertEquals(8.0 * 1.1, ProtectionBlock.scaledCost(8, 1, 1.1), EPS);
    }

    /** The old behaviour is still reachable through the config (growth = 2.0). */
    @Test
    void doublingStillSupported() {
        assertEquals(1.0, ProtectionBlock.scaledCost(1, 0, 2.0), EPS);
        assertEquals(2.0, ProtectionBlock.scaledCost(1, 1, 2.0), EPS);
        assertEquals(8.0, ProtectionBlock.scaledCost(1, 3, 2.0), EPS);
        assertEquals(64.0, ProtectionBlock.scaledCost(4, 4, 2.0), EPS);
    }

    /** growth <= 1 (or a broken value) must degrade to a flat price, never to a negative one. */
    @Test
    void nonGrowingFactorsMeanFlatPrice() {
        assertEquals(10.0, ProtectionBlock.scaledCost(10, 50, 1.0), EPS);
        assertEquals(10.0, ProtectionBlock.scaledCost(10, 50, 0.5), EPS);
        assertEquals(10.0, ProtectionBlock.scaledCost(10, 50, -3.0), EPS);
        assertEquals(10.0, ProtectionBlock.scaledCost(10, 50, Double.NaN), EPS);
        assertEquals(10.0, ProtectionBlock.scaledCost(10, -7, 1.1), EPS);
    }

    /** Huge counters / degenerate bases must clamp to MAX_COST instead of overflowing. */
    @Test
    void overflowClampsToMaxCost() {
        assertEquals(ProtectionBlock.MAX_COST, ProtectionBlock.scaledCost(1, 1000, 1.1), EPS);
        assertEquals(ProtectionBlock.MAX_COST, ProtectionBlock.scaledCost(Integer.MAX_VALUE, 5, 2.0), EPS);
        assertEquals(ProtectionBlock.MAX_COST, ProtectionBlock.scaledCost(0, 3, 1.1), EPS);
        assertEquals(ProtectionBlock.MAX_COST, ProtectionBlock.scaledCost(-4, 3, 1.1), EPS);
    }

    /** A growing curve is never cheaper than the previous step. */
    @Test
    void priceIsMonotonic() {
        double previous = 0.0;
        for (int n = 0; n <= 400; n++) {
            double cost = ProtectionBlock.scaledCost(3, n, 1.1);
            assertTrue(cost >= previous, "cost dropped at n=" + n + ": " + cost + " < " + previous);
            previous = cost;
        }
        assertEquals(ProtectionBlock.MAX_COST, previous, EPS);
    }
}
