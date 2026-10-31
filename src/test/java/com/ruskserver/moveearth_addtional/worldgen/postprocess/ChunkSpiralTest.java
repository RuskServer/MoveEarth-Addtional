package com.ruskserver.moveearth_addtional.worldgen.postprocess;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkSpiralTest {
    @Test
    void theWalkCoversTheSquareExactlyOnceRingByRing() {
        int radius = 12;
        Set<Long> seen = new HashSet<>();
        int lastRing = 0;
        for (long index = 0; index < ChunkSpiral.total(radius); index++) {
            int[] offset = ChunkSpiral.offset(index);
            int ring = Math.max(Math.abs(offset[0]), Math.abs(offset[1]));
            assertTrue(ring <= radius, "inside the square");
            assertTrue(ring >= lastRing, "never goes back inwards");
            lastRing = ring;
            assertTrue(seen.add(((long) offset[0] << 32) ^ (offset[1] & 0xFFFFFFFFL)), "no chunk twice");
        }
        assertEquals((2 * radius + 1) * (2 * radius + 1), seen.size());
    }

    @Test
    void itStartsAtTheCentre() {
        assertArrayEquals(new int[] {0, 0}, ChunkSpiral.offset(0));
        assertEquals(1L, ChunkSpiral.total(0));
    }

    @Test
    void largeIndicesStayExact() {
        int radius = 2500;
        long last = ChunkSpiral.total(radius) - 1;
        int[] offset = ChunkSpiral.offset(last);
        assertEquals(radius, Math.max(Math.abs(offset[0]), Math.abs(offset[1])));
    }
}
