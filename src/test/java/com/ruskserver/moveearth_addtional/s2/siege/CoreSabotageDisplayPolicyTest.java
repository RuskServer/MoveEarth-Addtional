package com.ruskserver.moveearth_addtional.s2.siege;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoreSabotageDisplayPolicyTest {
    @Test
    void roundsCountdownUpToWholeSeconds() {
        assertEquals(20, CoreSabotageDisplayPolicy.remainingSeconds(0,
                CoreSabotageDisplayPolicy.INSTALL_TICKS));
        assertEquals(1, CoreSabotageDisplayPolicy.remainingSeconds(399,
                CoreSabotageDisplayPolicy.INSTALL_TICKS));
        assertEquals(0, CoreSabotageDisplayPolicy.remainingSeconds(400,
                CoreSabotageDisplayPolicy.INSTALL_TICKS));
    }

    @Test
    void warningsAccelerateOnlyNearDetonation() {
        assertEquals(20, CoreSabotageDisplayPolicy.warningInterval(false, 799));
        assertEquals(20, CoreSabotageDisplayPolicy.warningInterval(true, 599));
        assertEquals(10, CoreSabotageDisplayPolicy.warningInterval(true, 600));
        assertEquals(10, CoreSabotageDisplayPolicy.warningInterval(true, 739));
        assertEquals(5, CoreSabotageDisplayPolicy.warningInterval(true, 740));
    }
}
