package com.ruskserver.moveearth_addtional.client.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupFlowPolicyTest {
    @Test
    void warningAndLogoDelaysUseInclusiveBoundaries() {
        assertFalse(StartupFlowPolicy.warningCanContinue(1_499L));
        assertTrue(StartupFlowPolicy.warningCanContinue(1_500L));
        assertFalse(StartupFlowPolicy.logoCanSkip(249L));
        assertTrue(StartupFlowPolicy.logoCanSkip(250L));
        assertFalse(StartupFlowPolicy.logoFinished(1_799L));
        assertTrue(StartupFlowPolicy.logoFinished(1_800L));
    }

    @Test
    void entranceProgressIsClampedAndEased() {
        assertEquals(0.0F, StartupFlowPolicy.easedProgress(-1L, 650L));
        assertEquals(1.0F, StartupFlowPolicy.easedProgress(650L, 650L));
        assertTrue(StartupFlowPolicy.easedProgress(325L, 650L) > 0.5F);
    }
}
