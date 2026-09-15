package com.ruskserver.moveearth_addtional.client.loading;

/** Pure selection policy used to keep loading tips stable and predictable. */
public final class LoadingTipRotation {
    public static final long ROTATION_MILLIS = 12_000L;

    private LoadingTipRotation() {
    }

    public static int index(long seed, long elapsedMillis, int tipCount) {
        if (tipCount <= 0) throw new IllegalArgumentException("tipCount must be positive");
        long rotation = Math.max(0L, elapsedMillis) / ROTATION_MILLIS;
        return Math.floorMod(seed + rotation, tipCount);
    }
}
