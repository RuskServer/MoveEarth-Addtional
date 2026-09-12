package com.ruskserver.moveearth_addtional.s2;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class S2NationSnapshotTest {
    @Test
    void permissionMaskRoundTrips() {
        var permissions = EnumSet.of(S2Permission.MANAGE_MEMBERS, S2Permission.MANAGE_TERRITORY);
        assertEquals(permissions, S2Permission.fromMask(S2Permission.toMask(permissions)));
    }

    @Test
    void unaffiliatedSnapshotHasSafeEmptyCollections() {
        S2NationSnapshot snapshot = S2NationSnapshot.unaffiliated("Rusk", false);
        assertFalse(snapshot.member());
        assertEquals("Rusk", snapshot.playerName());
        assertTrue(snapshot.members().isEmpty());
        assertTrue(snapshot.roles().isEmpty());
        assertTrue(snapshot.invitations().isEmpty());
        assertTrue(snapshot.inviteCandidates().isEmpty());
    }

    @Test
    void snapshotCopiesListsAndServerAdminOverridesNationPermissions() {
        var mutable = new java.util.ArrayList<S2NationSnapshot.MemberView>();
        mutable.add(new S2NationSnapshot.MemberView(
                java.util.UUID.randomUUID(), "Rusk", "owner", "Owner", true, 1234L));
        S2NationSnapshot snapshot = new S2NationSnapshot(1, "Rusk", true, true,
                "Test", "TST", "Owner", 0, 1, 1, 2, 1, 10,
                "NONE", mutable, List.of(), List.of(), List.of(), List.of());
        mutable.clear();
        assertEquals(1, snapshot.members().size());
        assertEquals(1234L, snapshot.members().getFirst().lastSeenAt());
        assertTrue(snapshot.can(S2Permission.MANAGE_ROLES));
    }

    @Test
    void snapshotCopiesInvitationAndCandidateViews() {
        var nationId = java.util.UUID.randomUUID();
        var candidateId = java.util.UUID.randomUUID();
        var invitations = new java.util.ArrayList<S2NationSnapshot.InvitationView>();
        var candidates = new java.util.ArrayList<S2NationSnapshot.CandidateView>();
        invitations.add(new S2NationSnapshot.InvitationView(nationId, "Green Nation", "GRN"));
        candidates.add(new S2NationSnapshot.CandidateView(candidateId, "Alex"));

        S2NationSnapshot snapshot = new S2NationSnapshot(2, "Rusk", false, false,
                "", "", "", 0, 0, 0, 0, 0, 0,
                "NONE", List.of(), List.of(), List.of(), invitations, candidates);
        invitations.clear();
        candidates.clear();

        assertEquals(nationId, snapshot.invitations().getFirst().nationId());
        assertEquals("GRN", snapshot.invitations().getFirst().nationTag());
        assertEquals(candidateId, snapshot.inviteCandidates().getFirst().id());
    }

    @Test
    void invalidTabIdFallsBackToOverview() {
        assertEquals(S2HubTab.OVERVIEW, S2HubTab.fromNetworkId(999));
    }

    @Test
    void snapshotCopiesDiplomacyViews() {
        var nationId = java.util.UUID.randomUUID();
        var diplomacy = new java.util.ArrayList<S2NationSnapshot.DiplomacyView>();
        diplomacy.add(new S2NationSnapshot.DiplomacyView(nationId, "Blue Nation", "BLU",
                S2NationSnapshot.DiplomacyState.HOSTILE, false));
        S2NationSnapshot snapshot = new S2NationSnapshot(3, "Rusk", false, true,
                "Green Nation", "GRN", "Member", S2Permission.BASTION_ACCESS.mask(),
                1, 1, 9, 1, 2, "NONE", List.of(), List.of(), diplomacy, List.of(), List.of());
        diplomacy.clear();

        assertEquals(1, snapshot.diplomacy().size());
        assertEquals(nationId, snapshot.diplomacy().getFirst().nationId());
        assertFalse(snapshot.diplomacy().getFirst().hostileByViewer());
    }
}
