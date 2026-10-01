package com.ruskserver.moveearth_addtional.s2.vehicle;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Vehicle ids by (dimension, packed core position), so a per-block "is this a vehicle core" check during
 * an explosion is a hash lookup instead of a scan over every vehicle. Free of Minecraft types.
 */
public final class VehiclePositionIndex {
    private final Map<Key, Set<UUID>> byPosition = new HashMap<>();

    public void add(Object dimension, long pos, UUID id) {
        if (dimension == null || id == null) return;
        byPosition.computeIfAbsent(new Key(dimension, pos), ignored -> new LinkedHashSet<>()).add(id);
    }

    public void remove(Object dimension, long pos, UUID id) {
        if (dimension == null || id == null) return;
        Key key = new Key(dimension, pos);
        Set<UUID> ids = byPosition.get(key);
        if (ids == null) return;
        ids.remove(id);
        if (ids.isEmpty()) byPosition.remove(key);
    }

    /** Ids registered at the position; empty when none. Callers break ties by their own order. */
    public Set<UUID> at(Object dimension, long pos) {
        Set<UUID> ids = byPosition.get(new Key(dimension, pos));
        return ids == null ? Set.of() : Collections.unmodifiableSet(ids);
    }

    public void clear() { byPosition.clear(); }

    private record Key(Object dimension, long pos) { }
}
