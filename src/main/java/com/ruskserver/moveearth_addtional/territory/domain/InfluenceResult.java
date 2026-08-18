package com.ruskserver.moveearth_addtional.territory.domain;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record InfluenceResult(
        Optional<TerritoryOwnerId> leadingOwner,
        Optional<TerritoryOwnerId> controllingOwner,
        double leadingInfluence,
        double runnerUpInfluence,
        boolean contested,
        Set<ProtectionAction> protectedActions,
        Map<TerritoryOwnerId, Double> influenceByOwner
) {
    public InfluenceResult {
        Objects.requireNonNull(leadingOwner, "leadingOwner");
        Objects.requireNonNull(controllingOwner, "controllingOwner");
        Objects.requireNonNull(protectedActions, "protectedActions");
        Objects.requireNonNull(influenceByOwner, "influenceByOwner");
        protectedActions = Set.copyOf(protectedActions);
        influenceByOwner = Map.copyOf(influenceByOwner);
    }

    public static InfluenceResult unclaimed() {
        return new InfluenceResult(Optional.empty(), Optional.empty(), 0.0D, 0.0D,
                false, Set.of(), Map.of());
    }

    public boolean protects(ProtectionAction action) {
        return protectedActions.contains(action);
    }
}
