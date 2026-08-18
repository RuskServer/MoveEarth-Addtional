package com.ruskserver.moveearth_addtional.territory.domain;

import java.util.Objects;
import java.util.UUID;

public record TerritoryOwnerId(UUID value) implements Comparable<TerritoryOwnerId> {
    public TerritoryOwnerId {
        Objects.requireNonNull(value, "value");
    }

    public static TerritoryOwnerId of(UUID value) {
        return new TerritoryOwnerId(value);
    }

    @Override
    public int compareTo(TerritoryOwnerId other) {
        return value.compareTo(other.value);
    }
}
