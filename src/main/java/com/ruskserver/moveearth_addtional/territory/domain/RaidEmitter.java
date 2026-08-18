package com.ruskserver.moveearth_addtional.territory.domain;

import java.util.Objects;
import java.util.UUID;

public record RaidEmitter(
        UUID id,
        TerritoryOwnerId attackerOwnerId,
        TerritoryPosition position,
        double radius,
        double strength,
        boolean active
) {
    public RaidEmitter {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(attackerOwnerId, "attackerOwnerId");
        Objects.requireNonNull(position, "position");
        if (!Double.isFinite(radius) || radius <= 0.0D) {
            throw new IllegalArgumentException("radius must be finite and positive");
        }
        if (!Double.isFinite(strength) || strength < 0.0D) {
            throw new IllegalArgumentException("strength must be finite and non-negative");
        }
    }

    public double suppressionAt(TerritoryPosition query, DistanceModel distanceModel) {
        if (!active) {
            return 0.0D;
        }
        double distance = position.distanceTo(query, distanceModel);
        if (distance >= radius) {
            return 0.0D;
        }
        return strength * (1.0D - distance / radius);
    }
}
