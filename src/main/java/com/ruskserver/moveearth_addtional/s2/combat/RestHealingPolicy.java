package com.ruskserver.moveearth_addtional.s2.combat;

/** Pure calculations shared by the persistent rest-healing quota. */
public final class RestHealingPolicy {
    private RestHealingPolicy() { }

    public static Clock advance(long period, long elapsed, long addedTicks) {
        long safePeriod = Math.max(1L, period);
        long total = Math.max(0L, elapsed) + Math.max(0L, addedTicks);
        return new Clock(total / safePeriod, total % safePeriod);
    }

    public static float remaining(float maximum, float spent) {
        return Math.max(0.0F, Math.max(0.0F, maximum) - Math.max(0.0F, spent));
    }

    public static float healAmount(float missingHealth, float allowance, float step) {
        return Math.max(0.0F, Math.min(Math.max(0.0F, missingHealth),
                Math.min(Math.max(0.0F, allowance), Math.max(0.0F, step))));
    }

    public record Clock(long completedPeriods, long elapsedTicks) { }
}
