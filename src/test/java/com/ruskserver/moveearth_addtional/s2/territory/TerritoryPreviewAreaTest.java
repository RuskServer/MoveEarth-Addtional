package com.ruskserver.moveearth_addtional.s2.territory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TerritoryPreviewAreaTest {
    @Test
    void maximumRadiusProducesNineByNineChunks() {
        TerritoryPreviewArea area = new TerritoryPreviewArea(10, -3, 4);
        assertEquals(9, area.diameterChunks());
        assertEquals(81, area.chunkCount());
        assertEquals(6, area.minChunkX());
        assertEquals(14, area.maxChunkX());
    }

    @Test
    void convertsNegativeChunksToExclusiveBlockBounds() {
        TerritoryPreviewArea area = new TerritoryPreviewArea(-2, -1, 1);
        assertEquals(-48, area.minBlockX());
        assertEquals(0, area.maxBlockXExclusive());
        assertEquals(-32, area.minBlockZ());
        assertEquals(16, area.maxBlockZExclusive());
    }

    @Test
    void rejectsRadiusOutsideDesignLimit() {
        assertThrows(IllegalArgumentException.class, () -> new TerritoryPreviewArea(0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new TerritoryPreviewArea(0, 0, 5));
    }

    @Test
    void detectsOverlapIncludingSharedEdgeChunk() {
        TerritoryPreviewArea first = new TerritoryPreviewArea(0, 0, 1);
        assertTrue(first.overlaps(new TerritoryPreviewArea(2, 0, 1)));
        assertFalse(first.overlaps(new TerritoryPreviewArea(3, 0, 1)));
    }

    @Test
    void containsOnlyChunksInsideSquareRadius() {
        TerritoryPreviewArea area = new TerritoryPreviewArea(10, -4, 2);
        assertTrue(area.containsChunk(8, -6));
        assertTrue(area.containsChunk(12, -2));
        assertFalse(area.containsChunk(13, -2));
    }
}
