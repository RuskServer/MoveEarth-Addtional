package com.ruskserver.moveearth_addtional.s2.territory;

/** Pure access rule shared by the server territory registry and lightweight tests. */
public final class TerritoryReinforcementAccessPolicy {
    private TerritoryReinforcementAccessPolicy() {
    }

    public static boolean canManage(boolean effectivelyControlled, boolean configuringReservation) {
        return effectivelyControlled || configuringReservation;
    }
}
