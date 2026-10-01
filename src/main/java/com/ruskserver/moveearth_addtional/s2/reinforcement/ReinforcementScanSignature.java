package com.ruskserver.moveearth_addtional.s2.reinforcement;

/**
 * Content hash deciding whether a reinforcement scan must be resent. It covers only state that changes
 * through events; the activation countdown is represented by its absolute {@code activatesAt} tick, so a
 * running countdown never invalidates an otherwise identical snapshot.
 */
public final class ReinforcementScanSignature {
    public static final long EMPTY = 0xcbf29ce484222325L;

    private ReinforcementScanSignature() { }

    public static long add(long hash, long pos, ReinforcementEntry entry, boolean siegeDisabled) {
        hash = mix(hash, pos);
        hash = mix(hash, entry.material().ordinal());
        hash = mix(hash, entry.durability());
        hash = mix(hash, entry.enabled() ? 1L : 0L);
        hash = mix(hash, entry.activatesAt());
        hash = mix(hash, entry.constructing() ? 1L : 0L);
        hash = mix(hash, siegeDisabled ? 1L : 0L);
        return hash;
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * 0x100000001b3L;
    }
}
