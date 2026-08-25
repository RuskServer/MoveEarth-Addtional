package com.ruskserver.moveearth_addtional.jobs;

/** Pure recurring-point conversion used by persistent Jobs data and unit tests. */
public final class JobPointIncome {
    public static final double XP_PER_POINT = 500.0D;
    public static final int MAX_POINTS_PER_WINDOW = 4;
    public static final long WINDOW_TICKS = 60L * 60L * 20L;
    private static final double EPSILON = 1.0E-9D;

    private JobPointIncome() {
    }

    public static Result apply(long startedAt, double storedXp, int pointsInWindow,
                               double effectiveXp, long gameTime) {
        if (!Double.isFinite(effectiveXp) || effectiveXp <= 0) {
            return new Result(startedAt, sanitizeXp(storedXp), clampPoints(pointsInWindow), 0);
        }
        if (startedAt < 0 || gameTime < startedAt || gameTime - startedAt >= WINDOW_TICKS) {
            startedAt = gameTime;
            storedXp = 0;
            pointsInWindow = 0;
        } else {
            storedXp = sanitizeXp(storedXp);
            pointsInWindow = clampPoints(pointsInWindow);
        }
        if (pointsInWindow >= MAX_POINTS_PER_WINDOW) {
            return new Result(startedAt, 0, MAX_POINTS_PER_WINDOW, 0);
        }

        double combined = storedXp + effectiveXp;
        long convertible = (long) Math.floor((combined + EPSILON) / XP_PER_POINT);
        int earned = (int) Math.min(convertible, MAX_POINTS_PER_WINDOW - pointsInWindow);
        int updatedPoints = pointsInWindow + earned;
        double updatedXp = Math.max(0.0D, combined - earned * XP_PER_POINT);
        if (updatedPoints >= MAX_POINTS_PER_WINDOW) {
            updatedXp = 0;
        } else if (updatedXp >= XP_PER_POINT) {
            updatedXp %= XP_PER_POINT;
        }
        return new Result(startedAt, updatedXp, updatedPoints, earned);
    }

    public static long ticksRemaining(long startedAt, long gameTime) {
        if (startedAt < 0 || gameTime < startedAt || gameTime - startedAt >= WINDOW_TICKS) {
            return WINDOW_TICKS;
        }
        return WINDOW_TICKS - (gameTime - startedAt);
    }

    private static double sanitizeXp(double xp) {
        return Double.isFinite(xp) ? Math.max(0.0D, Math.min(xp, XP_PER_POINT)) : 0.0D;
    }

    private static int clampPoints(int points) {
        return Math.max(0, Math.min(MAX_POINTS_PER_WINDOW, points));
    }

    public record Result(long startedAt, double xpTowardsNextPoint, int pointsInWindow, int pointsEarned) {
    }
}
