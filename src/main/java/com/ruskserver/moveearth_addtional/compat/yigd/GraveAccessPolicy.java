package com.ruskserver.moveearth_addtional.compat.yigd;

import java.util.UUID;

/** Death-time ownership is immutable; placement outside that nation's land unlocks permanently. */
public final class GraveAccessPolicy {
    private GraveAccessPolicy() { }

    public record Ownership(UUID player, UUID nation, boolean unlocked) {
        public Ownership placed(boolean insideHomeTerritory) {
            return unlocked || nation == null || !insideHomeTerritory
                    ? new Ownership(player, nation, true) : this;
        }

        public boolean allows(UUID actor, UUID actorNation, boolean alliedStorage,
                              boolean siegeLoot, boolean operator) {
            return operator || unlocked || player.equals(actor)
                    || nation != null && (nation.equals(actorNation) || alliedStorage || siegeLoot);
        }
    }
}
