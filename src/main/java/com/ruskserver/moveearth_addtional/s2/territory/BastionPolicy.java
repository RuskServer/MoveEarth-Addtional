package com.ruskserver.moveearth_addtional.s2.territory;

import java.util.Optional;
import java.util.UUID;

/** Pure ownership rule shared by all server-side Bastion entry points. */
final class BastionPolicy {
    private BastionPolicy() {
    }

    static boolean isRestricted(Optional<UUID> controllingNation, Optional<UUID> actorNation,
                                boolean allied, boolean roleAccess, boolean administrativeBypass) {
        if (administrativeBypass || controllingNation.isEmpty()) return false;
        if (actorNation.isEmpty()) return true;
        boolean acceptedNation = controllingNation.get().equals(actorNation.get()) || allied;
        return !acceptedNation || !roleAccess;
    }

    /** A machine has no player identity: it may not carry blocks into another nation's active Bastion. */
    static boolean blocksMachineMovement(Optional<UUID> actuatorNation, Optional<UUID> targetNation,
                                         boolean bastionEnabled) {
        return bastionEnabled && targetNation.isPresent()
                && !targetNation.equals(actuatorNation);
    }
}
