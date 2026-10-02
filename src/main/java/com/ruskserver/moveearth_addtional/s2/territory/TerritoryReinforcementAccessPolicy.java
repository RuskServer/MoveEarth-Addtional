package com.ruskserver.moveearth_addtional.s2.territory;

/** Pure access rule shared by the server territory registry and lightweight tests. */
public final class TerritoryReinforcementAccessPolicy {
    private TerritoryReinforcementAccessPolicy() {
    }

    /**
     * @param effectivelyControlled the chunk is inside the nation's effective territory
     * @param configuringReservation the chunk is reserved by one of its configuring cores
     * @param lapsedButUnclaimed the chunk was reserved by a configuring core whose hour ran
     *                           out, and no other nation has claimed it since
     */
    public static boolean canManage(boolean effectivelyControlled, boolean configuringReservation,
                                    boolean lapsedButUnclaimed) {
        return effectivelyControlled || configuringReservation || lapsedButUnclaimed;
    }
}
