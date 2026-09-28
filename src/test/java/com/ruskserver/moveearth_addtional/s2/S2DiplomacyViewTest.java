package com.ruskserver.moveearth_addtional.s2;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S2DiplomacyViewTest {
    @Test void legacyConstructorHasNoPendingEndOrGrants() {
        var view = new S2NationSnapshot.DiplomacyView(UUID.randomUUID(), "Blue", "BLU",
                S2NationSnapshot.DiplomacyState.ALLIED, false);
        assertFalse(view.allianceEnding());
        assertEquals(0, view.grantMask());
        assertTrue(view.allyMembers().isEmpty());
    }

    @Test void pendingEndOnlyShowsForAlliancesAndMembersAreCopied() {
        var members = new java.util.ArrayList<S2NationSnapshot.AllyMemberView>();
        members.add(new S2NationSnapshot.AllyMemberView(null, null, 1));
        var allied = new S2NationSnapshot.DiplomacyView(UUID.randomUUID(), "Blue", "BLU",
                S2NationSnapshot.DiplomacyState.ALLIED, false, 1_200L, true, 3, members);
        members.clear();
        assertTrue(allied.allianceEnding());
        assertEquals(1, allied.allyMembers().size());
        assertEquals("", allied.allyMembers().getFirst().name());

        var neutral = new S2NationSnapshot.DiplomacyView(UUID.randomUUID(), "Red", "RED",
                S2NationSnapshot.DiplomacyState.NEUTRAL, false, -5L, false, 0, List.of());
        assertFalse(neutral.allianceEnding());
        assertEquals(0L, neutral.allianceEndRemainingTicks());
    }
}
