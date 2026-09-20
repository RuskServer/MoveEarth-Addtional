package com.ruskserver.moveearth_addtional.region;

/**
 * Finds which region a cell belongs to, with no Minecraft in it so it can be
 * tested.
 *
 * <p>The tile assigns a region to land and leaves the sea at zero — seventy
 * percent of the production tile. Ore features and ore veins still generate
 * under the seabed and on every island, so "no region" cannot be the answer for
 * most of the map; the nearest coast's region is. Doing that search here rather
 * than baking an extended layer into the tile keeps the tile untouched, which
 * matters because the tile also carries the terrain of a world that is already
 * being played on: regenerating it to widen one layer would move the ground.
 */
public final class RegionGrid {

    /** The region layer as a plain grid, so tests need no tile on disk. */
    public interface Cells {
        /** Region id at a cell; zero means the cell has no region. */
        int at(int cellX, int cellZ);

        /** Width of the square grid in cells. */
        int size();
    }

    /** Returned when no region was found within the search limit. */
    public static final int NONE = 0;

    private RegionGrid() { }

    /**
     * The region at a cell, or the nearest one within {@code maxRadiusCells}.
     *
     * <p>Rings are scanned outwards and the closest hit by true distance wins,
     * not the first ring to contain one: a cell at the corner of ring 3 is
     * further away than one in the middle of ring 4, so stopping at the first
     * ring with a hit would pick the wrong coast along diagonals. The scan ends
     * once the ring radius exceeds the best distance found, which is the first
     * point at which no later ring can improve on it.
     *
     * <p>Ties go to the lower region id. Two coasts exactly equidistant is rare
     * but real on a strait, and an arbitrary winner there would move with the
     * iteration order and put a chunk in a different region after a restart.
     */
    public static int regionAt(Cells cells, int cellX, int cellZ, int maxRadiusCells) {
        int here = read(cells, cellX, cellZ);
        if (here != NONE) {
            return here;
        }
        int best = NONE;
        long bestDistance = Long.MAX_VALUE;
        for (int radius = 1; radius <= maxRadiusCells; radius++) {
            if ((long) radius * radius > bestDistance) {
                break;
            }
            // The perimeter is walked directly rather than scanning the whole
            // square and skipping its inside. That turns each ring from O(r^2)
            // into O(r), and the whole search from O(r^3) into O(r^2), which is
            // what makes a radius wide enough for open ocean affordable.
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz : dx == -radius || dx == radius
                        ? allRows(radius)
                        : new int[] { -radius, radius }) {
                    int value = read(cells, cellX + dx, cellZ + dz);
                    if (value == NONE) {
                        continue;
                    }
                    long distance = (long) dx * dx + (long) dz * dz;
                    if (distance < bestDistance || (distance == bestDistance && value < best)) {
                        bestDistance = distance;
                        best = value;
                    }
                }
            }
        }
        return best;
    }

    /** The full column of a ring's left and right edges. */
    private static int[] allRows(int radius) {
        int[] rows = new int[radius * 2 + 1];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = i - radius;
        }
        return rows;
    }

    /** Reads a cell, treating anything off the grid as having no region. */
    private static int read(Cells cells, int cellX, int cellZ) {
        int size = cells.size();
        if (cellX < 0 || cellZ < 0 || cellX >= size || cellZ >= size) {
            return NONE;
        }
        int value = cells.at(cellX, cellZ);
        return value < 0 ? NONE : value;
    }
}
