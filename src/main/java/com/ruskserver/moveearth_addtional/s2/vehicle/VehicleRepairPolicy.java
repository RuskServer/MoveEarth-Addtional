package com.ruskserver.moveearth_addtional.s2.vehicle;

/** Pure, vehicle-wide repair limits; callers pay only when gain is positive. */
public final class VehicleRepairPolicy {
    private VehicleRepairPolicy() { }

    public record State(long combatUntil, long nextRepairAt) {
        public static final State EMPTY = new State(0, 0);
        public State hit(long now, long quietTicks) {
            return new State(Math.max(combatUntil, now + quietTicks), nextRepairAt);
        }
    }
    public record Result(int gain, boolean emergency, long waitTicks, State state) { }

    public static Result repair(int health, int maximum, long now, State state,
                                int emergencyGain, int normalGain, int emergencyCapPercent,
                                long emergencyInterval, long normalInterval) {
        boolean emergency = now < state.combatUntil();
        if (now < state.nextRepairAt()) {
            return new Result(0, emergency, state.nextRepairAt() - now, state);
        }
        int ceiling = emergency ? (int) ((long) maximum * emergencyCapPercent / 100) : maximum;
        // A disabled core cannot be brought back online while it is under fire.
        int gain = emergency && health <= 0 ? 0
                : Math.max(0, Math.min(ceiling - health, emergency ? emergencyGain : normalGain));
        return new Result(gain, emergency, gain == 0 && emergency ? Math.max(0, state.combatUntil() - now) : 0,
                gain == 0 ? state : new State(state.combatUntil(), now + (emergency ? emergencyInterval : normalInterval)));
    }
}
