package com.ruskserver.moveearth_addtional.s2.reinforcement;

import java.util.HashMap;
import java.util.Map;

/** Transient chunk epochs. Retain deleted chunks' epochs so removing the last armor invalidates caches. */
public final class ReinforcementMassRevisions {
    private long revision;
    private final Map<Long, Long> chunks = new HashMap<>();

    public long revision() { return revision; }

    public void changed(int blockX, int blockZ) {
        chunks.put(key(blockX >> 4, blockZ >> 4), ++revision);
    }

    public long inside(int minX, int minZ, int maxX, int maxZ) {
        long newest = 0L;
        for (int x = minX >> 4; x <= maxX >> 4; x++) {
            for (int z = minZ >> 4; z <= maxZ >> 4; z++) {
                newest = Math.max(newest, chunks.getOrDefault(key(x, z), 0L));
            }
        }
        return newest;
    }

    private static long key(int x, int z) {
        return (x & 0xffffffffL) | ((long) z << 32);
    }
}
