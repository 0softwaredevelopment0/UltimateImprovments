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

    /** First purchase always costs exactly the base price. */
    @Test
    void firstPurchaseCostsBase() {
        assertEquals(1, ProtectionBlock.scaledCost(1, 0, 1.1));
        assertEquals(25, ProtectionBlock.scaledCost(25, 0, 1.1));
        assertEquals(1, ProtectionBlock.scaledCost(1, 0, 2.0));
    }

    /** Default curve: ceil(base × 1.1^n), i.e. 1, 2, 2, 2, 2, 2, 2, 2, 3 ... */
    @Test
    void defaultGrowthIsGentleAndCeiled() {
        assertEquals(2, ProtectionBlock.scaledCost(1, 1, 1.1));  // 1.10 -> 2
        assertEquals(2, ProtectionBlock.scaledCost(1, 4, 1.1));  // 1.4641 -> 2
        assertEquals(3, ProtectionBlock.scaledCost(1, 8, 1.1));  // 2.1436 -> 3
        assertEquals(8, ProtectionBlock.scaledCost(5, 4, 1.1));  // 7.3205 -> 8
    }

    /** The old behaviour is still reachable through the config (growth = 2.0). */
    @Test
    void doublingStillSupported() {
        assertEquals(1, ProtectionBlock.scaledCost(1, 0, 2.0));
        assertEquals(2, ProtectionBlock.scaledCost(1, 1, 2.0));
        assertEquals(8, ProtectionBlock.scaledCost(1, 3, 2.0));
        assertEquals(64, ProtectionBlock.scaledCost(4, 4, 2.0));
    }

    /** growth <= 1 (or a broken value) must degrade to a flat price, never to a negative one. */
    @Test
    void nonGrowingFactorsMeanFlatPrice() {
        assertEquals(10, ProtectionBlock.scaledCost(10, 50, 1.0));
        assertEquals(10, ProtectionBlock.scaledCost(10, 50, 0.5));
        assertEquals(10, ProtectionBlock.scaledCost(10, 50, -3.0));
        assertEquals(10, ProtectionBlock.scaledCost(10, 50, Double.NaN));
        assertEquals(10, ProtectionBlock.scaledCost(10, -7, 1.1));
    }

    /** Huge counters must clamp to Integer.MAX_VALUE instead of overflowing. */
    @Test
    void overflowClampsToMaxInt() {
        assertEquals(Integer.MAX_VALUE, ProtectionBlock.scaledCost(1, 1000, 1.1));
        assertEquals(Integer.MAX_VALUE, ProtectionBlock.scaledCost(Integer.MAX_VALUE, 5, 2.0));
        assertEquals(Integer.MAX_VALUE, ProtectionBlock.scaledCost(0, 3, 1.1));
        assertEquals(Integer.MAX_VALUE, ProtectionBlock.scaledCost(-4, 3, 1.1));
    }

    /** A growing curve is never cheaper than the previous step. */
    @Test
    void priceIsMonotonic() {
        int previous = 0;
        for (int n = 0; n <= 400; n++) {
            int cost = ProtectionBlock.scaledCost(3, n, 1.1);
            assertTrue(cost >= previous, "cost dropped at n=" + n + ": " + cost + " < " + previous);
            previous = cost;
        }
        assertEquals(Integer.MAX_VALUE, previous);
    }
}
