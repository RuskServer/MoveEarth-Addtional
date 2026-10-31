package com.ruskserver.moveearth_addtional.pvp.deploy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PvpTacticalMapTest {
    private static PvpTacticalMap sample(int width, int depth) {
        byte[] colors = new byte[width * depth];
        byte[] heights = new byte[width * depth];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = (byte) (i % 7 == 0 ? 0 : 1 + i % 60);
            heights[i] = (byte) (200 + i % 50);
        }
        return new PvpTacticalMap("arena", 42L, -100, 50, 2, width, depth, 60, colors, heights);
    }

    @Test
    void compressionRoundTripsEveryCell() {
        PvpTacticalMap map = sample(120, 80);
        byte[] data = map.compressed();
        assertTrue(data.length < 120 * 80 * 2);
        PvpTacticalMap back = PvpTacticalMap.fromCompressed("arena", 42L, -100, 50, 2, 120, 80, 60, data);
        assertArrayEquals(map.colors(), back.colors());
        assertArrayEquals(map.heights(), back.heights());
    }

    @Test
    void heightsAreUnsignedAboveTheBase() {
        PvpTacticalMap map = sample(4, 4);
        // 200 + 0 stored as a byte reads back as 200 above the base, not negative.
        assertEquals(60 + 200, map.height(0, 0));
        assertEquals(0, map.color(0, 0));
    }

    @Test
    void truncatedDataIsRejected() {
        byte[] data = sample(64, 64).compressed();
        byte[] cut = java.util.Arrays.copyOf(data, data.length / 3);
        assertThrows(IllegalArgumentException.class,
                () -> PvpTacticalMap.fromCompressed("arena", 1L, 0, 0, 1, 64, 64, 0, cut));
    }

    @Test
    void oversizedMapsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PvpTacticalMap.fromCompressed("arena", 1L, 0, 0, 1, PvpTacticalMap.MAX_CELLS + 1, 4, 0, new byte[0]));
    }
}
