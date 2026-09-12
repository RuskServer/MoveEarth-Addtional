package com.ruskserver.moveearth_addtional.s2.siege;

import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.NationUpkeepService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritoryCoreHealthService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Server-authoritative GUI actions for vaults, peace proposals, and surrender. */
public final class SiegeActionService {
    private SiegeActionService() { }

    public static Result setVault(ServerPlayer player, long expectedRevision) {
        NationSavedData nations = NationSavedData.get(player.server);
        UUID nationId = nations.nationIdFor(player.getUUID()).orElse(null);
        if (expectedRevision != nations.revision()) return Result.STALE;
        if (nationId == null || !nations.can(player.getUUID(), S2Permission.MANAGE_TERRITORY)) {
            return Result.NO_PERMISSION;
        }
        if (SiegeSavedData.get(player.server).isNationLocked(nationId)) return Result.SIEGE_LOCKED;
        return switch (TerritorySavedData.get(player.server).setVaultChunk(nationId,
                player.level().dimension().location(), player.blockPosition())) {
            case UPDATED -> Result.VAULT_UPDATED;
            case UNCHANGED -> Result.VAULT_UNCHANGED;
            case COOLDOWN -> Result.VAULT_COOLDOWN;
            case NOT_CONTROLLED -> Result.VAULT_NOT_CONTROLLED;
            case CAPITAL_CHUNK -> Result.VAULT_CAPITAL_CHUNK;
            case INVALID -> Result.INVALID;
        };
    }

    public static Result proposePeace(ServerPlayer player, UUID opponentNation, long gold,
                                      long expectedRevision) {
        NationSavedData nations = NationSavedData.get(player.server);
        UUID nationId = nations.nationIdFor(player.getUUID()).orElse(null);
        if (expectedRevision != nations.revision()) return Result.STALE;
        if (nationId == null || !nations.can(player.getUUID(), S2Permission.MANAGE_DIPLOMACY)) {
            return Result.NO_PERMISSION;
        }
        SiegeSavedData sieges = SiegeSavedData.get(player.server);
        if (!sieges.hasConflictBetween(nationId, opponentNation)
                && PrisonerSavedData.get(player.server).countBetween(nationId, opponentNation) == 0) {
            return Result.CONFLICT_NOT_FOUND;
        }
        PeaceSavedData.ProposalResult result = PeaceSavedData.get(player.server).propose(
                nationId, opponentNation, gold, S2TerritoryConfig.peaceProposalTicks());
        if (result.status() != PeaceSavedData.Status.PROPOSED) return Result.INVALID;
        notifyNation(player, opponentNation, Component.translatable(
                "message.moveearth_addtional.peace.received", nations.nation(nationId)
                        .map(NationSavedData.Nation::name).orElse("?"), gold));
        return Result.PEACE_PROPOSED;
    }

