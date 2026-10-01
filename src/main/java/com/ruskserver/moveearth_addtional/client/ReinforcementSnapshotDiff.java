package com.ruskserver.moveearth_addtional.client;

import java.util.function.LongConsumer;

/**
 * Which positions and chunk meshes a reinforcement update invalidates. Pure logic, no game classes.
 *
 * <p>States map a packed block position to the visual state the mesher merges on. A position is
 * changed when it appears, disappears or changes visual state; its chunk is then remeshed, together
 * with the horizontal neighbour across any border it sits on (that neighbour's exposed faces depend
 * on occupancy across the border). Fields that do not change the mesh, such as the activation
 * countdown, never reach the states, so a resent but visually identical snapshot invalidates nothing.
 */
final class ReinforcementSnapshotDiff {
    private ReinforcementSnapshotDiff() { }

    /** Read access to one side of the diff. */
    interface States {
        boolean contains(long pos);

        long get(long pos);

        void forEach(PositionStateConsumer consumer);
    }

    @FunctionalInterface
    interface PositionStateConsumer {
        void accept(long pos, long state);
    }

    @FunctionalInterface
    interface ChunkConsumer {
        void accept(int chunkX, int chunkZ);
    }

    /** Reports every position added, removed or restyled between {@code previous} and {@code next}. */
    static void changedPositions(States previous, States next, LongConsumer changed) {
        next.forEach((pos, state) -> {
            if (!previous.contains(pos) || previous.get(pos) != state) changed.accept(pos);
        });
        previous.forEach((pos, state) -> {
            if (!next.contains(pos)) changed.accept(pos);
        });
    }

    /** The block's own chunk, plus the horizontal neighbour across any border it sits on. */
    static void chunksTouching(int blockX, int blockZ, ChunkConsumer out) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        out.accept(chunkX, chunkZ);
        int localX = blockX & 15;
        int localZ = blockZ & 15;
        if (localX == 0) out.accept(chunkX - 1, chunkZ);
        if (localX == 15) out.accept(chunkX + 1, chunkZ);
        if (localZ == 0) out.accept(chunkX, chunkZ - 1);
        if (localZ == 15) out.accept(chunkX, chunkZ + 1);
    }
}
