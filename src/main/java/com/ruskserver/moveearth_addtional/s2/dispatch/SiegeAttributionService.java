package com.ruskserver.moveearth_addtional.s2.dispatch;

import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeParticipationSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.Optional;

/** Resolves temporary combat affiliation without granting ordinary nation permissions. */
public final class SiegeAttributionService {
    private SiegeAttributionService() { }

    public static UUID activeCombatNation(MinecraftServer server, UUID playerId) {
        var participation = SiegeParticipationSavedData.get(server).forPlayer(playerId).orElse(null);
        if (participation == null) return NationSavedData.get(server).nationIdFor(playerId).orElse(null);
        var contract = participation.contractId() == null ? null
                : DispatchContractSavedData.get(server).byId(participation.contractId()).orElse(null);
        if (contract == null || contract.state() != DispatchContractSavedData.State.ACTIVE
                || !participation.siegeId().equals(contract.siegeId())) {
            return NationSavedData.get(server).nationIdFor(playerId).orElse(null);
        }
        return participation.combatNation();
    }

    public static UUID nationForTarget(ServerPlayer player, ServerLevel level, BlockPos target) {
        UUID fallback = NationSavedData.get(player.server).nationIdFor(player.getUUID()).orElse(null);
        return combatNationForTarget(player.server, player.getUUID(), level, target).orElse(fallback);
    }

    public static Optional<UUID> combatNationForTarget(MinecraftServer server, UUID playerId,
                                                        ServerLevel level, BlockPos target) {
        var participation = SiegeParticipationSavedData.get(server).forPlayer(playerId).orElse(null);
        if (participation == null) return Optional.empty();
        DispatchContractSavedData.Contract contract = participation.contractId() == null ? null
                : DispatchContractSavedData.get(server).byId(participation.contractId()).orElse(null);
        if (contract == null || contract.state() != DispatchContractSavedData.State.ACTIVE) return Optional.empty();
        TerritorySavedData.CoreRecord core = TerritorySavedData.get(server)
                .controllingCore(server, level.dimension().location(), target).orElse(null);
        if (core == null || !core.id().equals(contract.targetCoreId())) return Optional.empty();
        boolean matching = SiegeSavedData.get(server).activeById(participation.siegeId())
                .map(value -> value.coreId().equals(core.id())).orElseGet(() ->
                        SiegeSavedData.get(server).fallenBySiegeId(participation.siegeId())
                                .map(value -> value.coreId().equals(core.id())).orElse(false));
        return matching ? Optional.of(participation.combatNation()) : Optional.empty();
    }
}
