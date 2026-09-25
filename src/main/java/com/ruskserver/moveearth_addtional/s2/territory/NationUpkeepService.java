package com.ruskserver.moveearth_addtional.s2.territory;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import com.ruskserver.moveearth_addtional.network.S2C_NationTreasuryPacket;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationService;
import com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData;
import com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData.Account;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData;
import com.ruskserver.moveearth_addtional.s2.recovery.RecoveryService;
import com.ruskserver.moveearth_addtional.s2.recovery.EconomyGateway;
import com.ruskserver.moveearth_addtional.s2.recovery.NationRecoverySavedData;
import com.ruskserver.moveearth_addtional.s2.recovery.RecoveryFundSavedData;
import com.ruskserver.moveearth_addtional.s2.recovery.RecoveryFundService;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class NationUpkeepService {
    private static final Map<UUID, UpkeepPenalty> LAST_NOTIFIED_PENALTY = new HashMap<>();
    private static final Map<UUID, UpkeepPenalty> TICK_PENALTIES = new HashMap<>();
    private static MinecraftServer penaltyCacheServer;
    private static long penaltyCacheTick = Long.MIN_VALUE;
    private NationUpkeepService() {
    }

    public static boolean canManage(ServerPlayer player) {
        return player.hasPermissions(2)
                || NationSavedData.get(player.server).can(player.getUUID(), S2Permission.MANAGE_TREASURY);
    }

    public static void sendScreen(ServerPlayer player) {
        NationSavedData nations = NationSavedData.get(player.server);
        UUID nationId = nations.nationIdFor(player.getUUID()).orElse(null);
        if (nationId == null) return;
        NationUpkeepSavedData.AccountState state = NationUpkeepSavedData.get(player.server).state(nationId);
        TerritorySavedData territories = TerritorySavedData.get(player.server);
        long upkeep = TerritoryUpkeepPolicy.calculateConfigured(territories.controlledChunkCount(nationId),
                territories.activeOutpostCount(nationId), VehicleSavedData.get(player.server).count(nationId));
        EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(player.server);
        java.util.List<String> recent = ledger.recent(Account.nation(nationId), 5).stream()
                .map(tx -> (Account.nation(nationId).equals(tx.to()) ? "+" : "-")
                        + tx.amount() + "  " + tx.reason())
                .toList();
        PacketDistributor.sendToPlayer(player, new S2C_NationTreasuryPacket(canManage(player), upkeep,
                S2TerritoryConfig.upkeepCycleHours(),
                ledger.balance(Account.nation(nationId)), ledger.balance(Account.player(player.getUUID())),
                state.nextDueAt(), state.failedPayments(), state.overdueSince(),
                penalty(state, System.currentTimeMillis()), recent));
    }

    public static boolean moveFunds(ServerPlayer player, long amount, boolean intoNation) {
        if (amount <= 0L) return false;
        UUID nationId = NationSavedData.get(player.server).nationIdFor(player.getUUID()).orElse(null);
        if (nationId == null) return false;
        if (!intoNation && !canManage(player)) return false;
        Account nation = Account.nation(nationId);
        Account personal = Account.player(player.getUUID());
        EconomyLedgerSavedData.Result result = EconomyLedgerSavedData.get(player.server).transfer(
                UUID.randomUUID(), intoNation ? personal : nation, intoNation ? nation : personal,
                amount, intoNation ? "treasury_deposit" : "treasury_withdrawal");
        if (result == EconomyLedgerSavedData.Result.APPLIED && intoNation) {
            com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(player,
                    com.ruskserver.moveearth_addtional.advancement.ModCriteria.TREASURY_CONFIGURED);
        }
        return result == EconomyLedgerSavedData.Result.APPLIED;
    }

    public static boolean payNow(ServerPlayer player) {
        if (!canManage(player)) return false;
        UUID nationId = NationSavedData.get(player.server).nationIdFor(player.getUUID()).orElse(null);
        boolean paid = nationId != null && charge(player.server, nationId, System.currentTimeMillis());
        if (paid) com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(player,
                com.ruskserver.moveearth_addtional.advancement.ModCriteria.UPKEEP_PAID);
        return paid;
    }

    /** Atomic nation-to-nation compensation. */
    public static TransferResult transferGold(MinecraftServer server, UUID payerNation,
                                              UUID receiverNation, long amount, UUID transactionId) {
        if (amount < 0L) return TransferResult.INVALID_AMOUNT;
        if (amount == 0L) return TransferResult.SUCCESS;
        if (payerNation == null) return TransferResult.PAYER_ACCOUNT_MISSING;
        if (receiverNation == null) return TransferResult.RECEIVER_ACCOUNT_MISSING;
        return switch (EconomyLedgerSavedData.get(server).transfer(transactionId,
                Account.nation(payerNation), Account.nation(receiverNation), amount,
                "peace_compensation", transactionId)) {
            case APPLIED, ALREADY_APPLIED -> TransferResult.SUCCESS;
            case INSUFFICIENT_FUNDS -> TransferResult.INSUFFICIENT_FUNDS;
            case INVALID -> TransferResult.INVALID_AMOUNT;
            case OVERFLOW, CONFLICT -> TransferResult.DEPOSIT_FAILED;
        };
    }

    public enum TransferResult {
        SUCCESS, INVALID_AMOUNT, PAYER_ACCOUNT_MISSING, RECEIVER_ACCOUNT_MISSING,
        INSUFFICIENT_FUNDS, DEPOSIT_FAILED
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % 1200L != 17L) return;
        long now = System.currentTimeMillis();
        NationUpkeepSavedData data = NationUpkeepSavedData.get(server);
        TerritorySavedData territories = TerritorySavedData.get(server);
        VehicleSavedData vehicles = VehicleSavedData.get(server);
        for (NationSavedData.Nation nation : NationSavedData.get(server).nations().values()) {
            UUID nationId = nation.id();
            if (territories.controlledCoreCount(nationId) <= 0 && vehicles.count(nationId) <= 0) continue;
            data.ensureScheduled(nationId, now);
            var state = data.state(nationId);
            if (now >= state.nextDueAt() && now >= state.nextAttemptAt()) {
                charge(server, nationId, now);
            }
            notifyPenaltyChange(server, nationId, penalty(state, now));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_NOTIFIED_PENALTY.clear();
        TICK_PENALTIES.clear();
        penaltyCacheServer = null;
        penaltyCacheTick = Long.MIN_VALUE;
    }

    /** Drops transient notification state when a nation is permanently removed. */
    public static void removeNation(UUID nationId) {
        LAST_NOTIFIED_PENALTY.remove(nationId);
        invalidatePenalty(nationId);
    }

    public static UpkeepPenalty penalty(MinecraftServer server, UUID nationId) {
        long tick = server.overworld().getGameTime();
        if (penaltyCacheServer != server || penaltyCacheTick != tick) {
            TICK_PENALTIES.clear();
            penaltyCacheServer = server;
            penaltyCacheTick = tick;
        }
        return TICK_PENALTIES.computeIfAbsent(nationId, ignored ->
                penalty(NationUpkeepSavedData.get(server).state(nationId), System.currentTimeMillis()));
    }

    public static int effectiveTerritoryRadius(MinecraftServer server, UUID nationId, int configuredRadius) {
        return UpkeepPenaltyPolicy.effectiveTerritoryRadius(configuredRadius, penalty(server, nationId),
                S2TerritoryConfig.overdueTerritoryRadiusPercent());
    }

    private static UpkeepPenalty penalty(NationUpkeepSavedData.AccountState state, long now) {
        return UpkeepPenaltyPolicy.evaluate(state.overdueSince(), now,
                S2TerritoryConfig.upkeepWeakenMillis(), S2TerritoryConfig.upkeepDisableMillis());
    }

    private static void invalidatePenalty(UUID nationId) {
        if (nationId != null) TICK_PENALTIES.remove(nationId);
    }

    private static void notifyPenaltyChange(MinecraftServer server, UUID nationId, UpkeepPenalty penalty) {
        UpkeepPenalty previous = LAST_NOTIFIED_PENALTY.put(nationId, penalty);
        if (penalty == UpkeepPenalty.CURRENT || penalty == previous) return;
        String key = "message.moveearth_addtional.upkeep.penalty." + penalty.name().toLowerCase(java.util.Locale.ROOT);
        NationNotificationService.publish(server, java.util.List.of(nationId),
                NationNotificationSavedData.EventType.UPKEEP_WARNING, null, null,
                net.minecraft.network.chat.Component.translatable(key),
                java.util.List.of(penalty.name()));
    }

    private static boolean charge(MinecraftServer server, UUID nationId, long now) {
        NationUpkeepSavedData data = NationUpkeepSavedData.get(server);
        NationUpkeepSavedData.AccountState state = data.state(nationId);
        UUID chargeId = UUID.nameUUIDFromBytes(("nation_upkeep:" + nationId + ":" + state.nextDueAt())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var priorAid = RecoveryFundSavedData.get(server)
                .transaction(RecoveryFundSavedData.Type.UPKEEP_SUBSIDY, chargeId).orElse(null);
        EconomyLedgerSavedData.Transaction prior = EconomyLedgerSavedData.get(server).transaction(chargeId);
        if (prior != null) {
            if (!Account.nation(nationId).equals(prior.from()) || prior.to() != null
                    || !"nation_upkeep".equals(prior.reason())) {
                Moveearth_addtional.LOGGER.error("Conflicting upkeep transaction for nation {}", nationId);
                return false;
            }
            if (priorAid != null && priorAid.state() == RecoveryFundSavedData.State.RESERVED
                    && !RecoveryFundService.consumeAid(server,
                    NationRecoverySavedData.get(server).eligibleForNation(nationId,
                            com.ruskserver.moveearth_addtional.s2.time.OpenTimeService.now(server))
                            .map(NationRecoverySavedData.Episode::id).orElse(null),
                    priorAid.id(), priorAid.amount())) {
                Moveearth_addtional.LOGGER.error("Unable to reconcile upkeep aid for nation {}", nationId);
                return false;
            }
            data.paymentSucceeded(nationId, now);
            invalidatePenalty(nationId);
            return true;
        }
        if (priorAid != null && priorAid.state() == RecoveryFundSavedData.State.PAID) {
            // A fully subsidized charge has no ledger withdrawal to use as its receipt.
            data.paymentSucceeded(nationId, now);
            invalidatePenalty(nationId);
            return true;
        }
        TerritorySavedData territories = TerritorySavedData.get(server);
        long amount = TerritoryUpkeepPolicy.calculateConfigured(territories.controlledChunkCount(nationId),
                territories.activeOutpostCount(nationId), VehicleSavedData.get(server).count(nationId));
        if (amount <= 0L) {
            data.paymentSucceeded(nationId, now);
            invalidatePenalty(nationId);
            return true;
        }
        UUID aidTransaction = null;
        UUID episodeId = null;
        long subsidy = 0L;
        NationRecoverySavedData.Episode episode = NationRecoverySavedData.get(server)
                .eligibleForNation(nationId, com.ruskserver.moveearth_addtional.s2.time.OpenTimeService.now(server))
                .orElse(null);
        if (priorAid != null && priorAid.state() == RecoveryFundSavedData.State.RESERVED) {
            aidTransaction = priorAid.id();
            episodeId = episode == null ? null : episode.id();
            subsidy = priorAid.amount();
        } else if (episode != null) {
            RecoveryFundService.Result aid = RecoveryFundService.reserveAid(server, episode, chargeId, amount,
                    RecoveryFundSavedData.Type.UPKEEP_SUBSIDY);
            if (aid.success()) {
                aidTransaction = aid.transactionId();
                episodeId = episode.id();
                subsidy = RecoveryFundSavedData.get(server).transaction(aidTransaction)
                        .map(RecoveryFundSavedData.Transaction::amount).orElse(0L);
            }
        }
        long ownAmount = Math.max(0L, amount - subsidy);
        try {
            EconomyGateway.Result withdrawal = EconomyGateway.withdraw(server, nationId, ownAmount,
                    chargeId, "nation_upkeep");
            if (withdrawal == EconomyGateway.Result.SUCCESS) {
                if (aidTransaction != null && !RecoveryFundService.consumeAid(server, episodeId,
                        aidTransaction, subsidy)) {
                    Moveearth_addtional.LOGGER.error("Failed to commit upkeep recovery aid for nation {}", nationId);
                    return false;
                }
                data.paymentSucceeded(nationId, now);
                invalidatePenalty(nationId);
                RecoveryService.onUpkeepPaid(server, nationId, amount);
                return true;
            }
        } catch (RuntimeException exception) {
            Moveearth_addtional.LOGGER.warn("Nation upkeep payment failed for {}", nationId, exception);
        }
        if (aidTransaction != null) RecoveryFundService.refundAid(server, episodeId, aidTransaction);
        data.paymentFailed(nationId, now);
        invalidatePenalty(nationId);
        return false;
    }
}
