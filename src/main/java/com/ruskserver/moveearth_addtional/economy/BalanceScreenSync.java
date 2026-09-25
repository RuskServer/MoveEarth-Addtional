package com.ruskserver.moveearth_addtional.economy;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.C2S_BalanceActionPacket;
import com.ruskserver.moveearth_addtional.network.S2C_BalanceSnapshotPacket;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative wallet view and atomic player-to-player transfers. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class BalanceScreenSync {
    private static final int PAY_COOLDOWN_TICKS = 20;
    private static final Map<UUID, Integer> LAST_PAY_TICK = new HashMap<>();
    private static final Map<UUID, Integer> LAST_REFRESH_TICK = new HashMap<>();

    private BalanceScreenSync() { }

    public static void open(ServerPlayer player) { send(player, true, "", false); }

    public static void handle(ServerPlayer player, C2S_BalanceActionPacket packet) {
        if (packet == null) return;
        if ("REFRESH".equals(packet.action())) {
            int now = player.server.getTickCount();
            Integer last = LAST_REFRESH_TICK.put(player.getUUID(), now);
            if (last == null || now - last >= 10) send(player, false, "", false);
            return;
        }
        if (!"PAY".equals(packet.action())) return;
        UUID senderId = player.getUUID();
        if (!BalancePaymentPolicy.validRequest(senderId, packet.recipient(), packet.amount())) {
            send(player, false, "送金先と金額（1～1,000,000 TC）を確認してください", false);
            return;
        }
        ServerPlayer recipient = player.server.getPlayerList().getPlayer(packet.recipient());
        if (recipient == null) {
            send(player, false, "相手がオフラインです。オンライン中の相手を選んでください", false);
            return;
        }
        int now = player.server.getTickCount();
        Integer last = LAST_PAY_TICK.get(senderId);
        if (last != null && now - last < PAY_COOLDOWN_TICKS) {
            send(player, false, "連続送金は少し待ってから行ってください", false);
            return;
        }
        LAST_PAY_TICK.put(senderId, now);
        var ledger = EconomyLedgerSavedData.get(player.server);
        ledger.rememberPlayer(senderId, player.getScoreboardName());
        ledger.rememberPlayer(recipient.getUUID(), recipient.getScoreboardName());
        var result = ledger.transfer(UUID.randomUUID(),
                EconomyLedgerSavedData.Account.player(senderId),
                EconomyLedgerSavedData.Account.player(recipient.getUUID()), packet.amount(), "player_pay");
        if (result != EconomyLedgerSavedData.Result.APPLIED) {
            send(player, false, result == EconomyLedgerSavedData.Result.INSUFFICIENT_FUNDS
                    ? "残高が足りません" : "送金できませんでした", false);
            return;
        }
        EconomyWaypointSync.syncBalance(player);
        EconomyWaypointSync.syncBalance(recipient);
        recipient.sendSystemMessage(MoveEarthMessage.success(
                player.getScoreboardName() + " から " + packet.amount() + " TC を受け取りました"));
        send(player, false, recipient.getScoreboardName() + " に " + packet.amount() + " TC を送りました", true);
        send(recipient, false, "", false);
    }

    private static void send(ServerPlayer player, boolean openScreen, String result, boolean success) {
        MinecraftServer server = player.server;
        var ledger = EconomyLedgerSavedData.get(server);
        var account = EconomyLedgerSavedData.Account.player(player.getUUID());
        var history = ledger.recent(account, 40).stream().map(tx -> {
            boolean incoming = account.equals(tx.to());
            var other = incoming ? tx.from() : tx.to();
            return new S2C_BalanceSnapshotPacket.HistoryEntry(tx.occurredAt(), tx.amount(), incoming,
                    tx.reason(), accountName(server, other));
        }).toList();
        var recipients = server.getPlayerList().getPlayers().stream()
                .filter(other -> !other.getUUID().equals(player.getUUID()))
                .sorted(Comparator.comparing(ServerPlayer::getScoreboardName, String.CASE_INSENSITIVE_ORDER))
                .limit(256)
                .map(other -> new S2C_BalanceSnapshotPacket.Recipient(other.getUUID(), other.getScoreboardName()))
                .toList();
        PacketDistributor.sendToPlayer(player, new S2C_BalanceSnapshotPacket(openScreen,
                ledger.balance(account), history, recipients, result, success));
    }

    private static String accountName(MinecraftServer server, EconomyLedgerSavedData.Account account) {
        if (account == null) return "システム";
        return switch (account.kind()) {
            case NATION -> "国家口座";
            case ESCROW -> "市場預託";
            case PLAYER -> {
                ServerPlayer online = server.getPlayerList().getPlayer(account.id());
                String known = EconomyLedgerSavedData.get(server).knownPlayerName(account.id());
                yield online != null ? online.getScoreboardName()
                        : known != null ? known : "プレイヤー " + account.id().toString().substring(0, 8);
            }
        };
    }

    @SubscribeEvent public static void onStopped(ServerStoppedEvent event) {
        LAST_PAY_TICK.clear();
        LAST_REFRESH_TICK.clear();
    }
}
