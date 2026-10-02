package com.ruskserver.moveearth_addtional.warehouse;

import com.ruskserver.moveearth_addtional.ModSounds;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_WarehouseZonesPacket;
import com.ruskserver.moveearth_addtional.s2.time.OpenTimeService;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tells players where Warehouses are and what stepping in does; see
 * {@link WarehouseZoneView}. Clients draw each footprint as a force field;
 * players about to walk into an armed one are warned, and those who set one
 * off are told plainly that the guards are coming.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class WarehouseZoneService {
    /**
     * Only Warehouses this close are sent. The field shows from 32 blocks, and a player
     * updated once a second cannot cross the rest of this between updates; sending
     * every site would hand a modified client a live map of all of them, raids included.
     */
    private static final double SYNC_RADIUS = 256.0D;
    private static final Map<UUID, List<S2C_WarehouseZonesPacket.Zone>> LAST_SENT = new HashMap<>();

    private WarehouseZoneService() { }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        List<S2C_WarehouseZonesPacket.Zone> zones = zones(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            List<S2C_WarehouseZonesPacket.Zone> nearby = near(player, zones);
            // A first, empty list is still sent, so the client starts from a known state.
            List<S2C_WarehouseZonesPacket.Zone> previous = LAST_SENT.put(player.getUUID(), nearby);
            if (!nearby.equals(previous)) PacketDistributor.sendToPlayer(player, new S2C_WarehouseZonesPacket(nearby));
        }
        warnApproachingPlayers(server);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_SENT.clear();
    }

    private static List<S2C_WarehouseZonesPacket.Zone> near(ServerPlayer player,
                                                           List<S2C_WarehouseZonesPacket.Zone> zones) {
        var dimension = player.level().dimension().location();
        return zones.stream()
                .filter(zone -> zone.dimension().equals(dimension)
                        && WarehouseZoneView.distanceToFootprint(zone.min().getX(), zone.min().getZ(),
                        player.getX(), player.getZ()) <= SYNC_RADIUS)
                .toList();
    }

    /** Called when a player walking into {@code site} has just called out its guards. */
    static void announceDetected(ServerLevel level, WarehouseSites.Site site) {
        Component title = Component.translatable("title.moveearth_addtional.warehouse.detected")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        Component subtitle = Component.translatable("title.moveearth_addtional.warehouse.detected.subtitle")
                .withStyle(ChatFormatting.GOLD);
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !WarehouseSitePolicy.insideFightZone(site.min().getX(), site.min().getY(),
                    site.min().getZ(), player.getBlockX(), player.getBlockY(), player.getBlockZ())) continue;
            player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.sendSystemMessage(MoveEarthMessage.warning(Component.translatable(
                    "message.moveearth_addtional.warehouse.detected")));
            player.playNotifySound(ModSounds.SERVER_NOTICE.get(), SoundSource.MASTER, 1.0F, 0.8F);
        }
    }

    private static void warnApproachingPlayers(MinecraftServer server) {
        if (!OpenTimeService.isOpen(server)) return;
        ServerLevel level = server.overworld();
        WarehouseEncounterState state = WarehouseEncounterState.get(server);
        for (WarehouseSites.Site site : WarehouseSites.get(server).all()) {
            if (!site.dimension().equals(level.dimension().location())
                    || state.get(site.regionId()).phase() != WarehouseEncounterState.Phase.DORMANT) continue;
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator() || player.isCreative()
                        || !WarehouseZoneView.approaching(site.min().getX(), site.min().getY(), site.min().getZ(),
                        player.getBlockX(), player.getBlockY(), player.getBlockZ())) continue;
                player.displayClientMessage(MoveEarthMessage.warning(Component.translatable(
                        "message.moveearth_addtional.warehouse.approaching")), true);
            }
        }
    }

    private static List<S2C_WarehouseZonesPacket.Zone> zones(MinecraftServer server) {
        boolean open = OpenTimeService.isOpen(server);
        WarehouseEncounterState state = WarehouseEncounterState.get(server);
        return WarehouseSites.get(server).all().stream()
                .sorted(Comparator.comparingInt(WarehouseSites.Site::regionId))
                .map(site -> new S2C_WarehouseZonesPacket.Zone(site.dimension(), site.min(),
                        zoneState(state.get(site.regionId()).phase(), open)))
                .toList();
    }

    private static WarehouseZoneView.State zoneState(WarehouseEncounterState.Phase phase, boolean open) {
        return WarehouseZoneView.state(phase == WarehouseEncounterState.Phase.DORMANT,
                phase == WarehouseEncounterState.Phase.ACTIVE || phase == WarehouseEncounterState.Phase.ALERTED,
                open);
    }
}
