package com.ruskserver.moveearth_addtional.s2.territory;

public final class TerritoryCoreHealthPolicy {
    private TerritoryCoreHealthPolicy() { }

    public static int damage(int health, int amount) {
        return Math.max(0, health - Math.max(0, amount));
    }

    public static RegenResult advance(int health, int maximum, long delayTicks, long progressTicks,
                                      long elapsedTicks, long configuredIntervalTicks,
                                      double percentPerInterval) {
        int safeMaximum = Math.max(1, maximum);
        int safeHealth = Math.max(0, Math.min(safeMaximum, health));
        if (safeHealth == 0 || safeHealth == safeMaximum || elapsedTicks <= 0L) {
            return new RegenResult(safeHealth, Math.max(0L, delayTicks), Math.max(0L, progressTicks));
        }
        long remainingElapsed = elapsedTicks;
        long nextDelay = Math.max(0L, delayTicks);
        if (nextDelay > 0L) {
            long consumed = Math.min(nextDelay, remainingElapsed);
            nextDelay -= consumed;
            remainingElapsed -= consumed;
        }
        long interval = Math.max(1L, configuredIntervalTicks);
        long nextProgress = Math.max(0L, progressTicks) + remainingElapsed;
        long cycles = nextProgress / interval;
        nextProgress %= interval;
        if (cycles <= 0L) return new RegenResult(safeHealth, nextDelay, nextProgress);
        int perCycle = Math.max(1, (int) Math.ceil(safeMaximum * Math.max(0.0D, percentPerInterval) / 100.0D));
        long healed = Math.min((long) safeMaximum - safeHealth, cycles * perCycle);
        int nextHealth = (int) (safeHealth + healed);
        return new RegenResult(nextHealth, nextDelay, nextHealth == safeMaximum ? 0L : nextProgress);
    }

    public record RegenResult(int health, long delayTicks, long progressTicks) { }
}