    public static Result respondPeace(ServerPlayer player, UUID proposalId, boolean accept,
                                      long expectedRevision) {
        NationSavedData nations = NationSavedData.get(player.server);
        UUID nationId = nations.nationIdFor(player.getUUID()).orElse(null);
        if (expectedRevision != nations.revision()) return Result.STALE;
        if (nationId == null || !nations.can(player.getUUID(), S2Permission.MANAGE_DIPLOMACY)) {
            return Result.NO_PERMISSION;
        }
        PeaceSavedData peace = PeaceSavedData.get(player.server);
        PeaceSavedData.Proposal proposal = peace.proposal(proposalId).orElse(null);
        if (proposal == null || !proposal.receiverNation().equals(nationId)) {
            return Result.PROPOSAL_NOT_FOUND;
        }
        if (!accept) {
            peace.remove(proposalId);
            notifyNation(player, proposal.proposerNation(), Component.translatable(
                    "message.moveearth_addtional.peace.rejected"));
            return Result.PEACE_REJECTED;
        }
        SiegeSavedData sieges = SiegeSavedData.get(player.server);
        if (!sieges.hasConflictBetween(proposal.proposerNation(), proposal.receiverNation())
                && PrisonerSavedData.get(player.server).countBetween(
                proposal.proposerNation(), proposal.receiverNation()) == 0) {
            peace.remove(proposalId);
            return Result.CONFLICT_NOT_FOUND;
        }
        if (proposal.returnPrisoners() && !PrisonerService.canReturnAll(player.server,
                proposal.proposerNation(), proposal.receiverNation())) {
            return Result.PRISONER_DATA_INVALID;
        }
        NationUpkeepService.TransferResult transfer = NationUpkeepService.transferGold(player.server,
                proposal.proposerNation(), proposal.receiverNation(), proposal.goldCompensation());
        if (transfer != NationUpkeepService.TransferResult.SUCCESS) {
            return switch (transfer) {
                case PAYER_ACCOUNT_MISSING -> Result.PAYER_ACCOUNT_MISSING;
                case RECEIVER_ACCOUNT_MISSING -> Result.RECEIVER_ACCOUNT_MISSING;
                case INSUFFICIENT_FUNDS -> Result.INSUFFICIENT_FUNDS;
                default -> Result.TRANSFER_FAILED;
            };
        }
        SiegeSavedData.ConflictEndResult ended = sieges.endConflictsBetween(
                proposal.proposerNation(), proposal.receiverNation());
        int returnedPrisoners = proposal.returnPrisoners() ? PrisonerService.returnAll(player.server,
                proposal.proposerNation(), proposal.receiverNation()) : 0;
        TerritorySavedData territories = TerritorySavedData.get(player.server);
        for (SiegeSavedData.FallenRecord fallen : ended.fallen()) {
            territories.recoverCore(fallen.coreId(), S2TerritoryConfig.siegeCounterRecoveryPercent())
                    .ifPresent(core -> TerritoryCoreHealthService.syncCore(player.server, core));
            SiegeService.syncFallVisuals(player.server, fallen);
        }
        sieges.startPeaceTruce(proposal.proposerNation(), proposal.receiverNation(),
                S2TerritoryConfig.peaceTruceTicks());
        peace.removeBetween(proposal.proposerNation(), proposal.receiverNation());
        String proposerName = nations.nation(proposal.proposerNation())
                .map(NationSavedData.Nation::name).orElse("?");
        String receiverName = nations.nation(proposal.receiverNation())
                .map(NationSavedData.Nation::name).orElse("?");
        player.server.getPlayerList().broadcastSystemMessage(MoveEarthMessage.success(Component.translatable(
                "message.moveearth_addtional.peace.accepted", proposerName, receiverName,
                proposal.goldCompensation(), returnedPrisoners,
                formatTicks(S2TerritoryConfig.peaceTruceTicks()))), false);
        return Result.PEACE_ACCEPTED;
    }

    public static Result cancelPeace(ServerPlayer player, UUID proposalId, long expectedRevision) {
        NationSavedData nations = NationSavedData.get(player.server);
        UUID nationId = nations.nationIdFor(player.getUUID()).orElse(null);
        if (expectedRevision != nations.revision()) return Result.STALE;
        if (nationId == null || !nations.can(player.getUUID(), S2Permission.MANAGE_DIPLOMACY)) {
            return Result.NO_PERMISSION;
        }
        PeaceSavedData peace = PeaceSavedData.get(player.server);
        PeaceSavedData.Proposal proposal = peace.proposal(proposalId).orElse(null);
        return proposal != null && proposal.proposerNation().equals(nationId) && peace.remove(proposalId)
                ? Result.PEACE_CANCELLED : Result.PROPOSAL_NOT_FOUND;
    }

