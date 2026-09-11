package com.ruskserver.moveearth_addtional.s2.territory;

/** Pure geometry for the square chunk area owned by one territory core. */
public record TerritoryPreviewArea(int centerChunkX, int centerChunkZ, int radius) {
    public static final int MIN_RADIUS = 0;
    public static final int MAX_RADIUS = 4;

    public TerritoryPreviewArea {
        if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("Territory radius must be between 0 and 4 chunks");
        }
    }

    public int diameterChunks() {
        return radius * 2 + 1;
    }

    public int chunkCount() {
        int diameter = diameterChunks();
        return diameter * diameter;
    }

    public int minChunkX() {
        return centerChunkX - radius;
    }

    public int maxChunkX() {
        return centerChunkX + radius;
    }

    public int minChunkZ() {
        return centerChunkZ - radius;
    }

    public int maxChunkZ() {
        return centerChunkZ + radius;
    }

    public int minBlockX() {
        return minChunkX() << 4;
    }

    public int maxBlockXExclusive() {
        return (maxChunkX() + 1) << 4;
    }

    public int minBlockZ() {
        return minChunkZ() << 4;
    }

    public int maxBlockZExclusive() {
        return (maxChunkZ() + 1) << 4;
    }

    public boolean containsChunk(int chunkX, int chunkZ) {
        return chunkX >= minChunkX() && chunkX <= maxChunkX()
                && chunkZ >= minChunkZ() && chunkZ <= maxChunkZ();
    }

    public boolean overlaps(TerritoryPreviewArea other) {
        return minChunkX() <= other.maxChunkX() && maxChunkX() >= other.minChunkX()
                && minChunkZ() <= other.maxChunkZ() && maxChunkZ() >= other.minChunkZ();
    }
}
