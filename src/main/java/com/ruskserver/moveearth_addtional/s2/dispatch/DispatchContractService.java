package com.ruskserver.moveearth_addtional.s2.dispatch;

import com.ruskserver.moveearth_addtional.CompatEventHandler;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.analytics.activity.PlayerActivityTracker;
import com.ruskserver.moveearth_addtional.config.RecoveryDispatchConfig;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationService;
import com.ruskserver.moveearth_addtional.s2.recovery.EconomyGateway;
import com.ruskserver.moveearth_addtional.s2.recovery.NationRecoverySavedData;
import com.ruskserver.moveearth_addtional.s2.recovery.RecoveryFundSavedData;
import com.ruskserver.moveearth_addtional.s2.recovery.RecoveryFundService;
import com.ruskserver.moveearth_addtional.s2.recovery.WarHistorySavedData;
import com.ruskserver.moveearth_addtional.s2.siege.PrisonerSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeParticipationSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.s2.time.OpenTimeService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class DispatchContractService {
    private DispatchContractService() { }

    public static ActionResult create(ServerPlayer actor, UUID providerNation, UUID targetCoreId,
                                      UUID requestedOpponent,
                                      DispatchContractSavedData.Side side, Set<UUID> participants,
                                      long pricePerMinute, long maxOpenTicks, long requestedSubsidy) {
        if (!RecoveryDispatchConfig.dispatchEnabled()) return ActionResult.fail("dispatch_disabled");
        NationSavedData nations = NationSavedData.get(actor.server);
        UUID employer = nations.nationIdFor(actor.getUUID()).orElse(null);
        if (employer == null || !nations.can(actor.getUUID(), S2Permission.MANAGE_SIEGE)
                || providerNation == null || providerNation.equals(employer) || side == null
                || participants == null || participants.isEmpty()) return ActionResult.fail("invalid_contract");
        TerritorySavedData.CoreRecord core = TerritorySavedData.get(actor.server).coreById(targetCoreId).orElse(null);
        if (core == null) return ActionResult.fail("target_missing");
        UUID opponent = side == DispatchContractSavedData.Side.DEFENSE ? requestedOpponent : core.nationId();
        if (side == DispatchContractSavedData.Side.DEFENSE && !core.nationId().equals(employer)
                || side == DispatchContractSavedData.Side.DEFENSE
                && (opponent == null || opponent.equals(employer))
                || side == DispatchContractSavedData.Side.OFFENSE && core.nationId().equals(employer)) {
            return ActionResult.fail("invalid_side");
        }
        if (nations.isAllied(employer, opponent) || nations.isAllied(providerNation, opponent)
                || SiegeSavedData.get(actor.server).isPeaceTruceActive(employer, opponent)
                || SiegeSavedData.get(actor.server).isPeaceTruceActive(providerNation, opponent)) {
            return ActionResult.fail("diplomacy_blocks");
        }
        if (side == DispatchContractSavedData.Side.OFFENSE) {
            NationRecoverySavedData.Episode recovery = NationRecoverySavedData.get(actor.server)
                    .eligibleForNation(employer, OpenTimeService.now(actor.server)).orElse(null);
            if (recovery != null && !NationRecoverySavedData.get(actor.server).protectionWaived(recovery.id())) {
                return ActionResult.fail("recovery_protection_confirmation");
            }
        }
        NationSavedData.Nation provider = nations.nation(providerNation).orElse(null);
        if (provider == null || participants.stream().anyMatch(id -> !provider.members().containsKey(id))) {
            return ActionResult.fail("invalid_participants");
        }
        if (participants.stream().anyMatch(id -> DispatchContractSavedData.get(actor.server)
                .reservedForPlayer(id).isPresent())) return ActionResult.fail("participant_busy");
        long boundedTicks = Math.min(RecoveryDispatchConfig.dispatchMaxOpenTicks(), Math.max(1L, maxOpenTicks));
        long maximumCost = DispatchContractPolicy.maximumCost(pricePerMinute, boundedTicks, participants.size());
        if (maximumCost > 0L && !RecoveryDispatchConfig.dispatchMoneyEnabled()) {
            return ActionResult.fail("dispatch_money_disabled");
        }
        DispatchContractSavedData.Contract created = DispatchContractSavedData.get(actor.server).create(
                employer, providerNation, targetCoreId, opponent, side, participants, pricePerMinute,
                boundedTicks, Math.min(maximumCost, Math.max(0L, requestedSubsidy)), OpenTimeService.now(actor.server));
        WarHistorySavedData.get(actor.server).append(OpenTimeService.now(actor.server),
                WarHistorySavedData.Type.DISPATCH_CREATED, WarHistorySavedData.Visibility.NATION,
                employer, providerNation, created.id(), List.of(side.name()));
        NationNotificationService.publish(actor.server, List.of(employer, providerNation),
                NationNotificationSavedData.EventType.DISPATCH_CREATED, null, null, null,
                List.of(created.id().toString(), side.name()));
        return ActionResult.ok(created);
    }

    public static ActionResult approve(ServerPlayer actor, UUID contractId, long revision, boolean subsidyApproval) {
        DispatchContractSavedData data = DispatchContractSavedData.get(actor.server);
        DispatchContractSavedData.Contract contract = data.byId(contractId).orElse(null);
        if (contract == null) return ActionResult.fail("contract_missing");
        NationSavedData nations = NationSavedData.get(actor.server);
        UUID actorNation = nations.nationIdFor(actor.getUUID()).orElse(null);
        boolean employer = !subsidyApproval && contract.employerNation().equals(actorNation)
                && nations.can(actor.getUUID(), S2Permission.MANAGE_SIEGE);
        boolean provider = !subsidyApproval && contract.providerNation().equals(actorNation)
                && nations.can(actor.getUUID(), S2Permission.MANAGE_DISPATCH);
        if (provider) {
            NationRecoverySavedData.Episode recovery = NationRecoverySavedData.get(actor.server)
                    .eligibleForNation(contract.providerNation(), OpenTimeService.now(actor.server)).orElse(null);
            if (recovery != null && !NationRecoverySavedData.get(actor.server)
                    .protectionWaived(recovery.id())) {
                return ActionResult.fail("recovery_protection_confirmation");
            }
        }
        boolean adminSubsidy = subsidyApproval && actor.hasPermissions(2);
        if (!employer && !provider && !adminSubsidy) return ActionResult.fail("no_permission");
        DispatchContractSavedData.Contract updated = data.approve(contractId, employer, provider,
                adminSubsidy, revision);
        return updated == null ? ActionResult.fail("stale") : ActionResult.ok(updated);
    }

    public static ActionResult consent(ServerPlayer actor, UUID contractId, long revision, boolean accepted) {
        DispatchContractSavedData.Contract updated = DispatchContractSavedData.get(actor.server)
                .consent(contractId, actor.getUUID(), accepted, revision);
        return updated == null ? ActionResult.fail("stale_or_not_participant") : ActionResult.ok(updated);
    }

    public static ActionResult fund(ServerPlayer actor, UUID contractId, long revision) {
        DispatchContractSavedData data = DispatchContractSavedData.get(actor.server);
        DispatchContractSavedData.Contract original = data.byId(contractId).orElse(null);
        if (original == null) return ActionResult.fail("contract_missing");
        NationSavedData nations = NationSavedData.get(actor.server);
        if (!original.employerNation().equals(nations.nationIdFor(actor.getUUID()).orElse(null))
                || !nations.can(actor.getUUID(), S2Permission.MANAGE_TREASURY)) return ActionResult.fail("no_permission");
        if (RecoveryDispatchConfig.dispatchSubsidyApprovalRequired() && original.requestedSubsidy() > 0L
                && !original.subsidyApproved()) return ActionResult.fail("subsidy_pending");
        DispatchContractSavedData.Contract funding = data.beginFunding(contractId, revision);
        if (funding == null) return ActionResult.fail("not_ready");
        long maximum = DispatchContractPolicy.maximumCost(funding.pricePerOpenMinute(), funding.maximumOpenTicks(),
                funding.participants().size());
        long subsidy = 0L;
        UUID fundTransaction = null;
        NationRecoverySavedData.Episode episode = NationRecoverySavedData.get(actor.server)
                .eligibleForNation(funding.employerNation(), OpenTimeService.now(actor.server)).orElse(null);
        if (episode != null && funding.requestedSubsidy() > 0L) {
            RecoveryFundService.Result reserve = RecoveryFundService.reserveAid(actor.server, episode,
                    funding.id(), Math.min(maximum, funding.requestedSubsidy()),
                    RecoveryFundSavedData.Type.DISPATCH_SUBSIDY);
            if (reserve.success()) {
                fundTransaction = reserve.transactionId();
                subsidy = RecoveryFundSavedData.get(actor.server).transaction(fundTransaction)
                        .map(RecoveryFundSavedData.Transaction::amount).orElse(0L);
            }
        }
        if (funding.requestedSubsidy() > 0L && subsidy == 0L) {
            data.fundingRejected(funding.id(), "requested_subsidy_unavailable");
            return ActionResult.fail("fund_limit");
        }
        long own = Math.max(0L, maximum - subsidy);
        UUID employerTransaction = null;
        if (own > 0L) {
            RecoveryFundSavedData journal = RecoveryFundSavedData.get(actor.server);
            var transaction = journal.prepare(RecoveryFundSavedData.Type.DISPATCH_EMPLOYER,
                    funding.employerNation(), funding.id(), own, OpenTimeService.now(actor.server), "dispatch_escrow");
            employerTransaction = transaction.id();
            EconomyGateway.Result withdrawal = EconomyGateway.withdraw(actor.server, funding.employerNation(), own,
                    transaction.id(), "dispatch_escrow_funding");
            if (withdrawal != EconomyGateway.Result.SUCCESS) {
                if (withdrawal == EconomyGateway.Result.ERROR) {
                    journal.reviewRequired(transaction.id(), "withdrawal:" + withdrawal.name());
                } else {
                    journal.abortPrepared(transaction.id(), "withdrawal:" + withdrawal.name());
                }
                if (fundTransaction != null) RecoveryFundService.refundAid(actor.server, episode.id(), fundTransaction);
                if (withdrawal == EconomyGateway.Result.ERROR) {
                    data.review(funding.id(), "employer_withdrawal:" + withdrawal.name());
                } else {
                    data.fundingRejected(funding.id(), "funding_rejected:" + withdrawal.name());
                }
                return ActionResult.fail(withdrawal.name().toLowerCase(java.util.Locale.ROOT));
            }
            journal.markWithdrawn(transaction.id());
            journal.completeExternal(transaction.id());
        }
        DispatchContractSavedData.Contract funded = data.funded(funding.id(), own, subsidy,
                employerTransaction, fundTransaction);
        return funded == null ? ActionResult.fail("funding_failed") : ActionResult.ok(funded);
    }

    public static void bindEligible(MinecraftServer server, SiegeSavedData.SiegeRecord siege) {
        if (!RecoveryDispatchConfig.dispatchEnabled() || siege == null || siege.individualAttacker()) return;
        DispatchContractSavedData data = DispatchContractSavedData.get(server);
        NationSavedData nations = NationSavedData.get(server);
        DispatchContractSavedData.Contract selected = data.all().stream()
                .filter(contract -> contract.state() == DispatchContractSavedData.State.FUNDED
                        && contract.targetCoreId().equals(siege.coreId()))
                .filter(contract -> nations.nation(contract.providerNation()).map(provider ->
                        contract.participants().stream().allMatch(provider.members()::containsKey)).orElse(false))
                .filter(contract -> contract.side() == DispatchContractSavedData.Side.OFFENSE
                    ? contract.employerNation().equals(siege.attackerNation())
                    && contract.opponentNation().equals(siege.defenderNation())
                    : contract.employerNation().equals(siege.defenderNation())
                    && contract.opponentNation().equals(siege.attackerNation()))
                .min(java.util.Comparator.comparingLong(DispatchContractSavedData.Contract::createdAt)
                        .thenComparing(DispatchContractSavedData.Contract::id)).orElse(null);
        if (selected != null) {
            DispatchContractSavedData.Contract active = data.bind(selected.id(), siege.id(), OpenTimeService.now(server));
            if (active == null) return;
            for (UUID player : active.participants()) {
                SiegeParticipationSavedData.get(server).register(player, active.providerNation(),
                        active.employerNation(), siege.id(), active.id(), active.side().name());
            }
            WarHistorySavedData.get(server).append(OpenTimeService.now(server),
                    WarHistorySavedData.Type.DISPATCH_ACTIVATED, WarHistorySavedData.Visibility.PUBLIC,
                    active.employerNation(), active.providerNation(), active.id(), List.of());
            NationNotificationService.publish(server, List.of(active.employerNation(), active.providerNation()),
                    NationNotificationSavedData.EventType.DISPATCH_ACTIVATED, null, null, null,
                    List.of(active.id().toString()));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % 20L != 13L) return;
        DispatchContractSavedData data = DispatchContractSavedData.get(server);
        if (!RecoveryDispatchConfig.dispatchEnabled()) {
            for (DispatchContractSavedData.Contract contract : data.all()) {
                if (contract.state() == DispatchContractSavedData.State.ACTIVE
                        || contract.state() == DispatchContractSavedData.State.FUNDED
                        || contract.state() == DispatchContractSavedData.State.FUNDING) {
                    settle(server, contract.id(), "feature_disabled");
                }
            }
            return;
        }
        if (!OpenTimeService.isOpen(server)) return;
        long now = OpenTimeService.now(server);
        NationSavedData nations = NationSavedData.get(server);
        for (DispatchContractSavedData.Contract contract : data.all()) {
            if (contract.state() != DispatchContractSavedData.State.ACTIVE
                    && contract.state() != DispatchContractSavedData.State.COMPLETED
                    && contract.state() != DispatchContractSavedData.State.CANCELLED
                    && contract.state() != DispatchContractSavedData.State.REVIEW_REQUIRED
                    && now - contract.createdAt() >= contract.maximumOpenTicks()) {
                if (contract.state() == DispatchContractSavedData.State.FUNDED
                        || contract.state() == DispatchContractSavedData.State.FUNDING) {
                    settle(server, contract.id(), "reservation_expired");
                } else {
                    DispatchContractSavedData.Contract finished = data.finish(
                            contract.id(), true, "reservation_expired");
                    publishFinished(server, finished, true, "reservation_expired", 0L);
                }
                continue;
            }
            if (contract.state() != DispatchContractSavedData.State.ACTIVE) continue;
            boolean invalidParties = nations.nation(contract.employerNation()).isEmpty()
                    || nations.nation(contract.providerNation()).isEmpty()
                    || contract.participants().stream().anyMatch(player -> !contract.providerNation().equals(
                    nations.nationIdFor(player).orElse(null)));
            if (invalidParties) { settle(server, contract.id(), "party_changed"); continue; }
            boolean siegeAlive = SiegeSavedData.get(server).activeById(contract.siegeId()).isPresent()
                    || SiegeSavedData.get(server).fallenBySiegeId(contract.siegeId()).isPresent();
            if (!siegeAlive) { settle(server, contract.id(), "siege_ended"); continue; }
            Map<UUID, Long> additions = new LinkedHashMap<>();
            boolean atLimit = true;
            for (UUID playerId : contract.participants()) {
                long billed = contract.billedTicks().getOrDefault(playerId, 0L);
                if (billed < contract.maximumOpenTicks()) atLimit = false;
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null && billable(server, player, contract) && billed < contract.maximumOpenTicks()) {
                    additions.put(playerId, 20L);
                }
            }
            if (!additions.isEmpty()) data.bill(contract.id(), additions, now);
            if (atLimit) settle(server, contract.id(), "time_limit");
        }
    }

    private static boolean billable(MinecraftServer server, ServerPlayer player,
                                    DispatchContractSavedData.Contract contract) {
        if (!player.isAlive() || player.isSpectator() || CompatEventHandler.playerDownedTicks(player) >= 0
                || PlayerActivityTracker.INSTANCE.isAfk(player.getUUID(), System.currentTimeMillis())) return false;
        PrisonerSavedData prisoners = PrisonerSavedData.get(server);
        if (prisoners.prisoner(player.getUUID()).isPresent() || prisoners.custody(player.getUUID()).isPresent()) return false;
        SiegeSavedData.SiegeRecord active = SiegeSavedData.get(server).activeById(contract.siegeId()).orElse(null);
        SiegeSavedData.FallenRecord fallen = SiegeSavedData.get(server).fallenBySiegeId(contract.siegeId()).orElse(null);
        var dimension = active != null ? active.dimension() : fallen == null ? null : fallen.dimension();
        var center = active != null ? active.corePos() : fallen == null ? null : fallen.corePos();
        if (dimension == null || center == null || !player.serverLevel().dimension().location().equals(dimension)) return false;
        double radius = RecoveryDispatchConfig.dispatchBattlefieldRadius();
        return player.distanceToSqr(center.getCenter()) <= radius * radius;
    }

    public static boolean settle(MinecraftServer server, UUID contractId, String reason) {
        DispatchContractSavedData data = DispatchContractSavedData.get(server);
        DispatchContractSavedData.Contract active = data.byId(contractId).orElse(null);
        if (active == null) return false;
        DispatchContractSavedData.Contract settling = data.settling(contractId, reason);
        if (settling == null) return false;
        long earned = settling.billedTicks().values().stream()
                .mapToLong(ticks -> DispatchContractPolicy.earned(settling.pricePerOpenMinute(), ticks)).sum();
        earned = Math.min(earned, saturatedAdd(settling.ownEscrow(), settling.subsidyEscrow()));
        long subsidyUsed = Math.min(earned, settling.subsidyEscrow());
        long ownUsed = Math.max(0L, earned - subsidyUsed);
        UUID providerPayment = UUID.nameUUIDFromBytes((contractId + ":provider_payment")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (EconomyGateway.deposit(server, settling.providerNation(), earned,
                providerPayment, "dispatch_provider_payment") != EconomyGateway.Result.SUCCESS) {
            data.review(contractId, "provider_deposit_failed");
            return false;
        }
        long ownRefund = Math.max(0L, settling.ownEscrow() - ownUsed);
        UUID employerRefund = UUID.nameUUIDFromBytes((contractId + ":employer_refund")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (EconomyGateway.deposit(server, settling.employerNation(), ownRefund,
                employerRefund, "dispatch_employer_refund") != EconomyGateway.Result.SUCCESS) {
            data.review(contractId, "employer_refund_failed");
            return false;
        }
        if (settling.fundTransactionId() != null) {
            NationRecoverySavedData.Episode episode = NationRecoverySavedData.get(server)
                    .forNation(settling.employerNation()).stream()
                    .filter(value -> value.aidReserved() >= settling.subsidyEscrow()).findFirst().orElse(null);
            if (episode == null || !RecoveryFundService.consumeAid(server, episode.id(),
                    settling.fundTransactionId(), subsidyUsed)) {
                data.review(contractId, "fund_settlement_failed");
                return false;
            }
        }
        SiegeParticipationSavedData.get(server).removeSiege(settling.siegeId());
        boolean cancelled = reason != null && reason.startsWith("cancelled");
        DispatchContractSavedData.Contract finished = data.finish(contractId, cancelled, reason);
        publishFinished(server, finished, cancelled, reason, earned);
        return finished != null;
    }

    public static ActionResult cancel(ServerPlayer actor, UUID contractId, String reason) {
        DispatchContractSavedData data = DispatchContractSavedData.get(actor.server);
        DispatchContractSavedData.Contract contract = data.byId(contractId).orElse(null);
        if (contract == null) return ActionResult.fail("contract_missing");
        NationSavedData nations = NationSavedData.get(actor.server);
        UUID nation = nations.nationIdFor(actor.getUUID()).orElse(null);
        boolean allowed = actor.hasPermissions(2)
                || contract.employerNation().equals(nation) && nations.can(actor.getUUID(), S2Permission.MANAGE_SIEGE)
                || contract.providerNation().equals(nation) && nations.can(actor.getUUID(), S2Permission.MANAGE_DISPATCH);
        if (!allowed) return ActionResult.fail("no_permission");
        if (contract.state() == DispatchContractSavedData.State.APPROVAL_PENDING
                || contract.state() == DispatchContractSavedData.State.CONSENT_PENDING) {
            DispatchContractSavedData.Contract finished = data.finish(contractId, true, "cancelled:" + reason);
            publishFinished(actor.server, finished, true, "cancelled:" + reason, 0L);
            return finished == null ? ActionResult.fail("cannot_cancel") : ActionResult.ok(finished);
        }
        return settle(actor.server, contractId, "cancelled:" + reason)
                ? ActionResult.ok(data.byId(contractId).orElse(null)) : ActionResult.fail("settlement_failed");
    }

    public static void terminateNation(MinecraftServer server, UUID nationId) {
        DispatchContractSavedData data = DispatchContractSavedData.get(server);
        for (DispatchContractSavedData.Contract contract : data.all()) {
            if (!nationId.equals(contract.employerNation()) && !nationId.equals(contract.providerNation())) continue;
            if (contract.state() == DispatchContractSavedData.State.APPROVAL_PENDING
                    || contract.state() == DispatchContractSavedData.State.CONSENT_PENDING) {
                data.finish(contract.id(), true, "nation_disbanded");
                publishFinished(server, data.byId(contract.id()).orElse(null), true,
                        "nation_disbanded", 0L);
            } else if (contract.state() == DispatchContractSavedData.State.FUNDING
                    || contract.state() == DispatchContractSavedData.State.FUNDED
                    || contract.state() == DispatchContractSavedData.State.ACTIVE) {
                settle(server, contract.id(), "nation_disbanded");
            }
        }
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static void publishFinished(MinecraftServer server, DispatchContractSavedData.Contract finished,
                                        boolean cancelled, String reason, long earned) {
        if (finished == null) return;
        WarHistorySavedData.get(server).append(OpenTimeService.now(server),
                cancelled ? WarHistorySavedData.Type.DISPATCH_CANCELLED
                        : WarHistorySavedData.Type.DISPATCH_COMPLETED,
                WarHistorySavedData.Visibility.PUBLIC,
                finished.employerNation(), finished.providerNation(), finished.id(), List.of(reason));
        NationNotificationService.publish(server, List.of(finished.employerNation(), finished.providerNation()),
                cancelled ? NationNotificationSavedData.EventType.DISPATCH_CANCELLED
                        : NationNotificationSavedData.EventType.DISPATCH_COMPLETED, null, null, null,
                List.of(Long.toString(earned), reason));
    }

    public record ActionResult(boolean success, String detail, DispatchContractSavedData.Contract contract) {
        static ActionResult ok(DispatchContractSavedData.Contract contract) { return new ActionResult(true, "ok", contract); }
        static ActionResult fail(String detail) { return new ActionResult(false, detail, null); }
    }
}
