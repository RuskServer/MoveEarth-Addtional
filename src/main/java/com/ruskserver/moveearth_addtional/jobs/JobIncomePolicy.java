package com.ruskserver.moveearth_addtional.jobs;

/** Converts validated Jobs XP to bounded currency without changing XP progression. */
public final class JobIncomePolicy {
    public static final double XP_PER_CURRENCY = 25.0D;
    public static final int HOURLY_LIMIT = 20;
    public static final int DAILY_LIMIT = 80;

    private JobIncomePolicy() { }

    public static Award award(double xp, double carriedXp, int paidThisHour, int paidToday) {
        if (!Double.isFinite(xp) || xp <= 0.0D) return new Award(0, safeCarry(carriedXp));
        int allowance = Math.max(0, Math.min(HOURLY_LIMIT - Math.max(0, paidThisHour),
                DAILY_LIMIT - Math.max(0, paidToday)));
        if (allowance == 0) return new Award(0, 0.0D);
        double totalXp = safeCarry(carriedXp) + xp;
        int earned = (int) Math.min(allowance, Math.floor(totalXp / XP_PER_CURRENCY));
        double remainder = earned == allowance ? 0.0D : totalXp - earned * XP_PER_CURRENCY;
        return new Award(earned, Math.max(0.0D, Math.min(XP_PER_CURRENCY - 1.0E-9D, remainder)));
    }

    private static double safeCarry(double value) {
        return Double.isFinite(value) && value >= 0.0D && value < XP_PER_CURRENCY ? value : 0.0D;
    }

    public record Award(int currency, double carriedXp) { }
}
