package com.ruskserver.moveearth_addtional.territory.service;

import com.ruskserver.moveearth_addtional.data.PlayerWhitelistSavedData;
import com.ruskserver.moveearth_addtional.territory.domain.TerritoryMembership;
import com.ruskserver.moveearth_addtional.territory.domain.TerritoryOwnerId;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public final class TerritoryMembershipService {
    private TerritoryMembershipService() {
    }

    public static TerritoryMembership membership(ServerLevel level, TerritoryOwnerId ownerId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(ownerId, "ownerId");
        return new TerritoryMembership(
                ownerId,
                PlayerWhitelistSavedData.get(level).getWhitelist(ownerId.value())
        );
    }

    public static boolean isMember(ServerLevel level, ServerPlayer player, TerritoryOwnerId ownerId) {
        Objects.requireNonNull(player, "player");
        return membership(level, ownerId).includes(player.getUUID(), player.getScoreboardName());
    }
}
