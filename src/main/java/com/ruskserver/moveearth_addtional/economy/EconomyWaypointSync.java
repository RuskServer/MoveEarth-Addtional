package com.ruskserver.moveearth_addtional.economy;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.S2C_EconomyHudPacket;
import com.ruskserver.moveearth_addtional.network.S2C_WaypointPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Sends changed balances at most once per second; waypoints are synced on login and edits. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class EconomyWaypointSync {
    private static final Map<UUID, Long> LAST_BALANCE = new HashMap<>();
    private EconomyWaypointSync() { }

    @SubscribeEvent public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EconomyLedgerSavedData.get(player.server).rememberPlayer(player.getUUID(), player.getScoreboardName());
            syncBalance(player);
            syncWaypoint(player);
        }
    }

    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_BALANCE.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent public static void onTick(ServerTickEvent.Post event) {
        if (event.getServer().overworld().getGameTime() % 20L != 0L) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) syncBalance(player);
    }

    @SubscribeEvent public static void onStopped(ServerStoppedEvent event) { LAST_BALANCE.clear(); }

    public static void syncBalance(ServerPlayer player) {
        long balance = EconomyLedgerSavedData.get(player.server)
                .balance(EconomyLedgerSavedData.Account.player(player.getUUID()));
        if (!Long.valueOf(balance).equals(LAST_BALANCE.put(player.getUUID(), balance)))
            PacketDistributor.sendToPlayer(player, new S2C_EconomyHudPacket(balance));
    }

    public static void syncWaypoint(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, WaypointSavedData.get(player.server).active(player.getUUID())
                .map(S2C_WaypointPacket::of).orElseGet(S2C_WaypointPacket::clear));
    }

    public static boolean setMarketWaypoint(ServerPlayer player, UUID stationId) {
        var station = MarketStationSavedData.get(player.server).byId(stationId).orElse(null);
        if (station == null) return false;
        var nation = com.ruskserver.moveearth_addtional.s2.nation.NationSavedData.get(player.server)
                .nation(station.nationId()).orElse(null);
        String name = nation == null ? "マーケット" : nation.name() + " / マーケット";
        setWaypoint(player, new WaypointSavedData.Waypoint(name, station.dimension(), station.pos(), true));
        return true;
    }

    public static void setWaypoint(ServerPlayer player, WaypointSavedData.Waypoint waypoint) {
        WaypointSavedData.get(player.server).set(player.getUUID(), waypoint);
        syncWaypoint(player);
    }

    public static void clearWaypoint(ServerPlayer player) {
        WaypointSavedData.get(player.server).clear(player.getUUID());
        syncWaypoint(player);
    }
}
