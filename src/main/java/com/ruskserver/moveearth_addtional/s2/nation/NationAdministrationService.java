package com.ruskserver.moveearth_addtional.s2.nation;

import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.PeaceSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.PrisonerSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.NationUpkeepSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.NationUpkeepService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.s2.technology.NationTechnologySavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Coordinates owner-only nation changes spanning more than one saved-data store. */
public final class NationAdministrationService {
    private NationAdministrationService() { }

    public static NationSavedData.NationAdminResult updateIdentity(ServerPlayer actor, String name,
                                                                   String tag, long expectedRevision) {
        return NationSavedData.get(actor.server).updateIdentity(
                actor.getUUID(), name, tag, expectedRevision);
    }

    public static NationSavedData.NationAdminResult transferOwner(ServerPlayer actor, UUID target,
                                                                  long expectedRevision) {
        NationSavedData nations = NationSavedData.get(actor.server);
        UUID nationId = nations.nationIdFor(actor.getUUID()).orElse(null);
        boolean siegeLocked = nationId != null && SiegeSavedData.get(actor.server).isNationLocked(nationId);
        NationSavedData.NationAdminResult result = nations.transferOwner(
                actor.getUUID(), target, expectedRevision, siegeLocked);
        if (result.success()) {
            ServerPlayer successor = actor.server.getPlayerList().getPlayer(target);
            if (successor != null) successor.sendSystemMessage(MoveEarthMessage.success(Component.translatable(
                    "message.moveearth_addtional.nation.owner_received")));
        }
        return result;
    }

