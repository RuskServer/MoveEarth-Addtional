package com.ruskserver.moveearth_addtional.s2.siege;

/** Pure validation shared by transfer discovery and the authoritative transfer action. */
public final class PrisonerTransferPolicy {
    public static final double MAX_DISTANCE_SQUARED = 36.0D;

    private PrisonerTransferPolicy() { }

    public static boolean allowed(boolean custodyExists, boolean targetExists, boolean differentPlayer,
                                  boolean sameDimension, double distanceSquared, boolean targetAlive,
                                  boolean targetConsenting, boolean sameOperationalNation,
                                  boolean targetRestricted, boolean alreadyEscorting) {
        return custodyExists && targetExists && differentPlayer && sameDimension
                && distanceSquared <= MAX_DISTANCE_SQUARED && targetAlive && targetConsenting
                && sameOperationalNation && !targetRestricted && !alreadyEscorting;
    }
}
