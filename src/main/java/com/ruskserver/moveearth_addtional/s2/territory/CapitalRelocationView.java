package com.ruskserver.moveearth_addtional.s2.territory;

import java.util.List;
import java.util.UUID;

/** Separate v3.8 view: never extends the v3.7 hub snapshot's wire format. */
public record CapitalRelocationView(int requestId, long revision, UUID capitalId, long remainingTicks, long cooldownTicks,
                                    boolean allowed, String result, List<Core> cores) {
    public static final UUID NONE = new UUID(0L, 0L);
    public static final int MAX_CORES = 512;
    public CapitalRelocationView { cores = List.copyOf(cores); }
    public record Core(UUID id, String dimension, int x, int y, int z, int radius,
                       boolean capital, String reason) { }
}
