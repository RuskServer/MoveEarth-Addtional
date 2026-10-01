package com.ruskserver.moveearth_addtional.s2.siege;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkSquareIndexTest {
    private record Area(String dimension, int x, int z, int radius, int id) {
        boolean contains(String actual, int chunkX, int chunkZ) {
            return dimension.equals(actual) && Math.abs(chunkX - x) <= radius && Math.abs(chunkZ - z) <= radius;
        }
    }

    @Test
    void matchesForwardAndBackwardScansOverAllAreas() {
        Random random = new Random(20261002L);
        List<Area> areas = new ArrayList<>();
        ChunkSquareIndex<Integer> index = new ChunkSquareIndex<>();
        for (int id = 0; id < 400; id++) {
            String dimension = random.nextInt(4) == 0 ? "minecraft:the_nether" : "minecraft:overworld";
            // Mostly territory-sized squares, some spanning bucket edges and a few very wide ones.
            int radius = id % 97 == 0 ? 20_000 : random.nextInt(24);
            Area area = new Area(dimension, random.nextInt(400) - 200, random.nextInt(400) - 200, radius, id);
            areas.add(area);
            index.add(area.dimension(), area.x(), area.z(), area.radius(), id);
        }
        for (int probe = 0; probe < 20_000; probe++) {
            String dimension = random.nextBoolean() ? "minecraft:overworld" : "minecraft:the_nether";
            int x = random.nextInt(480) - 240;
            int z = random.nextInt(480) - 240;
            Integer first = null;
            for (Area area : areas) if (area.contains(dimension, x, z)) { first = area.id(); break; }
            Integer last = null;
            for (int i = areas.size() - 1; i >= 0; i--) if (areas.get(i).contains(dimension, x, z)) { last = areas.get(i).id(); break; }
            assertEquals(first, index.first(dimension, x, z));
            assertEquals(last, index.last(dimension, x, z));
        }
    }

    @Test
    void newestCoveringAreaWinsAndOtherDimensionsAreIgnored() {
        ChunkSquareIndex<String> index = new ChunkSquareIndex<>();
        assertTrue(index.isEmpty());
        index.add("overworld", 0, 0, 2, "old");
        index.add("overworld", 1, 1, 0, "new");
        index.add("nether", 1, 1, 5, "elsewhere");
        assertEquals("new", index.last("overworld", 1, 1));
        assertEquals("old", index.first("overworld", 1, 1));
        assertEquals("old", index.last("overworld", -2, 2));
        assertNull(index.last("overworld", 3, 0));
        assertNull(index.first("end", 1, 1));
        assertEquals(3, index.size());
    }

    @Test
    void negativeChunksAcrossBucketEdgesAreFound() {
        ChunkSquareIndex<String> index = new ChunkSquareIndex<>();
        index.add("overworld", -16, -1, 1, "edge");
        assertEquals("edge", index.last("overworld", -17, 0));
        assertEquals("edge", index.last("overworld", -15, -2));
        assertNull(index.last("overworld", -18, 0));
    }
}
