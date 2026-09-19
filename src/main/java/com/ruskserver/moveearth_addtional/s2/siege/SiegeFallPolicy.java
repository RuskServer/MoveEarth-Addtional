package com.ruskserver.moveearth_addtional.s2.siege;

/** Pure timing, staged protection and counter-capture rules after a core falls. */
public final class SiegeFallPolicy {
    private SiegeFallPolicy() { }

    /** Mercenaries may fight, but only an able member of the defending nation holds capture progress. */
    public static Presence presence(boolean defender, boolean attacker) {
        return !defender ? Presence.EMPTY_OR_ATTACKER
                : attacker ? Presence.CONTESTED : Presence.DEFENDER_ONLY;
    }

    public static AdvanceResult advance(long remainingTicks, long captureTicks, Presence presence,
                                        long elapsedTicks, long totalTicks, long stageTicks,
                                        long requiredCaptureTicks) {
        long remaining = Math.max(0L, remainingTicks - Math.max(0L, elapsedTicks));
        long capture = Math.max(0L, captureTicks);
        if (presence == Presence.DEFENDER_ONLY) {
            capture = Math.min(Math.max(1L, requiredCaptureTicks), capture + Math.max(0L, elapsedTicks));
        } else if (presence == Presence.EMPTY_OR_ATTACKER) {
            capture = Math.max(0L, capture - Math.max(0L, elapsedTicks) / 2L);
        }
        return new AdvanceResult(remaining, capture,
                stageFor(remaining, totalTicks, stageTicks), capture >= Math.max(1L, requiredCaptureTicks));
    }

    public static int stageFor(long remainingTicks, long totalTicks, long stageTicks) {
        long elapsed = Math.max(0L, Math.max(1L, totalTicks) - Math.max(0L, remainingTicks));
        long stage = Math.max(1L, stageTicks);
        return elapsed >= Math.max(1L, totalTicks) ? 3 : elapsed >= stage * 2L ? 2 : elapsed >= stage ? 1 : 0;
    }

    /** Whether protection at a chunk-ring distance from the core is disabled. */
    public static boolean protectionDisabled(int stage, int coreRadius, int ringDistance) {
        int radius = Math.max(0, coreRadius);
        int ring = Math.max(0, ringDistance);
        if (stage >= 3) return true;
        if (stage == 2) return ring >= 1;
        return stage == 1 && radius > 0 && ring > Math.max(0, radius - 2);
    }

    public enum Presence { DEFENDER_ONLY, CONTESTED, EMPTY_OR_ATTACKER }
    public record AdvanceResult(long remainingTicks, long captureTicks, int stage, boolean recovered) { }
}
