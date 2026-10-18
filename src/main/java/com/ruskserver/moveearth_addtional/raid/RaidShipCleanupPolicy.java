package com.ruskserver.moveearth_addtional.raid;

import java.util.OptionalInt;

/** Which Sable bodies are left-over raid airships. Pure, so it is unit tested. */
public final class RaidShipCleanupPolicy {
    /** The name {@code SableAirshipController} gives a raid airship, followed by the raid number. */
    static final String NAME_PREFIX = "MoveEarth Raid #";

    private RaidShipCleanupPolicy() { }

    /** The raid number in a raid airship's name, for ships tagged before raids were recorded on the body. */
    public static OptionalInt raidIdFromName(String name) {
        if (name == null || !name.startsWith(NAME_PREFIX)) return OptionalInt.empty();
        try {
            return OptionalInt.of(Integer.parseInt(name.substring(NAME_PREFIX.length()).trim()));
        } catch (NumberFormatException notANumber) {
            return OptionalInt.empty();
        }
    }

    /**
     * A body belonging to a raid goes once that raid is no longer running, unless a player has made it a
     * vehicle of their own by placing a vehicle core on it.
     */
    public static boolean remove(boolean raidRunning, boolean claimedAsVehicle) {
        return !raidRunning && !claimedAsVehicle;
    }
}
