package com.ruskserver.moveearth_addtional.s2.nation;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.S2C_NationNameplatesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class NationNameplateSync {
    private static long lastRevision = Long.MIN_VALUE;
    private static Set<UUID> lastOnline = Set.of();

    private NationNameplateSync() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % 10L != 0L) return;
        NationSavedData nations = NationSavedData.get(server);
        Set<UUID> online = server.getPlayerList().getPlayers().stream()
                .map(ServerPlayer::getUUID).collect(java.util.stream.Collectors.toSet());
        if (nations.revision() == lastRevision && online.equals(lastOnline)) return;
        lastRevision = nations.revision();
        lastOnline = Set.copyOf(online);
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(viewer, snapshotFor(viewer, nations,
                    server.getPlayerList().getPlayers()));
        }
    }

    private static S2C_NationNameplatesPacket snapshotFor(ServerPlayer viewer, NationSavedData nations,
                                                           List<ServerPlayer> online) {
        UUID viewerNation = nations.nationIdFor(viewer.getUUID()).orElse(null);
        List<S2C_NationNameplatesPacket.Entry> entries = online.stream().map(target -> {
            NationSavedData.Nation targetNation = nations.nationFor(target.getUUID()).orElse(null);
            if (targetNation == null) {
                return new S2C_NationNameplatesPacket.Entry(
                        target.getUUID(), "", NationNameplateRelation.NEUTRAL);
            }
            boolean same = viewerNation != null && viewerNation.equals(targetNation.id());
            boolean allied = viewerNation != null && nations.isAllied(viewerNation, targetNation.id());
            boolean hostile = viewerNation != null
                    && nations.relation(viewerNation, targetNation.id()) == NationSavedData.DiplomacyRelation.HOSTILE;
            String prefix = targetNation.tag().isBlank()
                    ? "[" + targetNation.name() + "] " : "[" + targetNation.tag() + "] ";
            return new S2C_NationNameplatesPacket.Entry(target.getUUID(), prefix,
                    NationNameplateRelation.resolve(same, allied, hostile));
        }).toList();
        return new S2C_NationNameplatesPacket(entries);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        lastRevision = Long.MIN_VALUE;
        lastOnline = Set.of();
    }
}
