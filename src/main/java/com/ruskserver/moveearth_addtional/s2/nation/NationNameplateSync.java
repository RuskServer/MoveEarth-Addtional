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
import java.util.HashMap;
import java.util.Map;
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
        List<TargetProfile> targets = server.getPlayerList().getPlayers().stream()
                .map(target -> targetProfile(target, nations))
                .toList();
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(viewer, snapshotFor(viewer, nations, targets));
        }
    }

    private static S2C_NationNameplatesPacket snapshotFor(ServerPlayer viewer, NationSavedData nations,
                                                           List<TargetProfile> targets) {
        UUID viewerNation = nations.nationIdFor(viewer.getUUID()).orElse(null);
        Map<UUID, NationNameplateRelation> relationByNation = new HashMap<>();
        List<S2C_NationNameplatesPacket.Entry> entries = targets.stream().map(target -> {
            if (target.nationId() == null) {
                return new S2C_NationNameplatesPacket.Entry(target.playerId(), "",
                        NationNameplateRelation.NEUTRAL);
            }
            NationNameplateRelation relation = relationByNation.computeIfAbsent(target.nationId(), nationId -> {
                boolean same = viewerNation != null && viewerNation.equals(nationId);
                boolean allied = viewerNation != null && nations.isAllied(viewerNation, nationId);
                boolean hostile = viewerNation != null && nations.relation(viewerNation, nationId)
                        == NationSavedData.DiplomacyRelation.HOSTILE;
                return NationNameplateRelation.resolve(same, allied, hostile);
            });
            return new S2C_NationNameplatesPacket.Entry(target.playerId(), target.prefix(), relation);
        }).toList();
        return new S2C_NationNameplatesPacket(entries);
    }

    private static TargetProfile targetProfile(ServerPlayer target, NationSavedData nations) {
        NationSavedData.Nation nation = nations.nationFor(target.getUUID()).orElse(null);
        if (nation == null) return new TargetProfile(target.getUUID(), null, "");
        String prefix = nation.tag().isBlank()
                ? "[" + nation.name() + "] " : "[" + nation.tag() + "] ";
        return new TargetProfile(target.getUUID(), nation.id(), prefix);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        lastRevision = Long.MIN_VALUE;
        lastOnline = Set.of();
    }

    private record TargetProfile(UUID playerId, UUID nationId, String prefix) { }
}
