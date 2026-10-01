package com.ruskserver.moveearth_addtional.s2.nation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NationNameplateRevisionTest {
    @Test
    void sourceChangesThatLeaveNameplatesEqualKeepTheRevision() {
        NameplateRevisionTracker<Map<UUID, String>> tracker = new NameplateRevisionTracker<>();
        UUID player = UUID.randomUUID();
        Map<UUID, String> state = new HashMap<>(Map.of(player, "[A]"));
        long first = tracker.revision(1L, () -> Map.copyOf(state));

        // e.g. a role save or a join application: the nation revision moves, nameplates do not.
        assertEquals(first, tracker.revision(2L, () -> Map.copyOf(state)));
        assertEquals(first, tracker.revision(3L, () -> Map.copyOf(state)));

        state.put(player, "[B]");
        long renamed = tracker.revision(4L, () -> Map.copyOf(state));
        assertNotEquals(first, renamed);
        state.put(UUID.randomUUID(), "[B]");
        assertNotEquals(renamed, tracker.revision(5L, () -> Map.copyOf(state)), "a new member shows up");
    }

    @Test
    void theStateIsRebuiltAtMostOncePerSourceRevision() {
        NameplateRevisionTracker<String> tracker = new NameplateRevisionTracker<>();
        AtomicInteger builds = new AtomicInteger();
        for (int i = 0; i < 10; i++) tracker.revision(7L, () -> { builds.incrementAndGet(); return "s"; });
        assertEquals(1, builds.get());
        tracker.revision(8L, () -> { builds.incrementAndGet(); return "s"; });
        assertEquals(2, builds.get());
    }

    @Test
    void aDeltaCarriesOnlyChangedEntriesAndPlayersWhoLeft() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        Map<UUID, String> sent = new HashMap<>(Map.of(a, "A/own", b, "B/neutral"));

        assertTrue(NameplateDelta.apply(sent, Map.of(a, "A/own", b, "B/neutral"), Set.of(a, b)).isEmpty(),
                "nothing changed, nothing is sent");

        var change = NameplateDelta.apply(sent, Map.of(b, "B/hostile", c, "C/none"), Set.of(b, c));
        assertEquals(Set.of("B/hostile", "C/none"), Set.copyOf(change.upserts()));
        assertEquals(List.of(a), change.removed());
        assertEquals(Map.of(b, "B/hostile", c, "C/none"), sent);
    }

    @Test
    void aLoginOnlyTouchesTheJoiningTarget() {
        UUID a = UUID.randomUUID();
        UUID joined = UUID.randomUUID();
        Map<UUID, String> sent = new HashMap<>(Map.of(a, "A"));
        var change = NameplateDelta.apply(sent, Map.of(joined, "J"), Set.of(a, joined));
        assertEquals(List.of("J"), change.upserts());
        assertTrue(change.removed().isEmpty());
        assertEquals(Map.of(a, "A", joined, "J"), sent);
    }

    @Test
    void largeUpdatesAreSplitAndOnlyTheFirstChunkReplaces() {
        List<Integer> entries = new ArrayList<>();
        for (int i = 0; i < 1_025; i++) entries.add(i);
        var chunks = NameplateDelta.chunks(true, entries, List.of(UUID.randomUUID()), 512);
        assertEquals(3, chunks.size());
        assertTrue(chunks.getFirst().replace());
        assertFalse(chunks.get(1).replace());
        assertEquals(1, chunks.getFirst().removed().size());
        assertEquals(entries.size(), chunks.stream().mapToInt(chunk -> chunk.entries().size()).sum());
        assertEquals(1, NameplateDelta.chunks(false, List.of(), List.of(), 512).size());
    }
}