    public static Result surrender(ServerPlayer player, UUID siegeId, long expectedRevision) {
        NationSavedData nations = NationSavedData.get(player.server);
        UUID nationId = nations.nationIdFor(player.getUUID()).orElse(null);
        if (expectedRevision != nations.revision()) return Result.STALE;
        if (nationId == null || !nations.can(player.getUUID(), S2Permission.MANAGE_SIEGE)) {
            return Result.NO_PERMISSION;
        }
        SiegeSavedData sieges = SiegeSavedData.get(player.server);
        SiegeSavedData.SiegeRecord active = sieges.activeById(siegeId).orElse(null);
        if (active != null) {
            if (active.attackerNation().equals(nationId)) {
                sieges.removeActive(siegeId);
                sieges.startRetryCooldown(active.attackerNation(), active.defenderNation(),
                        S2TerritoryConfig.siegeRetryCooldownTicks());
                PeaceSavedData.get(player.server).removeBetween(active.attackerNation(), active.defenderNation());
                broadcastExit(player, active.attackerNation(), active.defenderNation(), true);
                return Result.ATTACK_WITHDRAWN;
            }
            if (!active.defenderNation().equals(nationId)) return Result.CONFLICT_NOT_FOUND;
            TerritorySavedData territories = TerritorySavedData.get(player.server);
            TerritorySavedData.CoreRecord core = territories.cores().stream()
                    .filter(value -> value.id().equals(active.coreId())).findFirst().orElse(null);
            if (core == null) return Result.CONFLICT_NOT_FOUND;
            TerritorySavedData.CoreRecord depleted = territories.damageCore(
                    core.dimension(), core.pos(), core.health()).orElse(core);
            SiegeSavedData.FallenResult fallen = sieges.markFallen(active, depleted);
            if (fallen.fallen() != null) {
                territories.markCoreFallen(core.id(), false)
                        .ifPresent(value -> TerritoryCoreHealthService.syncCore(player.server, value));
                SiegeService.broadcastFall(player.server, nations, fallen.fallen());
            }
            return Result.DEFENDER_SURRENDERED;
        }
        SiegeSavedData.FallenRecord fallen = sieges.fallenBySiegeId(siegeId).orElse(null);
        if (fallen == null) return Result.CONFLICT_NOT_FOUND;
        if (fallen.attackerNation().equals(nationId)) {
            sieges.removeFallen(fallen.coreId());
            sieges.startRetryCooldown(fallen.attackerNation(), fallen.defenderNation(),
                    S2TerritoryConfig.siegeRetryCooldownTicks());
            TerritorySavedData.get(player.server).recoverCore(fallen.coreId(),
                    S2TerritoryConfig.siegeCounterRecoveryPercent())
                    .ifPresent(core -> TerritoryCoreHealthService.syncCore(player.server, core));
            SiegeService.syncFallVisuals(player.server, fallen);
            broadcastExit(player, fallen.attackerNation(), fallen.defenderNation(), true);
            return Result.ATTACK_WITHDRAWN;
        }
        if (!fallen.defenderNation().equals(nationId)) return Result.CONFLICT_NOT_FOUND;
        SiegeService.finalizeFall(player.server, nations, sieges, fallen);
        return Result.DEFENDER_SURRENDERED;
    }

    private static void broadcastExit(ServerPlayer player, UUID attackerId, UUID defenderId,
                                      boolean attackerWithdrew) {
        NationSavedData nations = NationSavedData.get(player.server);
        String attacker = nations.nation(attackerId).map(NationSavedData.Nation::name).orElse("?");
        String defender = nations.nation(defenderId).map(NationSavedData.Nation::name).orElse("?");
        player.server.getPlayerList().broadcastSystemMessage(MoveEarthMessage.warning(Component.translatable(
                attackerWithdrew ? "message.moveearth_addtional.siege.attack_withdrawn"
                        : "message.moveearth_addtional.siege.defender_surrendered",
                attacker, defender)), false);
    }

    private static void notifyNation(ServerPlayer sender, UUID nationId, Component body) {
        NationSavedData.get(sender.server).nation(nationId).ifPresent(nation -> nation.members().keySet()
                .forEach(member -> {
                    ServerPlayer online = sender.server.getPlayerList().getPlayer(member);
                    if (online != null) online.sendSystemMessage(MoveEarthMessage.info(body));
                }));
    }

    private static String formatTicks(long ticks) {
        long seconds = Math.max(0L, (ticks + 19L) / 20L);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L);
    }

    public enum Result {
        VAULT_UPDATED, VAULT_UNCHANGED, VAULT_COOLDOWN, VAULT_NOT_CONTROLLED, VAULT_CAPITAL_CHUNK, PEACE_PROPOSED,
        PEACE_ACCEPTED, PEACE_REJECTED, PEACE_CANCELLED, ATTACK_WITHDRAWN,
        DEFENDER_SURRENDERED, PAYER_ACCOUNT_MISSING, RECEIVER_ACCOUNT_MISSING,
        INSUFFICIENT_FUNDS, TRANSFER_FAILED, PRISONER_DATA_INVALID, PROPOSAL_NOT_FOUND, CONFLICT_NOT_FOUND,
        SIEGE_LOCKED, NO_PERMISSION, STALE, INVALID;

        public boolean success() {
            return switch (this) {
                case VAULT_UPDATED, PEACE_PROPOSED, PEACE_ACCEPTED, PEACE_REJECTED,
                     PEACE_CANCELLED, ATTACK_WITHDRAWN, DEFENDER_SURRENDERED -> true;
                default -> false;
            };
        }
    }
}
