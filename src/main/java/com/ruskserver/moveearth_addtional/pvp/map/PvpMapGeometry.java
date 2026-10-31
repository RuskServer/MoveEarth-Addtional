package com.ruskserver.moveearth_addtional.pvp.map;

import java.util.List;

/**
 * The plain geometry of a PvP map's battle area, on whole block coordinates. Pure, so it is tested without
 * Minecraft.
 */
public final class PvpMapGeometry {
    /** The widest a battle area may be on either axis, so its tactical map stays small enough to send. */
    public static final int MAX_SPAN = 512;
    /** Blocks added around the spawns and the hill when a map has no area of its own. */
    static final int DERIVED_PADDING = 24;
    static final int DERIVED_BELOW = 8;
    static final int DERIVED_ABOVE = 32;
    /**
     * Blocks added below and above the corners an admin set. The area is an area on the map: the corners are
     * often clicked on the ground, and players stand and jump above it, so only its scan reads the height.
     */
    static final int OWN_BELOW = 16;
    static final int OWN_ABOVE = 48;

    private PvpMapGeometry() {
    }

    /** An inclusive box of blocks; {@code min} is never above {@code max} on any axis. */
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public static Box of(int x1, int y1, int z1, int x2, int y2, int z2) {
            return new Box(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                    Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
        }

        public int width() {
            return maxX - minX + 1;
        }

        public int depth() {
            return maxZ - minZ + 1;
        }

        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        public boolean containsBox(Box other) {
            return contains(other.minX, other.minY, other.minZ) && contains(other.maxX, other.maxY, other.maxZ);
        }

        /** Inside the area seen from above, at any height. */
        public boolean containsXZ(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        public boolean containsXZ(Box other) {
            return containsXZ(other.minX, other.minZ) && containsXZ(other.maxX, other.maxZ);
        }

        public boolean tooLarge() {
            return width() > MAX_SPAN || depth() > MAX_SPAN;
        }
    }

    /** One point as {x, y, z}. */
    public record Point(int x, int y, int z) { }

    /**
     * The area for a map that has none of its own: everything that matters in it, the spawns and the hill,
     * with room around them to move.
     */
    public static Box derived(List<Point> points) {
        if (points.isEmpty()) return new Box(0, 0, 0, 0, 0, 0);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Point point : points) {
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
            maxZ = Math.max(maxZ, point.z());
        }
        return new Box(minX - DERIVED_PADDING, minY - DERIVED_BELOW, minZ - DERIVED_PADDING,
                maxX + DERIVED_PADDING, maxY + DERIVED_ABOVE, maxZ + DERIVED_PADDING);
    }

    /** The area of two corners an admin set, with room above and below them for the scan. */
    public static Box own(int x1, int y1, int z1, int x2, int y2, int z2) {
        Box corners = Box.of(x1, y1, z1, x2, y2, z2);
        return new Box(corners.minX, corners.minY - OWN_BELOW, corners.minZ,
                corners.maxX, corners.maxY + OWN_ABOVE, corners.maxZ);
    }

    /**
     * Blocks per tactical map cell, so the larger side fits in {@code maxCells}: 1, 2 or 4.
     */
    public static int cellSize(Box box, int maxCells) {
        int span = Math.max(box.width(), box.depth());
        int size = 1;
        while (span > maxCells * size && size < 4) size *= 2;
        return size;
    }

    /** Cells along a side of {@code blocks} at {@code cellSize} blocks per cell. */
    public static int cells(int blocks, int cellSize) {
        return (blocks + cellSize - 1) / cellSize;
    }
}
