package com.ruskserver.moveearth_addtional.s2.recovery;

import com.ruskserver.moveearth_addtional.config.RecoveryDispatchConfig;
import com.ruskserver.moveearth_addtional.s2.time.OpenTimeService;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

public final class RecoveryFundService {
    private RecoveryFundService() { }

    public static Result creditMint(MinecraftServer server, long amount) {
        if (amount <= 0L) return new Result(false, null, "invalid_amount");
        RecoveryFundSavedData data = RecoveryFundSavedData.get(server);
        var transaction = data.prepare(RecoveryFundSavedData.Type.ADMIN_MINT, null, null,
                amount, OpenTimeService.now(server), "admin_mint");
        return new Result(data.credit(transaction.id()), transaction.id(), "credited");
    }

    public static Result creditFromNation(MinecraftServer server, UUID nationId, long amount) {
        if (nationId == null || amount <= 0L) return new Result(false, null, "invalid_amount");
        RecoveryFundSavedData data = RecoveryFundSavedData.get(server);
        var transaction = data.prepare(RecoveryFundSavedData.Type.NATION_TRANSFER, nationId, null,
                amount, OpenTimeService.now(server), "nation_transfer");
        EconomyGateway.Result withdrawal = EconomyGateway.withdraw(server, nationId, amount);
        if (withdrawal != EconomyGateway.Result.SUCCESS) {
            if (withdrawal == EconomyGateway.Result.ERROR) {
                data.reviewRequired(transaction.id(), "withdrawal:" + withdrawal.name());
            } else {
                data.abortPrepared(transaction.id(), "withdrawal:" + withdrawal.name());
            }
            return new Result(false, transaction.id(), withdrawal.name().toLowerCase(java.util.Locale.ROOT));
        }
        data.markWithdrawn(transaction.id());
        return new Result(data.credit(transaction.id()), transaction.id(), "credited");
    }

    public static Result reserveAid(MinecraftServer server, NationRecoverySavedData.Episode episode,
                                    UUID sourceId, long requested, RecoveryFundSavedData.Type type) {
        if (!RecoveryDispatchConfig.fundEnabled() || episode == null || requested <= 0L) {
            return new Result(false, null, "fund_disabled_or_invalid");
        }
        long now = OpenTimeService.now(server);
        if (now >= episode.expiresAt()) return new Result(false, null, "eligibility_expired");
        RecoveryFundSavedData data = RecoveryFundSavedData.get(server);
        if (sourceId != null && data.transaction(type, sourceId).isPresent()) {
            return new Result(false, data.transaction(type, sourceId).orElseThrow().id(), "duplicate_source");
        }
        long windowStart = Math.max(0L, now - RecoveryDispatchConfig.repeatCooldownOpenTicks());
        if (NationRecoverySavedData.get(server).hasPriorAidSince(
                episode.nationId(), episode.id(), windowStart)) {
            return new Result(false, null, "repeat_cooldown");
        }
        long unlockedCap = RecoveryDispatchConfig.fundEpisodeCap() * episode.supportPercent() / 100L;
        long episodeRemaining = Math.max(0L, unlockedCap
                - episode.aidUsed() - episode.aidReserved());
        long nationRemaining = Math.max(0L, RecoveryDispatchConfig.fundNationWindowCap()
                - data.paidToNationSince(episode.nationId(), windowStart));
        long globalRemaining = Math.max(0L, RecoveryDispatchConfig.fundGlobalWindowCap()
                - data.globallyPaidSince(windowStart));
        long amount = Math.min(requested, Math.min(data.available(), Math.min(episodeRemaining,
                Math.min(nationRemaining, globalRemaining))));
        if (amount <= 0L) return new Result(false, null, "fund_limit");
        var transaction = data.prepare(type, episode.nationId(), sourceId, amount, now, "aid_reservation");
        if (!data.reserve(transaction.id()) || !NationRecoverySavedData.get(server).reserveAid(episode.id(), amount)) {
            data.refund(transaction.id());
            return new Result(false, transaction.id(), "reservation_failed");
        }
        return new Result(true, transaction.id(), "reserved");
    }

    public static boolean consumeAid(MinecraftServer server, UUID episodeId, UUID transactionId, long used) {
        RecoveryFundSavedData data = RecoveryFundSavedData.get(server);
        var transaction = data.transaction(transactionId).orElse(null);
        if (transaction == null || used < 0L || used > transaction.amount()) return false;
        if (!data.consume(transactionId, used)) return false;
        return NationRecoverySavedData.get(server).settleAid(episodeId, transaction.amount(), used);
    }

    public static boolean refundAid(MinecraftServer server, UUID episodeId, UUID transactionId) {
        RecoveryFundSavedData data = RecoveryFundSavedData.get(server);
        var transaction = data.transaction(transactionId).orElse(null);
        if (transaction == null || !data.refund(transactionId)) return false;
        return NationRecoverySavedData.get(server).settleAid(episodeId, transaction.amount(), 0L);
    }

    public record Result(boolean success, UUID transactionId, String detail) { }
}
