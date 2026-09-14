package com.ruskserver.moveearth_addtional.s2.nation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NationApplicationPolicyTest {
    @Test
    void applicationNeverJoinsWithoutManagerApproval() {
        assertEquals(NationApplicationPolicy.Decision.ALLOW_APPLY,
                NationApplicationPolicy.apply(false, true, false));
        assertEquals(NationApplicationPolicy.Decision.NO_PERMISSION,
                NationApplicationPolicy.decide(false, true, false, true));
        assertEquals(NationApplicationPolicy.Decision.ALLOW_APPROVE,
                NationApplicationPolicy.decide(true, true, false, true));
    }

    @Test
    void limitsApplicantToOnePendingNation() {
        assertEquals(NationApplicationPolicy.Decision.ALREADY_APPLIED,
                NationApplicationPolicy.apply(false, true, true));
        assertEquals(NationApplicationPolicy.Decision.NATION_NOT_FOUND,
                NationApplicationPolicy.apply(false, false, false));
    }

    @Test
    void managerCanRejectWithoutAddingMembership() {
        assertEquals(NationApplicationPolicy.Decision.ALLOW_REJECT,
                NationApplicationPolicy.decide(true, true, false, false));
        assertEquals(NationApplicationPolicy.Decision.APPLICATION_NOT_FOUND,
                NationApplicationPolicy.decide(true, false, false, true));
    }
}
