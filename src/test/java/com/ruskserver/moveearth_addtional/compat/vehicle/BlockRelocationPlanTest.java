package com.ruskserver.moveearth_addtional.compat.vehicle;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockRelocationPlanTest {
    /** A 2D quarter turn around the origin, like Sable's rotated disassembly transform. */
    private static final UnaryOperator<List<Integer>> QUARTER_TURN = p -> List.of(-p.get(1), p.get(0));

    private static <P, E> void apply(BlockRelocationPlan<P, E> plan, Map<P, E> store) {
        plan.apply(store::remove, store::put);
    }

    @Test
    void plotToWorldMoveCarriesEntriesAndLeavesNothingAtThePlot() {
        // Disassembly: plot coordinates 1_000_000.. back to world coordinates.
        Map<Integer, String> store = new HashMap<>(Map.of(1_000_001, "iron", 1_000_003, "diamond", 7, "unrelated"));
        var plan = BlockRelocationPlan.<Integer, String>of(List.of(1_000_001, 1_000_002, 1_000_003),
                p -> p - 1_000_000, store::get);
        apply(plan, store);
        assertEquals(Map.of(1, "iron", 3, "diamond", 7, "unrelated"), store);
    }

    @Test
    void worldToPlotAndBackRestoresTheSameEntries() {
        Map<Integer, String> store = new HashMap<>(Map.of(10, "copper", 11, "gold"));
        List<Integer> world = List.of(10, 11, 12);
        apply(BlockRelocationPlan.<Integer, String>of(world, p -> p + 5_000, store::get), store);
        assertEquals(Map.of(5_010, "copper", 5_011, "gold"), store);
        apply(BlockRelocationPlan.<Integer, String>of(List.of(5_010, 5_011, 5_012), p -> p - 5_000, store::get), store);
        assertEquals(Map.of(10, "copper", 11, "gold"), store);
    }

    @Test
    void overlappingSourcesAndDestinationsNeverOverwriteAnEntryStillToMove() {
        // Shift by one along a row: every destination but the last is itself a source.
        Map<Integer, String> store = new HashMap<>(Map.of(0, "a", 1, "b", 2, "c"));
        apply(BlockRelocationPlan.<Integer, String>of(List.of(0, 1, 2), p -> p + 1, store::get), store);
        assertEquals(Map.of(1, "a", 2, "b", 3, "c"), store);
    }

    @Test
    void rotationInPlaceFollowsTheTransform() {
        Map<List<Integer>, String> store = new HashMap<>(Map.of(
                List.of(1, 0), "east", List.of(0, 1), "south", List.of(-1, 0), "west"));
        var plan = BlockRelocationPlan.<List<Integer>, String>of(store.keySet().stream().toList(), QUARTER_TURN,
                store::get);
        apply(plan, store);
        assertEquals(Map.of(List.of(0, 1), "east", List.of(-1, 0), "south", List.of(0, -1), "west"), store);
    }

    @Test
    void positionsWithoutMetadataAndDuplicatesAreSkipped() {
        Map<Integer, String> store = new HashMap<>(Map.of(4, "cobble"));
        var plan = BlockRelocationPlan.<Integer, String>of(List.of(3, 4, 4, 5), p -> p * 10, store::get);
        assertEquals(1, plan.moves().size());
        assertEquals(new BlockRelocationPlan.Move<>(4, 40, "cobble"), plan.moves().getFirst());
        assertTrue(BlockRelocationPlan.<Integer, String>of(List.of(1, 2), p -> p, store::get).isEmpty());
        assertFalse(plan.isEmpty());
    }
}
