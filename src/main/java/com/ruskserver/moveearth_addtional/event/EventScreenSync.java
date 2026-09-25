package com.ruskserver.moveearth_addtional.event;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData;
import com.ruskserver.moveearth_addtional.network.S2C_EventScreenPacket;
import com.ruskserver.moveearth_addtional.s2.time.OpenTimeService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class EventScreenSync {
    private static final Map<UUID, Long> LAST_ACTION = new HashMap<>();

    private EventScreenSync() { }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_ACTION.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        LAST_ACTION.clear();
    }

    public static void open(ServerPlayer player) {
        send(player, "", true);
    }

    public static void handle(ServerPlayer player, String action) {
        if (!"refresh".equals(action) && !"claim".equals(action)) return;
        long now = player.server.overworld().getGameTime();
        Long previous = LAST_ACTION.get(player.getUUID());
        if (previous != null && now - previous < 5L) return;
        LAST_ACTION.put(player.getUUID(), now);
        if ("refresh".equals(action)) {
            send(player, "", false);
        } else if ("claim".equals(action)) {
            int received = HarvestFestival.claim(player);
            send(player, received > 0 ? received + "個受け取りました"
                    : "受取待ちがないか、所持品に空きがありません", false);
        }
    }

    private static void send(ServerPlayer player, String result, boolean open) {
        PacketDistributor.sendToPlayer(player, snapshot(player, result, open));
    }

    private static S2C_EventScreenPacket snapshot(ServerPlayer player, String result, boolean open) {
        MinecraftServer server = player.getServer();
        EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(server);
        boolean hasEvent = ledger.harvestId() != null;
        boolean active = hasEvent && !ledger.harvestSettled();
        long now = OpenTimeService.now(server);
        int remaining = minutes(Math.max(0L, ledger.harvestEndTick() - now));
        int next = minutes(Math.max(0L, ledger.nextAutoEventTick() - now));
        List<Map.Entry<UUID, EconomyLedgerSavedData.HarvestScore>> ranking = HarvestFestival.ranking(ledger);
        int rank = 0;
        for (int index = 0; index < ranking.size(); index++) {
            if (ranking.get(index).getKey().equals(player.getUUID())) {
                rank = index + 1;
                break;
            }
        }
        EconomyLedgerSavedData.HarvestScore own = ledger.harvestScores().get(player.getUUID());
        List<S2C_EventScreenPacket.Leader> leaders = ranking.stream().limit(5)
                .map(entry -> new S2C_EventScreenPacket.Leader(entry.getValue().name(), entry.getValue().points()))
                .toList();
        Map<ResourceLocation, Integer> totals = new LinkedHashMap<>();
        for (EconomyLedgerSavedData.EventReward reward : ledger.pendingEventRewards(player.getUUID())) {
            reward.items().forEach(item -> totals.merge(BuiltInRegistries.ITEM.getKey(item.getItem()),
                    item.getCount(), Integer::sum));
        }
        List<S2C_EventScreenPacket.ClaimItem> claims = totals.entrySet().stream().limit(16)
                .map(entry -> new S2C_EventScreenPacket.ClaimItem(entry.getKey(), entry.getValue()))
                .toList();
        EconomyLedgerSavedData.EventReward reward = hasEvent
                ? ledger.eventReward(ledger.harvestId(), player.getUUID()) : null;
        String target = "RESOURCE".equals(ledger.eventKind())
                ? "地方" + ledger.targetRegion() + "・" + resourceName(ledger.targetMaterial()) + "鉱石"
                : "成熟作物の収穫";
        return new S2C_EventScreenPacket(open, hasEvent, active,
                hasEvent ? HarvestFestival.eventName(ledger) : "", hasEvent ? target : "",
                remaining, next, own == null ? 0 : own.points(), rank,
                own != null && own.farmer(), reward == null ? 0 : reward.currency(),
                leaders, claims, result);
    }

    private static int minutes(long ticks) {
        return (int) Math.min(Integer.MAX_VALUE, (ticks + 1199L) / 1200L);
    }

    private static String resourceName(String material) {
        return switch (material) {
            case "coal" -> "石炭";
            case "copper" -> "銅";
            default -> "鉄";
        };
    }
}
