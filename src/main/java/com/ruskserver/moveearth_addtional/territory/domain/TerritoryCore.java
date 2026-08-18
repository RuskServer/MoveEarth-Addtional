package com.ruskserver.moveearth_addtional.territory.domain;

import java.util.Objects;
import java.util.UUID;

public record TerritoryCore(
        UUID id,
        TerritoryOwnerId ownerId,
        TerritoryPosition position,
        boolean active
) {
    public TerritoryCore {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(position, "position");
    }
}