    public static NationSavedData.NationAdminResult disband(ServerPlayer actor, long expectedRevision) {
        NationSavedData nations = NationSavedData.get(actor.server);
        UUID nationId = nations.nationIdFor(actor.getUUID()).orElse(null);
        boolean siegeLocked = nationId != null && SiegeSavedData.get(actor.server).isNationLocked(nationId);
        boolean prisoners = nationId != null && PrisonerSavedData.get(actor.server).hasNation(nationId);
        NationSavedData.NationAdminResult validation = nations.validateDisband(
                actor.getUUID(), expectedRevision, siegeLocked, prisoners);
        if (validation.status() != NationSavedData.NationAdminStatus.ALLOWED || nationId == null) {
            return validation;
        }

        var ledger = com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData.get(actor.server);
        long treasuryBalance = ledger.balance(
                com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData.Account.nation(nationId));
        if (treasuryBalance != 0L) {
            actor.sendSystemMessage(MoveEarthMessage.warning(Component.literal(
                    "国家金庫に " + treasuryBalance + " TC が残っています。国庫画面から引き出してから解散してください")));
            return new NationSavedData.NationAdminResult(NationSavedData.NationAdminStatus.INVALID,
                    expectedRevision);
        }
        boolean unsettledDispatch = com.ruskserver.moveearth_addtional.s2.dispatch.DispatchContractSavedData
                .get(actor.server).all().stream().anyMatch(contract ->
                        (nationId.equals(contract.employerNation()) || nationId.equals(contract.providerNation()))
                                && contract.state() != com.ruskserver.moveearth_addtional.s2.dispatch.DispatchContractSavedData.State.COMPLETED
                                && contract.state() != com.ruskserver.moveearth_addtional.s2.dispatch.DispatchContractSavedData.State.CANCELLED);
        if (unsettledDispatch) {
            actor.sendSystemMessage(MoveEarthMessage.warning(Component.literal(
                    "未精算の派遣契約があります。契約を終了・精算してから解散してください")));
            return new NationSavedData.NationAdminResult(NationSavedData.NationAdminStatus.INVALID,
                    expectedRevision);
        }

        var station = com.ruskserver.moveearth_addtional.economy.MarketStationSavedData.get(actor.server)
                .forNation(nationId).orElse(null);
        if (station != null) {
            boolean hasMarketGoods = ledger.outstanding(station.id()) > 0
                    || ledger.marketOrders().stream().anyMatch(order -> order.stationId().equals(station.id()));
            ServerLevel marketLevel = actor.server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, station.dimension()));
            if (hasMarketGoods && (marketLevel == null || !marketLevel.hasChunkAt(station.pos())
                    || !marketLevel.getBlockState(station.pos()).is(
                    com.ruskserver.moveearth_addtional.block.ModBlocks.MARKET_STATION.get()))) {
                actor.sendSystemMessage(MoveEarthMessage.warning(Component.literal(
                        "Market Stationのチャンクを読み込み、注文・預託品を処理してから解散してください")));
                return new NationSavedData.NationAdminResult(NationSavedData.NationAdminStatus.INVALID,
                        expectedRevision);
            }
            if (hasMarketGoods) {
                com.ruskserver.moveearth_addtional.economy.MarketService.wreckStation(marketLevel, station.pos());
                if (ledger.outstanding(station.id()) > 0 || ledger.marketOrders().stream()
                        .anyMatch(order -> order.stationId().equals(station.id()))) {
                    actor.sendSystemMessage(MoveEarthMessage.warning(Component.literal(
                            "市場在庫の移管に失敗したため、国家解散を中止しました")));
                    return new NationSavedData.NationAdminResult(NationSavedData.NationAdminStatus.INVALID,
                            expectedRevision);
                }
            }
            if (marketLevel != null && marketLevel.hasChunkAt(station.pos())
                    && marketLevel.getBlockState(station.pos()).is(
                    com.ruskserver.moveearth_addtional.block.ModBlocks.MARKET_STATION.get()))
                marketLevel.removeBlock(station.pos(), false);
            com.ruskserver.moveearth_addtional.economy.MarketStationSavedData.get(actor.server)
                    .remove(nationId, station.id());
        }

        TerritorySavedData territories = TerritorySavedData.get(actor.server);
        NationSavedData.Nation nation = nations.nation(nationId).orElseThrow();
        String nationName = nation.name();
        java.util.Set<UUID> members = Set.copyOf(nation.members().keySet());
        List<TerritorySavedData.CoreRecord> cores = territories.cores().stream()
                .filter(core -> core.nationId().equals(nationId)).toList();
        TerritorySavedData.VaultChunk vault = territories.vaultChunk(nationId).orElse(null);
        NationSavedData.NationAdminResult result = nations.disband(actor.getUUID(), expectedRevision);
        if (!result.success()) return result;

        ledger.removeEmptyNationAccount(nationId);

        Map<net.minecraft.resources.ResourceLocation, Set<Long>> coveredChunks = coveredChunks(cores, vault);
        for (ServerLevel level : actor.server.getAllLevels()) {
            Set<Long> covered = coveredChunks.getOrDefault(level.dimension().location(), Set.of());
            ReinforcementSavedData.get(level).removeWhere(pos -> covered.contains(
                    ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4)));
        }
        List<TerritorySavedData.CoreRecord> removedCores = territories.removeNation(nationId);
        for (TerritorySavedData.CoreRecord core : removedCores) {
            ServerLevel level = actor.server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, core.dimension()));
            if (level != null) level.removeBlock(core.pos(), false);
        }
        com.ruskserver.moveearth_addtional.s2.dispatch.DispatchContractService
                .terminateNation(actor.server, nationId);
        com.ruskserver.moveearth_addtional.s2.recovery.NationRecoverySavedData
                .get(actor.server).closeNation(nationId);
        NationUpkeepSavedData.get(actor.server).removeNation(nationId);
        NationTechnologySavedData.get(actor.server).removeNation(nationId);
        com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData.get(actor.server).removeNation(nationId);
        NationUpkeepService.removeNation(nationId);
        PeaceSavedData.get(actor.server).removeNation(nationId);
        SiegeSavedData sieges = SiegeSavedData.get(actor.server);
        sieges.removeNationState(nationId);
        sieges.removeCoreState(removedCores);
        PrisonerSavedData.get(actor.server).removePendingReleaseNation(nationId);
        for (UUID memberId : members) {
            ServerPlayer online = actor.server.getPlayerList().getPlayer(memberId);
            if (online != null) online.sendSystemMessage(MoveEarthMessage.warning(Component.translatable(
                    "message.moveearth_addtional.nation.disbanded", nationName)));
        }
        return result;
    }

    private static Map<net.minecraft.resources.ResourceLocation, Set<Long>> coveredChunks(
            List<TerritorySavedData.CoreRecord> cores, TerritorySavedData.VaultChunk vault) {
        Map<net.minecraft.resources.ResourceLocation, Set<Long>> result = new HashMap<>();
        for (TerritorySavedData.CoreRecord core : cores) {
            Set<Long> dimension = result.computeIfAbsent(core.dimension(), ignored -> new HashSet<>());
            int centerX = core.pos().getX() >> 4;
            int centerZ = core.pos().getZ() >> 4;
            for (int x = centerX - core.radius(); x <= centerX + core.radius(); x++) {
                for (int z = centerZ - core.radius(); z <= centerZ + core.radius(); z++) {
                    dimension.add(ChunkPos.asLong(x, z));
                }
            }
        }
        if (vault != null) {
            result.computeIfAbsent(vault.dimension(), ignored -> new HashSet<>())
                    .add(ChunkPos.asLong(vault.chunkX(), vault.chunkZ()));
        }
        return result;
    }
}
