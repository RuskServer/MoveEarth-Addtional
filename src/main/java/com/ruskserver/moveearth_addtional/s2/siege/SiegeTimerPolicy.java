package com.ruskserver.moveearth_addtional.s2.siege;

/** Pure transition rules for the server-open-time Siege timer. */
public final class SiegeTimerPolicy {
    private SiegeTimerPolicy() { }

    public static State begin(boolean effectiveDamage, long initialTicks, long rollingTicks) {
        return effectiveDamage
                ? new State(Phase.ROLLING, rollingTicks)
                : new State(Phase.INITIAL_LOCK, initialTicks);
    }

    public static State attack(State current, boolean effectiveDamage, long rollingTicks) {
        if (current == null) return null;
        return effectiveDamage ? new State(Phase.ROLLING, rollingTicks) : current;
    }

    /**
     * Whether the clock should stop this tick.
     *
     * <p>Separated from the world so the boundary can be tested. A budget that
     * was spent one tick late or early would be invisible in play and would
     * decide sieges, which is the worst combination a number can have.
     *
     * @param spentTicks  how much of the budget this siege has already used
     * @param budgetTicks the whole budget; zero disables holding entirely
     */
    public static boolean holds(boolean contested, long spentTicks, long budgetTicks) {
        return contested && spentTicks < budgetTicks;
    }

    /**
     * Whether an attacker counts as pressing the siege right now.
     *
     * <p>Split out so the window can be tested without a world. Presence that
     * is not checked for work locks another nation's affairs on the strength of
     * somebody standing still, and a window that is off by a tick either way
     * would never be noticed from in game.
     *
     * @param sinceActiveTicks how long ago they last dug, hit or were hit;
     *                         negative for never
     */
    public static boolean pressing(long sinceActiveTicks, long windowTicks) {
        return sinceActiveTicks >= 0L && sinceActiveTicks <= windowTicks;
    }

    public static State advance(State current, long elapsedTicks) {
        if (current == null || elapsedTicks <= 0L) return current;
        return new State(current.phase(), Math.max(0L, current.remainingTicks() - elapsedTicks));
    }

    public enum Phase { INITIAL_LOCK, ROLLING }

    public record State(Phase phase, long remainingTicks) {
        public State {
            if (phase == null) phase = Phase.INITIAL_LOCK;
            remainingTicks = Math.max(0L, remainingTicks);
        }

        public boolean expired() { return remainingTicks == 0L; }
    }
}
