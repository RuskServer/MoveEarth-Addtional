package com.ruskserver.moveearth_addtional.handler.occlusion;

import java.util.function.LongConsumer;
import java.util.function.LongPredicate;

/**
 * Breadth-first walk over chunk sections through their open faces, limited by depth,
 * the view cone and a node budget. Plain Java on reusable primitive queues: the old
 * walk allocated a record, a SectionPos and a Vec3 per node, up to every tick per player.
 *
 * <p>Faces use {@code Direction} ordinals (DOWN, UP, NORTH, SOUTH, WEST, EAST), whose
 * opposite is {@code ordinal ^ 1}; section keys use {@code SectionPos.asLong}'s packing.</p>
 */
final class SectionVisibilitySearch {
    static final int COMPLETE = Integer.MAX_VALUE;
    private static final int[] STEP_X = {0, 0, 0, 0, -1, 1};
    private static final int[] STEP_Y = {-1, 1, 0, 0, 0, 0};
    private static final int[] STEP_Z = {0, 0, -1, 1, 0, 0};

    @FunctionalInterface
    interface MaskSource {
        long mask(int sectionX, int sectionY, int sectionZ);
    }

    private final int capacity;
    private final int[] queueX;
    private final int[] queueY;
    private final int[] queueZ;
    private final byte[] queueFace;
    private final short[] queueDepth;
    private int queued;

    SectionVisibilitySearch(int capacity) {
        this.capacity = Math.max(6, capacity);
        queueX = new int[this.capacity];
        queueY = new int[this.capacity];
        queueZ = new int[this.capacity];
        queueFace = new byte[this.capacity];
        queueDepth = new short[this.capacity];
    }

    /** Same packing as {@code SectionPos.asLong(x, y, z)}. */
    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFF) << 42) | (long) (y & 0xFFFFF) | ((long) (z & 0x3FFFFF) << 20);
    }

    static boolean connected(long mask, int fromFace, int toFace) {
        return (mask & (1L << (fromFace * 6 + toFace))) != 0L;
    }

    /** Sections queued by the last run, the start included. */
    int queued() {
        return queued;
    }

    /**
     * Fills {@code visible} with the sections seen from the start section.
     *
     * @param markVisited adds a key to the run's visited set, true when it was new
     * @return {@link #COMPLETE}, or the depth at which the node budget ran out: sections
     *         that far or further may be missing from {@code visible}
     */
    int run(int startX, int startY, int startZ,
            double eyeX, double eyeY, double eyeZ, double lookX, double lookY, double lookZ,
            double minDot, int maxDepth, MaskSource masks, LongPredicate markVisited, LongConsumer visible) {
        int head = 0;
        int tail = 0;
        int truncatedDepth = COMPLETE;
        long startKey = key(startX, startY, startZ);
        markVisited.test(startKey);
        visible.accept(startKey);
        queued = 1;
        for (int face = 0; face < 6; face++) {
            int x = startX + STEP_X[face];
            int y = startY + STEP_Y[face];
            int z = startZ + STEP_Z[face];
            if (!markVisited.test(key(x, y, z))) continue;
            queueX[tail] = x;
            queueY[tail] = y;
            queueZ[tail] = z;
            queueFace[tail] = (byte) (face ^ 1);
            queueDepth[tail] = 1;
            tail++;
        }

        while (head < tail) {
            int x = queueX[head];
            int y = queueY[head];
            int z = queueZ[head];
            int enterFace = queueFace[head];
            int depth = queueDepth[head];
            head++;

            if (depth > 1) {
                double dx = (x << 4) + 8.0D - eyeX;
                double dy = (y << 4) + 8.0D - eyeY;
                double dz = (z << 4) + 8.0D - eyeZ;
                double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (length > 0.0D && (lookX * dx + lookY * dy + lookZ * dz) / length < minDot) continue;
            }
            visible.accept(key(x, y, z));
            if (depth >= maxDepth) continue;

            long mask = masks.mask(x, y, z);
            if (mask == 0L) continue;
            for (int exitFace = 0; exitFace < 6; exitFace++) {
                if (exitFace == enterFace || !connected(mask, enterFace, exitFace)) continue;
                if (tail >= capacity) {
                    truncatedDepth = Math.min(truncatedDepth, depth + 1);
                    break;
                }
                int nx = x + STEP_X[exitFace];
                int ny = y + STEP_Y[exitFace];
                int nz = z + STEP_Z[exitFace];
                if (!markVisited.test(key(nx, ny, nz))) continue;
                queueX[tail] = nx;
                queueY[tail] = ny;
                queueZ[tail] = nz;
                queueFace[tail] = (byte) (exitFace ^ 1);
                queueDepth[tail] = (short) (depth + 1);
                tail++;
            }
        }
        queued += tail;
        return truncatedDepth;
    }
}
