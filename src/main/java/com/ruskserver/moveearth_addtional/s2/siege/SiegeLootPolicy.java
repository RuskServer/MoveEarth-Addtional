package com.ruskserver.moveearth_addtional.s2.siege;

/** Pure timing and area policy for post-fall storage access. */
public final class SiegeLootPolicy {
    public static final long FINAL_WINDOW_TICKS = 30L * 60L * 20L;

    private SiegeLootPolicy() { }

    public static boolean fallenAccess(int stage, boolean attacker, boolean coreChunk, boolean vaultChunk) {
        return stage >= 2 && attacker && !coreChunk && !vaultChunk;
    }

    public static boolean finalizedAccess(long nowOpenTick, long expiresOpenTick,
                                          boolean attacker, boolean insideArea, boolean vaultChunk) {
        return attacker && insideArea && !vaultChunk && nowOpenTick < expiresOpenTick;
    }
}
