package com.ruskserver.moveearth_addtional.s2.territory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TerritoryMapProjectionTest {
    @Test
    void projectsChunkAreaAtScaleZero() {
        var rect = TerritoryMapProjection.projectChunks(0, 0, 0, 0, 0, 0);
        assertTrue(rect.visible());
        assertEquals(64.0F, rect.minX());
        assertEquals(64.0F, rect.minY());
        assertEquals(80.0F, rect.maxX());
        assertEquals(80.0F, rect.maxY());
    }

    @Test
    void clipsPartiallyVisibleTerritoryToMapPixels() {
        var rect = TerritoryMapProjection.projectChunks(0, 0, 0, -5, 0, 1);
        assertTrue(rect.visible());
        assertEquals(0.0F, rect.minX());
        assertEquals(16.0F, rect.maxX());
    }

    @Test
    void rejectsTerritoryOutsideMap() {
        assertFalse(TerritoryMapProjection.projectChunks(0, 0, 0, 20, 20, 1).visible());
    }

    @Test
    void scaleChangesBlocksPerPixel() {
        var point = TerritoryMapProjection.projectBlock(0, 0, 2, 64, -32);
        assertEquals(80.0F, point.x());
        assertEquals(56.0F, point.y());
    }

    @Test
    void blockAreasGrowToTheMinimumAndClipToTheMap() {
        var exact = TerritoryMapProjection.projectBlocks(0, 0, 0, 0, 0, 37, 17, 2.6F);
        org.junit.jupiter.api.Assertions.assertEquals(64.0F, exact.minX(), 1.0E-4F);
        org.junit.jupiter.api.Assertions.assertEquals(101.0F, exact.maxX(), 1.0E-4F);
        org.junit.jupiter.api.Assertions.assertTrue(exact.visible());
        var coarse = TerritoryMapProjection.projectBlocks(0, 0, 4, 0, 0, 37, 17, 2.6F);
        org.junit.jupiter.api.Assertions.assertTrue(coarse.maxY() - coarse.minY() >= 2.59F);
        var edge = TerritoryMapProjection.projectBlocks(0, 0, 0, 60, 0, 97, 17, 2.6F);
        org.junit.jupiter.api.Assertions.assertEquals(128.0F, edge.maxX(), 1.0E-4F);
        var outside = TerritoryMapProjection.projectBlocks(0, 0, 0, 500, 500, 537, 517, 2.6F);
        org.junit.jupiter.api.Assertions.assertFalse(outside.visible());
    }
}
