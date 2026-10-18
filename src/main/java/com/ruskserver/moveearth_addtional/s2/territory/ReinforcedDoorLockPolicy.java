package com.ruskserver.moveearth_addtional.s2.territory;

/** Pure rules of {@link ReinforcedDoorLock}, tested without a level. */
public final class ReinforcedDoorLockPolicy {
    private ReinforcedDoorLockPolicy() {
    }

    /**
     * Whether a right click would open or close the block: it must be a door, trapdoor or fence gate, and
     * sneaking with something in hand uses the item instead, as when placing C4 against the door.
     */
    public static boolean opensBlock(boolean lockable, boolean sneaking, boolean holdingItem) {
        return lockable && !(sneaking && holdingItem);
    }

    /** A door is locked while either of its halves still has working reinforcement. */
    public static boolean locked(boolean clickedHalfReinforced, boolean otherHalfReinforced) {
        return clickedHalfReinforced || otherHalfReinforced;
    }
}
