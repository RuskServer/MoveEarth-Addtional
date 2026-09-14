package com.ruskserver.moveearth_addtional.s2.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatTagBossBarDisplayTest {
    @Test
    void convertsTicksIntoAClampedCountdown() {
        var display = CombatTagBossBarDisplay.create(300L, 600L);
        assertEquals(15, display.seconds());
        assertEquals(0.5F, display.progress());
        assertFalse(display.urgent());
        assertTrue(CombatTagBossBarDisplay.create(200L, 600L).urgent());
    }

    @Test
    void refreshOrExtensionResetsTheMaximumButCountdownDoesNot() {
        assertEquals(600L, CombatTagBossBarDisplay.updatedMaximum(600L, 400L, 600L));
        assertEquals(600L, CombatTagBossBarDisplay.updatedMaximum(600L, 400L, 380L));
        assertEquals(1200L, CombatTagBossBarDisplay.updatedMaximum(600L, 400L, 1200L));
    }
}
