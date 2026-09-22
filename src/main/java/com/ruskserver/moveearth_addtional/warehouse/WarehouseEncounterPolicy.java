package com.ruskserver.moveearth_addtional.warehouse;

/** Pure transition conditions, kept independent of entity event ordering. */
public final class WarehouseEncounterPolicy {
    private WarehouseEncounterPolicy() { }

    public static boolean firstHalfHealthHit(boolean notYetAlerted, float currentHealth,
                                             float maximumHealth) {
        return notYetAlerted && maximumHealth > 0.0F && currentHealth <= maximumHealth * 0.5F;
    }

    public static boolean matchesBoss(boolean phaseAllowsTransition, java.util.UUID expected,
                                      java.util.UUID actual) {
        return phaseAllowsTransition && expected != null && expected.equals(actual);
    }

    public static boolean deadlineReached(long openTicksNow, long deadline) {
        return deadline > 0 && openTicksNow >= deadline;
    }
}
