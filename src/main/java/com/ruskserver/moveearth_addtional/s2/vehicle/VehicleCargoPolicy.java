package com.ruskserver.moveearth_addtional.s2.vehicle;

/** Retention and packing rules of {@link VehicleCargoBackup}. Pure, so it is tested without a server. */
public final class VehicleCargoPolicy {
    /** Snapshots kept per vehicle; at the ten-minute interval, about two hours of history. */
    public static final int KEPT_SNAPSHOTS = 12;
    public static final int INTERVAL_TICKS = 10 * 60 * 20;
    /** Slots in the shulker boxes the cargo is handed out in. */
    public static final int BOX_SLOTS = 27;

    private VehicleCargoPolicy() {
    }

    /** How many boxes {@code stacks} item stacks fill. */
    public static int boxes(int stacks) {
        return (Math.max(0, stacks) + BOX_SLOTS - 1) / BOX_SLOTS;
    }

    /** The index to drop once a new snapshot is added to {@code count} kept ones, or -1 to drop none. */
    public static int dropIndex(int count) {
        return dropIndex(count, -1);
    }

    /**
     * As {@link #dropIndex(int)}, sparing {@code pinned}: the newest record taken just before a split, which is
     * the one staff hand back from, must not be pushed out by the routine records after it.
     */
    public static int dropIndex(int count, int pinned) {
        if (count < KEPT_SNAPSHOTS) return -1;
        return pinned == count - 1 ? count - 2 : count - 1;
    }

    /** {@code generation} counted from 1 for the newest, as an index into the kept list; -1 when out of range. */
    public static int index(int generation, int count) {
        return generation >= 1 && generation <= count ? generation - 1 : -1;
    }
}
