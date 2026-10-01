package com.ruskserver.moveearth_addtional.s2.time;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClosedHoursFreezeMigrationTest {
    @Test
    void recoveryRecordCannotBeClearedWhileLevelDatStillContainsThePause() {
        assertFalse(ClosedHoursFreeze.restorationSaved(true, false, "false", "false"));
        assertFalse(ClosedHoursFreeze.restorationSaved(true, false, "", ""));
        assertTrue(ClosedHoursFreeze.restorationSaved(true, false, "true", "false"));
        assertFalse(ClosedHoursFreeze.restorationSaved(false, true, "false", "false"));
        assertTrue(ClosedHoursFreeze.restorationSaved(false, true, "false", "true"));
    }
}
