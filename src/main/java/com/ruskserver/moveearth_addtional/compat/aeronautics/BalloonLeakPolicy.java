package com.ruskserver.moveearth_addtional.compat.aeronautics;

/** How fast a Create Aeronautics balloon loses hot air (see {@code ServerBalloonLeakMixin}). Pure, so it is tested. */
public final class BalloonLeakPolicy {
    private BalloonLeakPolicy() {
    }

    /**
     * Ticks over which a balloon's hot air falls toward its target: Aeronautics' own time, or the slower leak time
     * while it is leaking. A balloon leaks when it lost its whole volume, or when a hole left it less room than its
     * burners fill: then the gas above what it can hold is what is escaping. A burner turned down keeps its own time.
     *
     * @param normalTicks   the gas type's emptying time
     * @param leaking       whether the balloon lost its whole volume
     * @param capacity      blocks of hot air it can still hold
     * @param targetVolume  hot air its burners are producing
     */
    public static double emptyingTicks(boolean enabled, double normalTicks, boolean leaking, int capacity,
                                       double targetVolume, double leakSeconds) {
        if (!enabled || !(leaking || capacity < targetVolume)) return normalTicks;
        return Math.max(normalTicks, leakSeconds * 20.0D);
    }
}
