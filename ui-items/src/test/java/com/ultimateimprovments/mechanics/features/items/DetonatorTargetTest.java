package com.ultimateimprovments.mechanics.features.items;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DetonatorTargetTest {

    @Test
    void roundTripsOrdinaryCoordinates() {
        DetonatorTarget target = new DetonatorTarget("world", 128, 64, -2048);
        assertEquals(target, DetonatorTarget.decode(target.encode()));
    }

    @Test
    void roundTripsNegativeAndExtremeCoordinates() {
        DetonatorTarget target = new DetonatorTarget("world_nether", Integer.MIN_VALUE, -64, Integer.MAX_VALUE);
        assertEquals(target, DetonatorTarget.decode(target.encode()));
    }

    @Test
    void keepsWorldNamesContainingSemicolons() {
        DetonatorTarget target = new DetonatorTarget("my;custom;world", 1, 2, 3);
        assertEquals(target, DetonatorTarget.decode(target.encode()));
    }

    @Test
    void keepsWorldNamesContainingSpacesAndDots() {
        DetonatorTarget target = new DetonatorTarget("My World 1.21", 7, 8, 9);
        assertEquals(target, DetonatorTarget.decode(target.encode()));
    }

    @Test
    void rejectsMissingOrMalformedValues() {
        assertNull(DetonatorTarget.decode(null));
        assertNull(DetonatorTarget.decode(""));
        assertNull(DetonatorTarget.decode("1;2;3"));
        assertNull(DetonatorTarget.decode("1;2;3;"));
        assertNull(DetonatorTarget.decode("1;2;3;   "));
        assertNull(DetonatorTarget.decode("a;2;3;world"));
        assertNull(DetonatorTarget.decode("1;b;3;world"));
        assertNull(DetonatorTarget.decode("1;2;c;world"));
        assertNull(DetonatorTarget.decode("1.5;2;3;world"));
        assertNull(DetonatorTarget.decode("999999999999;2;3;world"));
    }

    @Test
    void toleratesSurroundingWhitespace() {
        assertEquals(new DetonatorTarget("world", 4, 5, 6),
                DetonatorTarget.decode(" 4 ; 5 ; 6 ; world "));
    }
}
