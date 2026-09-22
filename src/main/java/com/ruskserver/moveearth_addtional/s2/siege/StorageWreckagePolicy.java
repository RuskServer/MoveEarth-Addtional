package com.ruskserver.moveearth_addtional.s2.siege;

/** Pure rules for who may empty a wrecked container. */
public final class StorageWreckagePolicy {

    private StorageWreckagePolicy() { }

    /**
     * Whether this player may recover from the wreckage.
     *
     * <p>An unowned wreck is salvage. Ownership is written when a nation member
     * places a container, so a chest placed by somebody with no nation, outside
     * anyone's land, has none -- and neither does one from before the record
     * existed. Every other route needs a nation to match against, so with no
     * owner, nobody passed: the block cannot be opened, and it cannot be broken
     * either while it still holds something. A box nobody can empty and nobody
     * can remove is worse than any rule about who gets the contents.
     *
     * <p>Inside a territory, or inside a siege, an owner is always found -- the
     * controlling nation stands in. So this only ever fires where there was
     * genuinely nobody to give the items back to.
     */
    public static boolean mayRecover(boolean staff, boolean ownerKnown, boolean sameNation,
                                     boolean vehicleLoot, boolean siegeLoot) {
        if (staff) {
            return true;
        }
        if (!ownerKnown) {
            return true;
        }
        return sameNation || vehicleLoot || siegeLoot;
    }
}
