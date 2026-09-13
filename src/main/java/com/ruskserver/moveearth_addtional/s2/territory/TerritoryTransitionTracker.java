package com.ruskserver.moveearth_addtional.s2.territory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Remembers each player's last controlled nation without treating login as a border crossing. */
final class TerritoryTransitionTracker {
    private final Map<UUID, Zone> zones = new HashMap<>();

    boolean observe(UUID playerId, UUID controllingNationId) {
        Zone current = new Zone(controllingNationId);
        Zone previous = zones.put(playerId, current);
        return previous != null && !Objects.equals(previous.controllingNationId(), controllingNationId);
    }

    void forget(UUID playerId) {
        zones.remove(playerId);
    }

    void clear() {
        zones.clear();
    }

    private record Zone(UUID controllingNationId) { }
}
