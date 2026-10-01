package com.ruskserver.moveearth_addtional.client;

import java.util.Arrays;

/**
 * How many reinforcement faces one frame may draw, and in which order.
 *
 * <p>Chunks are visited nearest-first, so whichever limit applies cuts the far end of the overlay
 * rather than an arbitrary part of it. The compatibility path (no OpenGL 4.5) emits every face as a
 * separate immediate-mode box, so it is held to a much smaller limit than the instanced path.
 */
final class ReinforcementRenderBudget {
    static final int FACES_PER_BLOCK = 6;
    static final int PASSIVE_BLOCK_LIMIT = 4096;
    static final int DETAILED_BLOCK_LIMIT = 8192;
    static final int INSTANCED_FACE_CAPACITY = DETAILED_BLOCK_LIMIT * FACES_PER_BLOCK;
    /** Faces drawn as immediate-mode boxes per frame when instancing is unavailable. */
    static final int COMPATIBILITY_FACE_LIMIT = 4096;

    private ReinforcementRenderBudget() { }

    static int faceLimit(boolean instanced, boolean detailed) {
        if (!instanced) return COMPATIBILITY_FACE_LIMIT;
        return (detailed ? DETAILED_BLOCK_LIMIT : PASSIVE_BLOCK_LIMIT) * FACES_PER_BLOCK;
    }

    /**
     * Packs a distance and an index into one sortable key. Non-negative float bit patterns order
     * the same way as the floats, so sorting the keys orders by distance, then by index.
     */
    static long key(double distanceSquared, int index) {
        float distance = (float) Math.max(0.0D, distanceSquared);
        if (Float.isNaN(distance)) distance = Float.MAX_VALUE;
        return (long) Float.floatToIntBits(distance) << 32 | (index & 0xFFFFFFFFL);
    }

    static int index(long key) {
        return (int) key;
    }

    /** Sorts the first {@code count} keys nearest-first in place, without allocating. */
    static void sortNearestFirst(long[] keys, int count) {
        Arrays.sort(keys, 0, count);
    }
}
