package com.ruskserver.moveearth_addtional.s2.reinforcement;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReinforcementPackedChunkTest {
    private static Map<Long, ReinforcementEntry> sample() {
        Map<Long, ReinforcementEntry> rows = new LinkedHashMap<>();
        rows.put(1L, ReinforcementEntry.full(ReinforcementMaterial.DIAMOND));
        rows.put(-2L, new ReinforcementEntry(ReinforcementMaterial.IRON, 17, true));
        rows.put(Long.MAX_VALUE, ReinforcementEntry.pending(ReinforcementMaterial.COPPER, 1_000L));
        rows.put(Long.MIN_VALUE, ReinforcementEntry.pending(ReinforcementMaterial.GOLD, 5_000L, 9_000L)
                .advance(9_000L + ReinforcementEntry.ACTIVATION_DELAY_TICKS + 40L));
        rows.put(42L, new ReinforcementEntry(ReinforcementMaterial.COBBLESTONE, 0, false));
        return rows;
    }

    private static ReinforcementPackedChunk.Columns encode(Map<Long, ReinforcementEntry> rows,
                                                           ReinforcementPackedChunk.Palette palette) {
        ReinforcementPackedChunk.Writer writer = new ReinforcementPackedChunk.Writer(palette, 1);
        rows.forEach(writer::add);
        return writer.finish();
    }

    private static Map<Long, ReinforcementEntry> decode(ReinforcementPackedChunk.Columns columns,
                                                        ReinforcementPackedChunk.Palette palette) {
        Map<Long, ReinforcementEntry> decoded = new LinkedHashMap<>();
        ReinforcementPackedChunk.decode(columns, palette, decoded::put);
        return decoded;
    }

    @Test
    void roundTripPreservesEveryField() {
        Map<Long, ReinforcementEntry> rows = sample();
        ReinforcementPackedChunk.Palette palette = ReinforcementPackedChunk.Palette.current();
        ReinforcementPackedChunk.Columns columns = encode(rows, palette);
        assertEquals(rows.size(), columns.size());
        // Only the two construction rows carry timers.
        assertEquals(2, columns.timerIndices().length);
        ReinforcementPackedChunk.Palette reread = ReinforcementPackedChunk.Palette.read(palette.ids());
        assertEquals(rows, decode(columns, reread));
    }

    @Test
    void paletteIdsSurviveMaterialReordering() {
        Map<Long, ReinforcementEntry> rows = sample();
        ReinforcementPackedChunk.Palette palette = ReinforcementPackedChunk.Palette.current();
        ReinforcementPackedChunk.Columns columns = encode(rows, palette);
        // A palette written by a build whose enum had another order maps indices through ids, not ordinals.
        List<String> reversed = new java.util.ArrayList<>(palette.ids());
        java.util.Collections.reverse(reversed);
        byte[] remapped = columns.materials().clone();
        for (int i = 0; i < remapped.length; i++) {
            remapped[i] = (byte) (reversed.size() - 1 - remapped[i]);
        }
        ReinforcementPackedChunk.Columns reordered = new ReinforcementPackedChunk.Columns(columns.positions(),
                remapped, columns.durability(), columns.flags(), columns.timerIndices(), columns.startedAt(),
                columns.activatesAt());
        assertEquals(rows, decode(reordered, ReinforcementPackedChunk.Palette.read(reversed)));
    }

    @Test
    void unknownMaterialFallsBackLikeLegacyLoader() {
        ReinforcementPackedChunk.Palette palette = ReinforcementPackedChunk.Palette.read(List.of("unobtainium"));
        Map<Long, ReinforcementEntry> decoded = decode(new ReinforcementPackedChunk.Columns(
                new long[]{7L}, new byte[]{0}, new int[]{20}, new byte[]{1}, null, null, null), palette);
        assertEquals(new ReinforcementEntry(ReinforcementMaterial.COBBLESTONE, 20, true), decoded.get(7L));
        assertEquals(ReinforcementMaterial.COBBLESTONE,
                ReinforcementPackedChunk.legacyEntry("unobtainium", 20, true, false, 0L, 0L).material());
    }

    @Test
    void damagedArraysLoadTheCompleteRowsOnly() {
        ReinforcementPackedChunk.Palette palette = ReinforcementPackedChunk.Palette.current();
        Map<Long, ReinforcementEntry> decoded = decode(new ReinforcementPackedChunk.Columns(
                new long[]{1L, 2L, 3L}, new byte[]{2, 2}, new int[]{5, 6, 7}, new byte[]{1, 1, 1},
                new int[]{99}, new long[]{1L}, new long[]{2L}), palette);
        assertEquals(2, decoded.size());
        assertEquals(0L, decoded.get(1L).activatesAt());
    }

    @Test
    void legacyEntriesMatchPackedRoundTrip() {
        // Version 1 stored each field by name; the same values must come back identically from version 2.
        Map<Long, ReinforcementEntry> rows = sample();
        Map<Long, ReinforcementEntry> legacy = new LinkedHashMap<>();
        rows.forEach((pos, entry) -> legacy.put(pos, ReinforcementPackedChunk.legacyEntry(entry.material().id(),
                entry.durability(), entry.enabled(), true, entry.constructionStartedAt(), entry.activatesAt())));
        assertEquals(rows, legacy);
        ReinforcementPackedChunk.Palette palette = ReinforcementPackedChunk.Palette.current();
        assertEquals(legacy, decode(encode(legacy, palette), palette));
        // Pre-construction-timer saves had no ActivatesAt tag: stale timer values are ignored.
        assertEquals(0L, ReinforcementPackedChunk.legacyEntry("iron", 10, true, false, 5L, 6L).activatesAt());
    }
}
