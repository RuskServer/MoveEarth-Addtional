package com.ruskserver.moveearth_addtional.territory.domain;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record TerritoryMembership(TerritoryOwnerId ownerId, Set<String> memberNames) {
    public TerritoryMembership {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(memberNames, "memberNames");
        memberNames = Set.copyOf(memberNames);
    }

    public boolean includes(UUID playerId, String scoreboardName) {
        Objects.requireNonNull(playerId, "playerId");
        return ownerId.value().equals(playerId)
                || (scoreboardName != null && memberNames.contains(scoreboardName));
    }
}
