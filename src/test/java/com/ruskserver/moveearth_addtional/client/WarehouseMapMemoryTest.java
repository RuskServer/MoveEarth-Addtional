package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.WarehouseMapMemory.Site;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarehouseMapMemoryTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final Site NEAR = new Site(OVERWORLD, 100, 64, 100);
    private static final Site FAR = new Site(OVERWORLD, 2_000, 64, 2_000);

    @Test
    void remembersReportedSitesAndKeepsThemOutOfRange() {
        WarehouseMapMemory memory = new WarehouseMapMemory();
        assertTrue(memory.reconcile(OVERWORLD, List.of(NEAR), 90, 90, 200));
        // Walking away: the server stops reporting it, but it is far from the player now.
        assertFalse(memory.reconcile(OVERWORLD, List.of(), 1_000, 1_000, 200));
        assertEquals(List.of(NEAR), memory.in(OVERWORLD));
    }

    @Test
    void forgetsASiteThatIsNoLongerReportedWhereItShouldBe() {
        WarehouseMapMemory memory = new WarehouseMapMemory();
        memory.reconcile(OVERWORLD, List.of(NEAR, FAR), 90, 90, 200);
        assertTrue(memory.reconcile(OVERWORLD, List.of(), 110, 110, 200));
        assertEquals(List.of(FAR), memory.in(OVERWORLD));
    }

    @Test
    void otherDimensionsAreLeftAlone() {
        WarehouseMapMemory memory = new WarehouseMapMemory();
        Site nether = new Site("minecraft:the_nether", 100, 64, 100);
        memory.reconcile("minecraft:the_nether", List.of(nether), 100, 100, 200);
        memory.reconcile(OVERWORLD, List.of(), 100, 100, 200);
        assertEquals(List.of(nether), memory.in("minecraft:the_nether"));
        assertTrue(memory.in(OVERWORLD).isEmpty());
    }

    @Test
    void roundTripsThroughTextAndSkipsBrokenLines() {
        WarehouseMapMemory memory = new WarehouseMapMemory();
        memory.reconcile(OVERWORLD, List.of(NEAR, FAR), 0, 0, 0);
        String text = memory.serialize() + "garbage line\nminecraft:overworld x 1 2\n";
        WarehouseMapMemory restored = WarehouseMapMemory.parse(text);
        assertEquals(2, restored.size());
        assertEquals(memory.in(OVERWORLD), restored.in(OVERWORLD));
    }
}
