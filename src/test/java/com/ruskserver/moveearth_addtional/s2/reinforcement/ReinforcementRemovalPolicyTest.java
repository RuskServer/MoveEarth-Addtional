package com.ruskserver.moveearth_addtional.s2.reinforcement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReinforcementRemovalPolicyTest {
    @Test
    void permitsAuthorizedOwnerWithinReach() {
        assertTrue(ReinforcementRemovalPolicy.canStrip(true, true, true, true));
    }

    @Test
    void rejectsNonMembersAndForeignLocations() {
        assertFalse(ReinforcementRemovalPolicy.canStrip(false, true, true, true));
        assertFalse(ReinforcementRemovalPolicy.canStrip(true, true, false, true));
    }

    @Test
    void rejectsMissingPermissionAndRemoteTargets() {
        assertFalse(ReinforcementRemovalPolicy.canStrip(true, false, true, true));
        assertFalse(ReinforcementRemovalPolicy.canStrip(true, true, true, false));
    }
}
