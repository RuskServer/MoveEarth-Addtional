package com.ruskserver.moveearth_addtional.compat.mekanism;

/**
 * Pure rules of the MekaSuit heavy-hit shield. Kept free of Minecraft classes for unit tests.
 *
 * <p>The shield holds a few charges. A hit at or above the threshold, judged on
 * the damage before armor, spends one and loses a flat amount. Every stopped
 * hit restarts the cooldown; once it runs out all charges are back. With no
 * charges left, heavy hits go through untouched until then.
 */
public final class MekaSuitHeavyShieldPolicy {
    private MekaSuitHeavyShieldPolicy() {
    }

    /** Charges left and the game tick at which every charge is restored. */
    public record State(int charges, long readyAt) {
        public static State full(int maxCharges) {
            return new State(maxCharges, Long.MIN_VALUE);
        }
    }

    public record Settings(double threshold, double reduction, int maxCharges, long cooldownTicks) { }

    /** What a hit came to: the damage to apply and the shield afterwards. */
    public record Outcome(boolean blocked, float damage, State state, boolean depleted) { }

    /** The state at {@code now}, with charges restored once the cooldown has run out. */
    public static State current(State state, long now, int maxCharges) {
        if (state == null) return State.full(maxCharges);
        if (now >= state.readyAt()) return new State(maxCharges, state.readyAt());
        return new State(Math.min(state.charges(), maxCharges), state.readyAt());
    }

    public static Outcome onHit(float damage, State state, long now, Settings settings) {
        State current = current(state, now, settings.maxCharges());
        if (damage < settings.threshold() || current.charges() <= 0) {
            return new Outcome(false, damage, current, false);
        }
        int left = current.charges() - 1;
        float reduced = (float) Math.max(0.0D, damage - settings.reduction());
        return new Outcome(true, reduced, new State(left, now + settings.cooldownTicks()), left == 0);
    }

    /** Whether charges were spent and have now all come back, so the player can be told. */
    public static boolean justRecharged(State state, long now, int maxCharges) {
        return state != null && state.charges() < maxCharges && now >= state.readyAt();
    }
}
