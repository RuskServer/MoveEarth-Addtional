package com.ruskserver.moveearth_addtional.terrain;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TerrainLayerTest {
    private static TerrainLayer shorts(int size, int... values) {
        ByteBuffer buffer = ByteBuffer.allocate(size * size * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < values.length; i++) {
            buffer.putShort(i * 2, (short) values[i]);
        }
        return new TerrainLayer(buffer, TerrainLayer.Type.I2, size);
    }

    @Test
    void readsLittleEndianSignedShortsInRowMajorOrder() {
        // row index is world Z, column index is world X, matching the generator
        TerrainLayer layer = shorts(2, 10, 20, -30, 40);
        assertEquals(10.0, layer.at(0, 0));
        assertEquals(20.0, layer.at(1, 0));
        assertEquals(-30.0, layer.at(0, 1));
        assertEquals(40.0, layer.at(1, 1));
    }

    @Test
    void clampsInsteadOfWalkingOffTheTile() {
        TerrainLayer layer = shorts(2, 10, 20, -30, 40);
        assertEquals(10.0, layer.at(-5, -5));
        assertEquals(40.0, layer.at(9, 9));
        assertEquals(20.0, layer.at(7, 0));
    }

    @Test
    void unsignedTypesDoNotComeBackNegative() {
        ByteBuffer bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put(0, (byte) 200);
        assertEquals(200.0, new TerrainLayer(bytes, TerrainLayer.Type.U1, 2).at(0, 0));
        assertEquals(-56.0, new TerrainLayer(bytes, TerrainLayer.Type.I1, 2).at(0, 0));

        ByteBuffer wide = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        wide.putShort(0, (short) 40000);
        assertEquals(40000.0, new TerrainLayer(wide, TerrainLayer.Type.U2, 2).at(0, 0));
    }

    @Test
    void rejectsABufferTooSmallForTheDeclaredSize() {
        ByteBuffer tooSmall = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        assertThrows(IllegalArgumentException.class,
                () -> new TerrainLayer(tooSmall, TerrainLayer.Type.I2, 4));
    }

    @Test
    void parsesTheDtypeStringsTheGeneratorWrites() {
        assertEquals(TerrainLayer.Type.I2, TerrainLayer.Type.parse("<i2"));
        assertEquals(TerrainLayer.Type.U2, TerrainLayer.Type.parse("<u2"));
        assertEquals(TerrainLayer.Type.I1, TerrainLayer.Type.parse("i1"));
        assertEquals(TerrainLayer.Type.U1, TerrainLayer.Type.parse("u1"));
        assertThrows(IllegalArgumentException.class, () -> TerrainLayer.Type.parse("f4"));
    }
}
