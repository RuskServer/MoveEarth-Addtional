package com.ruskserver.moveearth_addtional.s2.nation;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.S2C_NationNameplatesPacket;
import com.ruskserver.moveearth_addtional.s2.dispatch.DispatchContractSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeParticipationSavedData;
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
    private static long lastParticipationRevision = Long.MIN_VALUE;
    private static Set<UUID> lastOnline = Set.of();

    private NationNameplateSync() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % 10L != 0L) return;
        NationSavedData nations = NationSavedData.get(server);
        SiegeParticipationSavedData participations = SiegeParticipationSavedData.get(server);
        Set<UUID> online = server.getPlayerList().getPlayers().stream()
                .map(ServerPlayer::getUUID).collect(java.util.stream.Collectors.toSet());
        if (nations.revision() == lastRevision && participations.revision() == lastParticipationRevision
                && online.equals(lastOnline)) return;
        lastRevision = nations.revision();
        lastParticipationRevision = participations.revision();
        lastOnline = Set.copyOf(online);
        List<TargetProfile> targets = server.getPlayerList().getPlayers().stream()
                .map(target -> targetProfile(target, nations, participations))
                .toList();
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(viewer, snapshotFor(viewer, nations, participations, targets));
        }
    }

    private static S2C_NationNameplatesPacket snapshotFor(ServerPlayer viewer, NationSavedData nations,
                                                           SiegeParticipationSavedData participations,
                                                           List<TargetProfile> targets) {
        UUID viewerNation = nations.nationIdFor(viewer.getUUID()).orElse(null);
        SiegeParticipationSavedData.Participation viewerParticipation = activeParticipation(
                viewer.server, participations.forPlayer(viewer.getUUID()).orElse(null));
        List<S2C_NationNameplatesPacket.Entry> entries = targets.stream().map(target -> {
            if (target.nationId() == null) {
                return new S2C_NationNameplatesPacket.Entry(target.playerId(), "",
                        NationNameplateRelation.NEUTRAL);
            }
            boolean sharedBattle = target.participation() != null && (viewerParticipation != null
                    && viewerParticipation.siegeId().equals(target.participation().siegeId())
                    || viewerNation != null && (viewerNation.equals(target.participation().combatNation())
                    || target.opponentNation() != null && viewerNation.equals(target.opponentNation())));
            UUID targetRelationNation = sharedBattle ? target.participation().combatNation() : target.nationId();
            UUID viewerRelationNation = sharedBattle && viewerParticipation != null
                    && viewerParticipation.siegeId().equals(target.participation().siegeId())
                    ? viewerParticipation.combatNation() : viewerNation;
            boolean same = viewerRelationNation != null && viewerRelationNation.equals(targetRelationNation);
            boolean allied = viewerRelationNation != null && nations.isAllied(viewerRelationNation, targetRelationNation);
            boolean hostile = sharedBattle && !same || viewerRelationNation != null
                    && nations.relation(viewerRelationNation, targetRelationNation)
                    == NationSavedData.DiplomacyRelation.HOSTILE;
            NationNameplateRelation relation = NationNameplateRelation.resolve(same, allied, hostile);
            return new S2C_NationNameplatesPacket.Entry(target.playerId(),
                    target.prefix() + (target.participation() == null ? "" : "[派遣] "), relation);
        }).toList();
        return new S2C_NationNameplatesPacket(entries);
    }

    private static TargetProfile targetProfile(ServerPlayer target, NationSavedData nations,
                                               SiegeParticipationSavedData participations) {
        NationSavedData.Nation nation = nations.nationFor(target.getUUID()).orElse(null);
        SiegeParticipationSavedData.Participation participation = activeParticipation(target.server,
                participations.forPlayer(target.getUUID()).orElse(null));
        UUID opponent = null;
        if (participation != null && participation.contractId() != null) {
            DispatchContractSavedData.Contract contract = DispatchContractSavedData.get(target.server)
                    .byId(participation.contractId()).orElse(null);
            opponent = contract == null ? null : contract.opponentNation();
        }
        if (nation == null) return new TargetProfile(target.getUUID(), null, "", participation, opponent);
        String prefix = nation.tag().isBlank()
                ? "[" + nation.name() + "] " : "[" + nation.tag() + "] ";
        return new TargetProfile(target.getUUID(), nation.id(), prefix, participation, opponent);
    }

    private static SiegeParticipationSavedData.Participation activeParticipation(MinecraftServer server,
            SiegeParticipationSavedData.Participation participation) {
        if (participation == null || participation.contractId() == null) return null;
        return DispatchContractSavedData.get(server).byId(participation.contractId())
                .filter(contract -> contract.state() == DispatchContractSavedData.State.ACTIVE
                        && participation.siegeId().equals(contract.siegeId()))
                .map(ignored -> participation).orElse(null);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        lastRevision = Long.MIN_VALUE;
        lastParticipationRevision = Long.MIN_VALUE;
        lastOnline = Set.of();
    }

    private record TargetProfile(UUID playerId, UUID nationId, String prefix,
                                 SiegeParticipationSavedData.Participation participation,
                                 UUID opponentNation) { }
}
