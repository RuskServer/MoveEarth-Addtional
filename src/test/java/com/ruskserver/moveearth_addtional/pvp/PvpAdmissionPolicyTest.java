package com.ruskserver.moveearth_addtional.pvp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PvpAdmissionPolicyTest {
    @Test void respawningParticipantMayChangeLoadoutButNewSpectatorCannotJoin() {
        boolean spectatorAdmission = PvpAdmissionPolicy.eligible(true, true, false, false, false);
        assertTrue(PvpAdmissionPolicy.canUpdateSelection(true, spectatorAdmission));
        assertFalse(PvpAdmissionPolicy.canUpdateSelection(false, spectatorAdmission));
        assertTrue(PvpAdmissionPolicy.canUpdateSelection(false,
                PvpAdmissionPolicy.eligible(true, false, false, false, false)));
    }

    @Test void startRequiresBothTeamsAfterQueueFiltering() {
        assertFalse(PvpAdmissionPolicy.canStart(0, 0));
        assertFalse(PvpAdmissionPolicy.canStart(1, 0));
        assertFalse(PvpAdmissionPolicy.canStart(0, 1));
        assertFalse(PvpAdmissionPolicy.canStart(3, 0));
        assertTrue(PvpAdmissionPolicy.canStart(1, 1));
        assertTrue(PvpAdmissionPolicy.canStart(2, 1));
    }
    @Test void healthyQueuedPlayerMayEnter() {
        assertTrue(PvpAdmissionPolicy.eligible(true, false, false, false, false));
    }
    @Test void stateChangesDuringVotingPreventEntry() {
        assertFalse(PvpAdmissionPolicy.eligible(false, false, false, false, false));
        assertFalse(PvpAdmissionPolicy.eligible(true, true, false, false, false));
        assertFalse(PvpAdmissionPolicy.eligible(true, false, true, false, false));
        assertFalse(PvpAdmissionPolicy.eligible(true, false, false, true, false));
        assertFalse(PvpAdmissionPolicy.eligible(true, false, false, false, true));
    }
}
