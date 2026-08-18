package com.ruskserver.moveearth_addtional.territory.service;

import com.ruskserver.moveearth_addtional.territory.domain.InfluenceResult;
import com.ruskserver.moveearth_addtional.territory.domain.ProtectionAction;
import com.ruskserver.moveearth_addtional.territory.domain.TerritoryOwnerId;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class TerritoryProtectionService {
    private TerritoryProtectionService() {
    }

    public static ProtectionDecision authorize(
            ServerLevel level,
            BlockPos pos,
            ServerPlayer actor,
            ProtectionAction action
    ) {
        if (!level.getServer().isDedicatedServer() || actor.hasPermissions(2)) {
            return ProtectionDecision.permit();
        }

        InfluenceResult influence = TerritoryInfluenceService.evaluate(level, pos);
        if (influence.controllingOwner().isEmpty() || !influence.protects(action)) {
            return ProtectionDecision.permit();
        }

        TerritoryOwnerId ownerId = influence.controllingOwner().orElseThrow();
        if (TerritoryMembershipService.isMember(level, actor, ownerId)) {
            return ProtectionDecision.permit();
        }
        return ProtectionDecision.deny(ownerId, influence);
    }

    public static boolean isProtected(ServerLevel level, BlockPos pos, ProtectionAction action) {
        if (!level.getServer().isDedicatedServer()) {
            return false;
        }
        InfluenceResult influence = TerritoryInfluenceService.evaluate(level, pos);
        return influence.controllingOwner().isPresent() && influence.protects(action);
    }

    public record ProtectionDecision(
            boolean allowed,
            TerritoryOwnerId ownerId,
            InfluenceResult influence
    ) {
        private static ProtectionDecision permit() {
            return new ProtectionDecision(true, null, null);
        }

        private static ProtectionDecision deny(TerritoryOwnerId ownerId, InfluenceResult influence) {
            return new ProtectionDecision(false, ownerId, influence);
        }
    }
}
