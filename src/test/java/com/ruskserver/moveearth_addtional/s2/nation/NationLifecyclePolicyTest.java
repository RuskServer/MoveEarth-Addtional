package com.ruskserver.moveearth_addtional.s2.nation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NationLifecyclePolicyTest {
    @Test
    void transferRequiresOwnerMemberTargetAndNoSiege() {
        assertEquals(NationLifecyclePolicy.Decision.ALLOWED,
                NationLifecyclePolicy.transfer(true, true, false, false));
        assertEquals(NationLifecyclePolicy.Decision.OWNER_ONLY,
                NationLifecyclePolicy.transfer(false, true, false, false));
        assertEquals(NationLifecyclePolicy.Decision.SIEGE_LOCKED,
                NationLifecyclePolicy.transfer(true, true, false, true));
        assertEquals(NationLifecyclePolicy.Decision.TARGET_NOT_MEMBER,
                NationLifecyclePolicy.transfer(true, false, false, false));
        assertEquals(NationLifecyclePolicy.Decision.TARGET_IS_OWNER,
                NationLifecyclePolicy.transfer(true, true, true, false));
    }

    @Test
    void disbandRejectsSiegeAndPrisonerOrphans() {
        assertEquals(NationLifecyclePolicy.Decision.ALLOWED,
                NationLifecyclePolicy.disband(true, false, false, false, false));
        assertEquals(NationLifecyclePolicy.Decision.OWNER_ONLY,
                NationLifecyclePolicy.disband(false, false, false, false, false));
        assertEquals(NationLifecyclePolicy.Decision.SIEGE_LOCKED,
                NationLifecyclePolicy.disband(true, true, false, false, false));
        assertEquals(NationLifecyclePolicy.Decision.PRISONERS_EXIST,
                NationLifecyclePolicy.disband(true, false, true, false, false));
    }

    @Test
    void disbandCannotShedAnAllianceOrPeaceTruce() {
        // An alliance under termination notice is still an alliance: it must run out first.
        assertEquals(NationLifecyclePolicy.Decision.ALLIANCE_ACTIVE,
                NationLifecyclePolicy.disband(true, false, false, true, false));
        assertEquals(NationLifecyclePolicy.Decision.PEACE_TRUCE_ACTIVE,
                NationLifecyclePolicy.disband(true, false, false, false, true));
        assertEquals(NationLifecyclePolicy.Decision.OWNER_ONLY,
                NationLifecyclePolicy.disband(false, false, false, true, true));
    }
}
