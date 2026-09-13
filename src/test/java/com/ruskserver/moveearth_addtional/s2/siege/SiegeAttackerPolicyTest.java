package com.ruskserver.moveearth_addtional.s2.siege;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiegeAttackerPolicyTest {
    @Test
    void nationMemberUsesNationIdentity() {
        UUID nation = UUID.randomUUID();
        var identity = SiegeAttackerPolicy.resolve(nation, UUID.randomUUID(), false);
        assertEquals(nation, identity.id());
        assertFalse(identity.individual());
    }

    @Test
    void unaffiliatedPlayerUsesIndividualIdentity() {
        UUID player = UUID.randomUUID();
        var identity = SiegeAttackerPolicy.resolve(null, player, false);
        assertEquals(player, identity.id());
        assertTrue(identity.individual());
    }

    @Test
    void joiningNationDoesNotReplaceAnActiveIndividualIdentity() {
        UUID player = UUID.randomUUID();
        var identity = SiegeAttackerPolicy.resolve(UUID.randomUUID(), player, true);
        assertEquals(player, identity.id());
        assertTrue(identity.individual());
    }

    @Test
    void unattributableAutomationHasNoSiegeIdentity() {
        assertNull(SiegeAttackerPolicy.resolve(null, null, false));
    }
}
