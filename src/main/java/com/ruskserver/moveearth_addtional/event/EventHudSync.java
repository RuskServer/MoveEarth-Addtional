package com.ruskserver.moveearth_addtional.event;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_EventHudPacket;
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
    /** Event each non-member was last told they cannot rank in, so the notice goes out once per event. */
    private static final Map<UUID, UUID> NOT_ELIGIBLE_NOTICE = new HashMap<>();

    private EventHudSync() { }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(player.getServer());
            sync(player, Standings.of(HarvestFestival.eligibleRanking(player.getServer(), ledger)),
                    ledger.pendingEventRewardCount(player.getUUID()));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
        NOT_ELIGIBLE_NOTICE.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        LAST.clear();
        NOT_ELIGIBLE_NOTICE.clear();
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % 20L != 1L) return;
        EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(server);
        // Ranked once per second for everyone; each player then costs map lookups only, and the
        // unclaimed-reward count is a running total in the goods store.
        Standings standings = Standings.of(HarvestFestival.eligibleRanking(server, ledger));
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            sync(player, standings, ledger.pendingEventRewardCount(player.getUUID()));
    }

    /** The eligible ranking, its top three and each player's place, built once per sync pass. */
    private record Standings(Map<UUID, Integer> rankOf, List<S2C_EventHudPacket.Leader> leaders) {
        static Standings of(List<Map.Entry<UUID, EconomyLedgerSavedData.HarvestScore>> ranking) {
            Map<UUID, Integer> rankOf = new HashMap<>();
            for (int index = 0; index < ranking.size(); index++) rankOf.putIfAbsent(ranking.get(index).getKey(), index + 1);
            List<S2C_EventHudPacket.Leader> leaders = ranking.stream().limit(3)
                    .map(entry -> new S2C_EventHudPacket.Leader(entry.getValue().name(), entry.getValue().points()))
                    .toList();
            return new Standings(rankOf, leaders);
        }
    }

    private static void sync(ServerPlayer player, Standings standings, int pending) {
        S2C_EventHudPacket packet = snapshot(player, standings, pending);
        if (!packet.equals(LAST.put(player.getUUID(), packet)))
            PacketDistributor.sendToPlayer(player, packet);
    }

    private static S2C_EventHudPacket snapshot(ServerPlayer player, Standings standings, int pending) {
        MinecraftServer server = player.getServer();
        EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(server);
        if (ledger.harvestId() == null || ledger.harvestSettled())
            return S2C_EventHudPacket.inactive(pending);
        noticeIfNotEligible(player, ledger.harvestId());
        int rank = standings.rankOf().getOrDefault(player.getUUID(), 0);
        List<S2C_EventHudPacket.Leader> leaders = standings.leaders();
        EconomyLedgerSavedData.HarvestScore score = ledger.harvestScore(player.getUUID());
        long remaining = Math.max(0L, ledger.harvestEndTick() - OpenTimeService.now(server));
        int minutes = (int) Math.min(Integer.MAX_VALUE, (remaining + 1199L) / 1200L);
        String target = "RESOURCE".equals(ledger.eventKind())
                ? "地方" + ledger.targetRegion() + " · " + resourceName(ledger.targetMaterial()) + "鉱石"
                : "成熟作物の収穫";
        return new S2C_EventHudPacket(true, HarvestFestival.eventName(ledger), target,
                minutes, score == null ? 0 : score.points(), rank, leaders, pending);
    }

    /**
     * Settlement ranks and rewards nation members only. A player outside a nation keeps rank 0 on
     * the HUD and is told why once per event.
     */
    private static void noticeIfNotEligible(ServerPlayer player, UUID eventId) {
        boolean member = com.ruskserver.moveearth_addtional.s2.nation.NationSavedData.get(player.getServer())
                .nationIdFor(player.getUUID()).isPresent();
        if (member || eventId.equals(NOT_ELIGIBLE_NOTICE.get(player.getUUID()))) return;
        NOT_ELIGIBLE_NOTICE.put(player.getUUID(), eventId);
        player.sendSystemMessage(com.ruskserver.moveearth_addtional.ui.MoveEarthMessage.warning(
                net.minecraft.network.chat.Component.translatable(
                        "message.moveearth_addtional.event.not_eligible_no_nation")));
    }

    private static String resourceName(String material) {
        return switch (material) {
            case "coal" -> "石炭";
            case "copper" -> "銅";
            default -> "鉄";
        };
    }
}
