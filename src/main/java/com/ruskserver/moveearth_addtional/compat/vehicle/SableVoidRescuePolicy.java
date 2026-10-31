package com.ruskserver.moveearth_addtional.compat.vehicle;

/** Bounded recovery attempts; a craft must stay safely above the floor before automatic recovery rearms. */
final class SableVoidRescuePolicy {
    static final long UNSET = Long.MIN_VALUE;
    record State(int attempts, long lastAttempt, long safeSince, boolean suspensionReported) {
        static final State EMPTY = new State(0, UNSET, UNSET, false);
    }

    private SableVoidRescuePolicy() { }

    static State restore(int attempts, long lastAttempt, boolean suspensionReported) {
        if (attempts <= 0 || lastAttempt == UNSET) return State.EMPTY;
        return new State(attempts, lastAttempt, UNSET, suspensionReported);
    }

    static State observe(State state, long now, boolean safelyAboveFloor, long stableTicks) {
        if (state.attempts() == 0 || now < state.lastAttempt()) return State.EMPTY;
        if (!safelyAboveFloor) return new State(state.attempts(), state.lastAttempt(), UNSET, state.suspensionReported());
        long since = state.safeSince() == UNSET ? now : state.safeSince();
        if (now - since >= stableTicks) return State.EMPTY;
        return new State(state.attempts(), state.lastAttempt(), since, state.suspensionReported());
    }

    static boolean mayAttempt(State state, long now, int maxAttempts, long cooldownTicks) {
        return state.attempts() < maxAttempts && (state.attempts() == 0
                || now >= state.lastAttempt() && now - state.lastAttempt() >= cooldownTicks);
    }

    static State attempted(State state, long now) {
        return new State(state.attempts() + 1, now, UNSET, state.suspensionReported());
    }

    static State reported(State state) {
        return new State(state.attempts(), state.lastAttempt(), state.safeSince(), true);
    }
}
