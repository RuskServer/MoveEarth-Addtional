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
