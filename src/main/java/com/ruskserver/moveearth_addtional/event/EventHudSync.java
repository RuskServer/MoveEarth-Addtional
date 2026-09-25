package com.ruskserver.moveearth_addtional.event;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData;
import com.ruskserver.moveearth_addtional.network.S2C_EventHudPacket;
import com.ruskserver.moveearth_addtional.s2.time.OpenTimeService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class EventHudSync {
    private static final Map<UUID, S2C_EventHudPacket> LAST = new HashMap<>();

    private EventHudSync() { }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            sync(player, HarvestFestival.ranking(EconomyLedgerSavedData.get(player.getServer())));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        LAST.clear();
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % 20L != 1L) return;
        List<Map.Entry<UUID, EconomyLedgerSavedData.HarvestScore>> ranking =
                HarvestFestival.ranking(EconomyLedgerSavedData.get(server));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sync(player, ranking);
    }

    private static void sync(ServerPlayer player,
                             List<Map.Entry<UUID, EconomyLedgerSavedData.HarvestScore>> ranking) {
        S2C_EventHudPacket packet = snapshot(player, ranking);
        if (!packet.equals(LAST.put(player.getUUID(), packet)))
            PacketDistributor.sendToPlayer(player, packet);
    }

    private static S2C_EventHudPacket snapshot(ServerPlayer player,
                                                List<Map.Entry<UUID, EconomyLedgerSavedData.HarvestScore>> ranking) {
        MinecraftServer server = player.getServer();
        EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(server);
        int pending = ledger.pendingEventRewards(player.getUUID()).size();
        if (ledger.harvestId() == null || ledger.harvestSettled())
            return S2C_EventHudPacket.inactive(pending);
        int rank = 0;
        for (int index = 0; index < ranking.size(); index++) {
            if (ranking.get(index).getKey().equals(player.getUUID())) {
                rank = index + 1;
                break;
            }
        }
        List<S2C_EventHudPacket.Leader> leaders = ranking.stream().limit(3)
                .map(entry -> new S2C_EventHudPacket.Leader(entry.getValue().name(), entry.getValue().points()))
                .toList();
        EconomyLedgerSavedData.HarvestScore score = ledger.harvestScores().get(player.getUUID());
        long remaining = Math.max(0L, ledger.harvestEndTick() - OpenTimeService.now(server));
        int minutes = (int) Math.min(Integer.MAX_VALUE, (remaining + 1199L) / 1200L);
        String target = "RESOURCE".equals(ledger.eventKind())
                ? "地方" + ledger.targetRegion() + " · " + resourceName(ledger.targetMaterial()) + "鉱石"
                : "成熟作物の収穫";
        return new S2C_EventHudPacket(true, HarvestFestival.eventName(ledger), target,
                minutes, score == null ? 0 : score.points(), rank, leaders, pending);
    }

    private static String resourceName(String material) {
        return switch (material) {
            case "coal" -> "石炭";
            case "copper" -> "銅";
            default -> "鉄";
        };
    }
}
