package com.ruskserver.moveearth_addtional.s2.combat;

/** Pure progress and urgency rules for the combat-tag boss bar. */
public final class CombatTagBossBarDisplay {
    private CombatTagBossBarDisplay() { }

    public static Display create(long remainingTicks, long maximumTicks) {
        long remaining = Math.max(0L, remainingTicks);
        long maximum = Math.max(1L, maximumTicks);
        int seconds = (int) Math.max(0L, (remaining + 19L) / 20L);
        float progress = Math.max(0.0F, Math.min(1.0F, remaining / (float) maximum));
        return new Display(seconds, progress, seconds <= 10);
    }

    public static long updatedMaximum(long previousMaximum, long previousRemaining, long remaining) {
        if (previousMaximum <= 0L || remaining > previousRemaining) return Math.max(1L, remaining);
        return previousMaximum;
    }

    public record Display(int seconds, float progress, boolean urgent) { }
}
