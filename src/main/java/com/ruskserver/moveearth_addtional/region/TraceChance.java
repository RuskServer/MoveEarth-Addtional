package com.ruskserver.moveearth_addtional.region;

/**
 * Whether a trace of an exclusive resource happens to sit at one spot.
 *
 * <p>A region that is not the specialist for a resource still gets a little of
 * it. For ore that is simply fewer veins, and worldgen's own randomness does
 * the thinning. For anything counted per position -- an oil chunk, a deposit --
 * it has to be decided here, and it has to give the same answer every time it
 * is asked about the same place.
 *
 * <p>Determinism is not a nicety. The Oil Scanner and the pumpjack ask the same
 * question about the same chunk at different moments, as do a deposit detector
 * and the deposit itself. An answer that varies would show resources that are
 * not there and hide ones that are, which is worse than either outcome on its
 * own because it teaches players that the tools lie.
 *
 * <p>No Minecraft in it, so the distribution can be checked rather than assumed.
 */
public final class TraceChance {

    /** Odd 64-bit constants, for mixing coordinates without collapsing them. */
    private static final long X_PRIME = 0x9E3779B97F4A7C15L;
    private static final long Z_PRIME = 0xC2B2AE3D27D4EB4FL;
    private static final long MATERIAL_PRIME = 0x165667B19E3779F9L;

    private TraceChance() { }

    /**
     * True when this spot carries a trace.
     *
     * @param rate the share of positions that should, 0 for none and 1 for all
     */
    public static boolean occurs(long worldSeed, int x, int z, String material, double rate) {
        if (rate <= 0.0) {
            return false;
        }
        if (rate >= 1.0) {
            return true;
        }
        // Each material draws from its own sequence. Sharing one would put every
        // exclusive resource in the same handful of chunks, so a region would
        // either have a little of everything or none of anything -- the one
        // pattern this is meant to avoid.
        long hash = mix(worldSeed
                + x * X_PRIME
                + z * Z_PRIME
                + (material == null ? 0L : material.hashCode() * MATERIAL_PRIME));
        // Top 53 bits, the most mixed ones, read as a fraction of one.
        double roll = (hash >>> 11) * 0x1.0p-53;
        return roll < rate;
    }

    /** SplitMix64's finalizer: cheap, and spreads neighbouring inputs apart. */
    private static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
