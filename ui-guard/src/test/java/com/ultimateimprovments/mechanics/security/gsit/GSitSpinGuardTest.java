package com.ultimateimprovments.mechanics.security.gsit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Command-label matching for the GSit /spin guard. */
class GSitSpinGuardTest {

    @Test
    @DisplayName("/spin and the GSit alias /gspin are guarded, other commands are not")
    void matchesOnlySpinLabels() {
        assertTrue(GSitSpinGuard.isSpinCommand("/spin"));
        assertTrue(GSitSpinGuard.isSpinCommand("/gspin"));
        assertTrue(GSitSpinGuard.isSpinCommand("/SPIN"));
        assertTrue(GSitSpinGuard.isSpinCommand("spin"));
        assertTrue(GSitSpinGuard.isSpinCommand("/spin 10"));
        assertTrue(GSitSpinGuard.isSpinCommand("/gsit:spin"));
        assertTrue(GSitSpinGuard.isSpinCommand("/gsit:gspin now"));

        assertFalse(GSitSpinGuard.isSpinCommand("/sit"));
        assertFalse(GSitSpinGuard.isSpinCommand("/spin2"));
        assertFalse(GSitSpinGuard.isSpinCommand("/gsit spin"));
        assertFalse(GSitSpinGuard.isSpinCommand("/me spin"));
        assertFalse(GSitSpinGuard.isSpinCommand(""));
        assertFalse(GSitSpinGuard.isSpinCommand(null));
    }
}
