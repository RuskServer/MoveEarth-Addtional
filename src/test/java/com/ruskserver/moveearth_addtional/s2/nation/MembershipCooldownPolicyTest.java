package com.ruskserver.moveearth_addtional.s2.nation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MembershipCooldownPolicyTest {
    private static final UUID CURRENT = UUID.randomUUID();
    private static final UUID FORMER = UUID.randomUUID();
    private static final UUID DEFENDER = UUID.randomUUID();

    @Test void cooldownLastsFourHoursOfOpenTime() {
        assertEquals(288_000L, MembershipCooldownPolicy.COOLDOWN_OPEN_TICKS);
        long endsAt = MembershipCooldownPolicy.endsAt(1_000L);
        assertTrue(MembershipCooldownPolicy.active(endsAt, 1_000L));
        assertEquals(288_000L, MembershipCooldownPolicy.remaining(endsAt, 1_000L));
        assertTrue(MembershipCooldownPolicy.active(endsAt, endsAt - 1L));
        assertFalse(MembershipCooldownPolicy.active(endsAt, endsAt));
        assertEquals(0L, MembershipCooldownPolicy.remaining(0L, 50L));
    }

    @Test void remainingMinutesRoundUp() {
        assertEquals(0L, MembershipCooldownPolicy.remainingMinutes(0L));
        assertEquals(1L, MembershipCooldownPolicy.remainingMinutes(1L));
        assertEquals(1L, MembershipCooldownPolicy.remainingMinutes(1_200L));
        assertEquals(2L, MembershipCooldownPolicy.remainingMinutes(1_201L));
        assertEquals(240L, MembershipCooldownPolicy.remainingMinutes(MembershipCooldownPolicy.COOLDOWN_OPEN_TICKS));
    }

    @Test void ceasefireFollowsCurrentNationFirst() {
        assertEquals(CURRENT, MembershipCooldownPolicy.ceasefireNation(CURRENT, FORMER, true, true));
    }

    @Test void nationlessPlayerStaysBoundToFormerNationDuringCooldown() {
        assertEquals(FORMER, MembershipCooldownPolicy.ceasefireNation(null, FORMER, true, true));
        assertNull(MembershipCooldownPolicy.ceasefireNation(null, FORMER, false, true));
        assertNull(MembershipCooldownPolicy.ceasefireNation(null, FORMER, true, false));
        assertNull(MembershipCooldownPolicy.ceasefireNation(null, null, true, true));
    }

    @Test void formerNationBlocksAttacksOnItsAlliesAndTrucePartners() {
        assertTrue(MembershipCooldownPolicy.formerNationBlocks(FORMER, DEFENDER, true, false));
        assertTrue(MembershipCooldownPolicy.formerNationBlocks(FORMER, DEFENDER, false, true));
        assertFalse(MembershipCooldownPolicy.formerNationBlocks(FORMER, DEFENDER, false, false));
        assertFalse(MembershipCooldownPolicy.formerNationBlocks(null, DEFENDER, true, true));
        assertFalse(MembershipCooldownPolicy.formerNationBlocks(FORMER, FORMER, true, true));
    }

    private static final long NOW = 10_000L;

    private static MembershipCooldownPolicy.Entry kick(UUID nation, long at) {
        return new MembershipCooldownPolicy.Entry(nation, MembershipCooldownPolicy.endsAt(at), false);
    }

    private static MembershipCooldownPolicy.Entry leave(UUID nation, long at) {
        return new MembershipCooldownPolicy.Entry(nation, MembershipCooldownPolicy.endsAt(at), true);
    }

    @Test void laterLossNeverReplacesAnEarlierRunningBinding() {
        UUID throwaway = UUID.randomUUID();
        // Kicked from FORMER, then found and at once disband a throwaway nation.
        List<MembershipCooldownPolicy.Entry> entries = MembershipCooldownPolicy.record(
                MembershipCooldownPolicy.record(List.of(), kick(FORMER, NOW), NOW), leave(throwaway, NOW + 20L), NOW + 20L);
        assertEquals(2, entries.size());
        // The throwaway nation is gone; the binding still points at FORMER.
        assertEquals(Optional.of(FORMER), MembershipCooldownPolicy.binding(entries, NOW + 40L, Set.of(FORMER)::contains)
                .map(MembershipCooldownPolicy.Entry::formerNationId));
        // Even if both still existed, the earliest binding is the one that applies.
        assertEquals(FORMER, MembershipCooldownPolicy.binding(entries, NOW + 40L, id -> true).orElseThrow().formerNationId());
        assertEquals(MembershipCooldownPolicy.endsAt(NOW) - (NOW + 40L),
                MembershipCooldownPolicy.bindingRemaining(entries, NOW + 40L, id -> true));
    }

    @Test void aSecondKickDoesNotShortenOrMoveTheBinding() {
        UUID second = UUID.randomUUID();
        List<MembershipCooldownPolicy.Entry> entries = MembershipCooldownPolicy.record(
                MembershipCooldownPolicy.record(List.of(), kick(FORMER, NOW), NOW), kick(second, NOW + 100L), NOW + 100L);
        assertEquals(FORMER, MembershipCooldownPolicy.binding(entries, NOW + 200L, id -> true).orElseThrow().formerNationId());
        // Once the first binding runs out, the later one takes over for the rest of its time.
        long afterFirst = MembershipCooldownPolicy.endsAt(NOW);
        assertEquals(second, MembershipCooldownPolicy.binding(entries, afterFirst, id -> true).orElseThrow().formerNationId());
        assertEquals(100L, MembershipCooldownPolicy.bindingRemaining(entries, afterFirst, id -> true));
    }

    @Test void expiredEntriesAreDroppedWhenRecording() {
        long later = MembershipCooldownPolicy.endsAt(NOW);
        List<MembershipCooldownPolicy.Entry> entries = MembershipCooldownPolicy.record(
                List.of(kick(FORMER, NOW)), kick(DEFENDER, later), later);
        assertEquals(List.of(kick(DEFENDER, later)), entries);
        assertTrue(MembershipCooldownPolicy.record(entries, null, MembershipCooldownPolicy.endsAt(later)).isEmpty());
    }

    @Test void entryCapKeepsTheBindingAndTheNewestLock() {
        List<MembershipCooldownPolicy.Entry> entries = List.of();
        for (int index = 0; index <= MembershipCooldownPolicy.MAX_ENTRIES + 3; index++) {
            entries = MembershipCooldownPolicy.record(entries, leave(UUID.randomUUID(), NOW + index), NOW + index);
        }
        MembershipCooldownPolicy.Entry newest = leave(FORMER, NOW + 100L);
        entries = MembershipCooldownPolicy.record(entries, newest, NOW + 100L);
        assertEquals(MembershipCooldownPolicy.MAX_ENTRIES, entries.size());
        assertEquals(MembershipCooldownPolicy.endsAt(NOW), entries.getFirst().endsAt());
        assertEquals(newest, entries.getLast());
    }

    @Test void joinLockFollowsTheLongestLeaveNotKicks() {
        List<MembershipCooldownPolicy.Entry> kicked = List.of(kick(FORMER, NOW));
        assertEquals(0L, MembershipCooldownPolicy.joinLockRemaining(kicked, NOW + 1L));
        List<MembershipCooldownPolicy.Entry> both = MembershipCooldownPolicy.record(kicked, leave(DEFENDER, NOW + 50L), NOW + 50L);
        assertEquals(MembershipCooldownPolicy.COOLDOWN_OPEN_TICKS, MembershipCooldownPolicy.joinLockRemaining(both, NOW + 50L));
        assertEquals(0L, MembershipCooldownPolicy.joinLockRemaining(null, NOW));
    }

    @Test void aBoundKickedPlayerCannotFoundANation() {
        List<MembershipCooldownPolicy.Entry> kicked = List.of(kick(FORMER, NOW));
        assertEquals(MembershipCooldownPolicy.FoundingDecision.FORMER_NATION_BOUND,
                MembershipCooldownPolicy.founding(kicked, NOW + 1L, id -> true));
        // The binding ends with the former nation, or when its time runs out.
        assertEquals(MembershipCooldownPolicy.FoundingDecision.ALLOWED,
                MembershipCooldownPolicy.founding(kicked, NOW + 1L, id -> false));
        assertEquals(MembershipCooldownPolicy.FoundingDecision.ALLOWED,
                MembershipCooldownPolicy.founding(kicked, MembershipCooldownPolicy.endsAt(NOW), id -> true));
        assertEquals(MembershipCooldownPolicy.FoundingDecision.MEMBERSHIP_COOLDOWN,
                MembershipCooldownPolicy.founding(List.of(leave(FORMER, NOW)), NOW + 1L, id -> false));
        assertEquals(MembershipCooldownPolicy.FoundingDecision.ALLOWED,
                MembershipCooldownPolicy.founding(List.of(), NOW, id -> true));
    }

    @Test void disbandLocksOnlyTheOwner() {
        var owner = MembershipCooldownPolicy.onDisband(true, FORMER, NOW);
        assertTrue(owner.isPresent());
        assertTrue(owner.get().joinLocked());
        assertEquals(MembershipCooldownPolicy.COOLDOWN_OPEN_TICKS,
                MembershipCooldownPolicy.joinLockRemaining(List.of(owner.get()), NOW));
        assertEquals(MembershipCooldownPolicy.FoundingDecision.MEMBERSHIP_COOLDOWN,
                MembershipCooldownPolicy.founding(List.of(owner.get()), NOW + 1L, id -> false));
        // An ordinary member chose nothing: no lock, no binding to a nation that is gone.
        assertTrue(MembershipCooldownPolicy.onDisband(false, FORMER, NOW).isEmpty());
    }

    @Test void disbandKeepsAMembersEarlierBinding() {
        // Kicked from FORMER, joined another nation at once, which was then disbanded.
        List<MembershipCooldownPolicy.Entry> entries = List.of(kick(FORMER, NOW));
        var added = MembershipCooldownPolicy.onDisband(false, DEFENDER, NOW + 50L).orElse(null);
        entries = MembershipCooldownPolicy.record(entries, added, NOW + 50L);
        assertEquals(List.of(kick(FORMER, NOW)), entries);
        assertEquals(0L, MembershipCooldownPolicy.joinLockRemaining(entries, NOW + 50L));
        assertEquals(MembershipCooldownPolicy.FoundingDecision.FORMER_NATION_BOUND,
                MembershipCooldownPolicy.founding(entries, NOW + 50L, Set.of(FORMER)::contains));
    }

    @Test void restrictionToReportPrefersTheJoinLock() {
        assertEquals(MembershipCooldownPolicy.Restriction.NONE, MembershipCooldownPolicy.restriction(0L, 0L));
        assertEquals(MembershipCooldownPolicy.Restriction.FOUNDING_BOUND, MembershipCooldownPolicy.restriction(0L, 5L));
        assertEquals(MembershipCooldownPolicy.Restriction.JOIN_LOCKED, MembershipCooldownPolicy.restriction(5L, 5L));
        assertEquals(MembershipCooldownPolicy.Restriction.JOIN_LOCKED, MembershipCooldownPolicy.restriction(5L, 0L));
    }
}
