package com.ruskserver.moveearth_addtional.jobs;

/** Pure level-curve calculations kept independent from Minecraft for reliable unit testing. */
public final class JobProgression {
    private JobProgression() {
    }

    public static long xpNeededForNextLevel(int level, int maxLevel, int baseXp,
                                            int linearXp, int quadraticXp) {
        if (level >= maxLevel) {
            return 0;
        }
        long offset = Math.max(0, level - 1L);
        try {
            long linear = Math.multiplyExact((long) linearXp, offset);
            long quadratic = Math.multiplyExact((long) quadraticXp, Math.multiplyExact(offset, offset));
            return Math.max(1L, Math.addExact(baseXp, Math.addExact(linear, quadratic)));
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
