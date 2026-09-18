package com.ruskserver.moveearth_addtional.terrain;

import java.nio.ByteBuffer;

/**
 * One layer of a terrain tile, held as a little-endian buffer.
 *
 * <p>The offline generator writes flat binaries with one value per cell in
 * row-major order, where the row index is the world Z axis and the column index
 * is the world X axis.
 */
public final class TerrainLayer {
    /** Storage types the tile format uses, matching the dtype strings in tile.json. */
    public enum Type {
        I1(1), U1(1), I2(2), U2(2);

        final int bytes;

        Type(int bytes) {
            this.bytes = bytes;
        }

        static Type parse(String dtype) {
            return switch (dtype) {
                case "i1" -> I1;
                case "u1" -> U1;
                case "<i2", "i2" -> I2;
                case "<u2", "u2" -> U2;
                default -> throw new IllegalArgumentException("Unsupported tile dtype: " + dtype);
            };
        }
    }

    private final ByteBuffer data;
    private final Type type;
    private final int size;

    TerrainLayer(ByteBuffer data, Type type, int size) {
        this.data = data;
        this.type = type;
        this.size = size;
        long expected = (long) size * size * type.bytes;
        if (data.capacity() < expected) {
            throw new IllegalArgumentException("Tile layer is " + data.capacity()
                    + " bytes but " + size + "x" + size + " of " + type + " needs " + expected);
        }
    }

    /** Raw value at a cell. Coordinates are clamped, so sampling never walks off the tile. */
    public double at(int cellX, int cellZ) {
        int x = cellX < 0 ? 0 : Math.min(cellX, size - 1);
        int z = cellZ < 0 ? 0 : Math.min(cellZ, size - 1);
        int index = z * size + x;
        return switch (type) {
            case I1 -> data.get(index);
            case U1 -> data.get(index) & 0xFF;
            case I2 -> data.getShort(index * 2);
            case U2 -> data.getShort(index * 2) & 0xFFFF;
        };
    }

    public Type type() {
        return type;
    }
}
