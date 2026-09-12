package com.ruskserver.moveearth_addtional.s2.territory;

public final class UpkeepPenaltyPolicy {
    private UpkeepPenaltyPolicy() { }

    public static UpkeepPenalty evaluate(long overdueSince, long now,
                                         long weakenAfterMillis, long disableAfterMillis) {
        if (overdueSince <= 0L || now < overdueSince) return UpkeepPenalty.CURRENT;
        long overdue = now - overdueSince;
        if (overdue >= Math.max(0L, disableAfterMillis)) return UpkeepPenalty.DISABLED;
        if (overdue >= Math.max(0L, weakenAfterMillis)) return UpkeepPenalty.WEAKENED;
        return UpkeepPenalty.GRACE;
    }

    public static int scaleSiegeDamage(int damage, UpkeepPenalty penalty, double weakenedMultiplier) {
        if (damage <= 0) return 0;
        if (penalty == UpkeepPenalty.DISABLED) return Integer.MAX_VALUE;
        if (penalty != UpkeepPenalty.WEAKENED) return damage;
        return (int) Math.min(Integer.MAX_VALUE,
                Math.max(1L, Math.round(damage * Math.max(1.0D, weakenedMultiplier))));
    }
}
