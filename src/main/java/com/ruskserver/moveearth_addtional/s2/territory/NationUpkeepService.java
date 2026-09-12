package com.ruskserver.moveearth_addtional.s2.territory;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import com.ruskserver.moveearth_addtional.network.S2C_NationTreasuryPacket;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationService;
import io.github.lightman314.lightmanscurrency.api.money.bank.BankAPI;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.BankReference;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValueParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class NationUpkeepService {
    private static final Map<UUID, UpkeepPenalty> LAST_NOTIFIED_PENALTY = new HashMap<>();
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
        List<BankReference> references = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (BankReference reference : BankAPI.getApi().GetAllBankReferences(false)) {
            try {
                if (reference != null && reference.isValid() && reference.allowedAccess(player)) {
                    IBankAccount account = reference.get();
                    if (account != null) {
                        references.add(reference);
                        names.add(account.getName().getString());
                    }
                }
            } catch (RuntimeException ignored) {
            }
        }
        int selected = state.reference() == null ? -1 : references.indexOf(state.reference());
        TerritorySavedData territories = TerritorySavedData.get(player.server);
        long upkeep = TerritoryUpkeepPolicy.calculateConfigured(territories.controlledChunkCount(nationId),
                territories.activeOutpostCount(nationId));
        PacketDistributor.sendToPlayer(player, new S2C_NationTreasuryPacket(canManage(player), upkeep,
                state.enabled(), state.nextDueAt(), state.failedPayments(), state.overdueSince(),
                penalty(state, System.currentTimeMillis()), selected, references, names));
    }

    public static boolean configure(ServerPlayer player, BankReference reference, boolean enabled) {
        if (!canManage(player)) return false;
        UUID nationId = NationSavedData.get(player.server).nationIdFor(player.getUUID()).orElse(null);
        if (nationId == null) return false;
        if (reference != null && (!reference.isValid() || !reference.allowedAccess(player))) return false;
        NationUpkeepSavedData.get(player.server).configure(nationId, reference, enabled,
                System.currentTimeMillis());
        return true;
    }

    public static boolean payNow(ServerPlayer player) {
        if (!canManage(player)) return false;
        UUID nationId = NationSavedData.get(player.server).nationIdFor(player.getUUID()).orElse(null);
        return nationId != null && charge(player.server, nationId, System.currentTimeMillis());
    }

    /** Transfers configured treasury gold, rolling the withdrawal back if the receiver deposit fails. */
    public static TransferResult transferGold(MinecraftServer server, UUID payerNation,
                                              UUID receiverNation, long amount) {
        if (amount < 0L) return TransferResult.INVALID_AMOUNT;
        if (amount == 0L) return TransferResult.SUCCESS;
        try {
            NationUpkeepSavedData data = NationUpkeepSavedData.get(server);
            BankReference payerReference = data.state(payerNation).reference();
            BankReference receiverReference = data.state(receiverNation).reference();
            IBankAccount payer = payerReference == null || !payerReference.isValid()
                    ? null : payerReference.get();
            IBankAccount receiver = receiverReference == null || !receiverReference.isValid()
                    ? null : receiverReference.get();
            if (payer == null) return TransferResult.PAYER_ACCOUNT_MISSING;
            if (receiver == null) return TransferResult.RECEIVER_ACCOUNT_MISSING;
            MoneyValue value = MoneyValueParser.ParseConfigString(
                    "coin;" + amount + "-lightmanscurrency:coin_gold", MoneyValue::empty);
            if (value.isEmpty() || !payer.getStoredMoney().containsValue(value)) {
                return TransferResult.INSUFFICIENT_FUNDS;
            }
            var withdrawn = BankAPI.getApi().BankWithdrawFromServer(payer, value);
            if (!withdrawn.getFirst()) return TransferResult.INSUFFICIENT_FUNDS;
            if (BankAPI.getApi().BankDepositFromServer(receiver, value)) return TransferResult.SUCCESS;
            if (!BankAPI.getApi().BankDepositFromServer(payer, value)) {
                Moveearth_addtional.LOGGER.error(
                        "Failed to roll back peace compensation for nation {}", payerNation);
            }
            return TransferResult.DEPOSIT_FAILED;
        } catch (RuntimeException exception) {
            Moveearth_addtional.LOGGER.warn("Peace compensation transfer failed from {} to {}",
                    payerNation, receiverNation, exception);
            return TransferResult.DEPOSIT_FAILED;
        }
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
        for (NationSavedData.Nation nation : NationSavedData.get(server).nations().values()) {
            UUID nationId = nation.id();
            if (territories.controlledCoreCount(nationId) <= 0) continue;
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
    }

    /** Drops transient notification state when a nation is permanently removed. */
    public static void removeNation(UUID nationId) {
        LAST_NOTIFIED_PENALTY.remove(nationId);
    }

    public static UpkeepPenalty penalty(MinecraftServer server, UUID nationId) {
        return penalty(NationUpkeepSavedData.get(server).state(nationId), System.currentTimeMillis());
    }

    private static UpkeepPenalty penalty(NationUpkeepSavedData.AccountState state, long now) {
        return UpkeepPenaltyPolicy.evaluate(state.overdueSince(), now,
                S2TerritoryConfig.upkeepWeakenMillis(), S2TerritoryConfig.upkeepDisableMillis());
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
        TerritorySavedData territories = TerritorySavedData.get(server);
        long amount = TerritoryUpkeepPolicy.calculateConfigured(territories.controlledChunkCount(nationId),
                territories.activeOutpostCount(nationId));
        if (amount <= 0L) {
            data.paymentSucceeded(nationId, now);
            return true;
        }
        try {
            BankReference reference = state.reference();
            IBankAccount account = reference == null || !reference.isValid() ? null : reference.get();
            MoneyValue fee = MoneyValueParser.ParseConfigString(
                    "coin;" + amount + "-lightmanscurrency:coin_gold", MoneyValue::empty);
            if (account != null && !fee.isEmpty() && account.getStoredMoney().containsValue(fee)
                    && BankAPI.getApi().BankWithdrawFromServer(account, fee).getFirst()) {
                data.paymentSucceeded(nationId, now);
                return true;
            }
        } catch (RuntimeException exception) {
            Moveearth_addtional.LOGGER.warn("Nation upkeep payment failed for {}", nationId, exception);
        }
        data.paymentFailed(nationId, now);
        return false;
    }
}
