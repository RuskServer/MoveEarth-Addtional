package com.ruskserver.moveearth_addtional.s2.nation;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.s2c.nation.S2C_NationNameplatesPacket;
import com.ruskserver.moveearth_addtional.s2.dispatch.DispatchContractSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeParticipationSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps every client's nameplate prefixes in step with nations and dispatch participation.
 *
 * <p>Each viewer gets one full list when it logs in and only deltas after: when the nameplate state
 * changes ({@link NationSavedData#nameplateRevision()}, which ignores role edits, applications and
 * other revision bumps, or dispatch participation) entries are recomputed and only those that differ
 * from what that viewer was last sent go out; when players merely log in or out, only the joining
 * targets are computed and leaving ones are removed. Entries are computed once per distinct viewer
 * perspective (nation and active dispatch), not once per viewer.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class NationNameplateSync {
    private static long lastNameplateRevision = Long.MIN_VALUE;
    private static long lastParticipationRevision = Long.MIN_VALUE;
    private static Set<UUID> lastOnline = Set.of();
    /** Per viewer, what it was last sent, by target. */
    private static final Map<UUID, Map<UUID, S2C_NationNameplatesPacket.Entry>> SENT = new HashMap<>();

    private NationNameplateSync() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % 10L != 0L) return;
        NationSavedData nations = NationSavedData.get(server);
        SiegeParticipationSavedData participations = SiegeParticipationSavedData.get(server);
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        Set<UUID> online = players.stream().map(ServerPlayer::getUUID)
                .collect(java.util.stream.Collectors.toSet());
        long nameplateRevision = nations.nameplateRevision();
        boolean stateChanged = nameplateRevision != lastNameplateRevision
                || participations.revision() != lastParticipationRevision;
        boolean freshViewer = players.stream().anyMatch(player -> !SENT.containsKey(player.getUUID()));
        if (!stateChanged && !freshViewer && online.equals(lastOnline)) return;
        Set<UUID> joined = new HashSet<>(online);
        joined.removeAll(lastOnline);
        lastNameplateRevision = nameplateRevision;
        lastParticipationRevision = participations.revision();
        lastOnline = Set.copyOf(online);
        SENT.keySet().retainAll(online);

        Map<UUID, TargetProfile> targets = new LinkedHashMap<>();
        for (ServerPlayer target : players) {
            targets.put(target.getUUID(), targetProfile(target, nations, participations));
        }
        Map<ViewerKey, Map<UUID, S2C_NationNameplatesPacket.Entry>> perspectives = new HashMap<>();
        for (ServerPlayer viewer : players) {
            Map<UUID, S2C_NationNameplatesPacket.Entry> sent = SENT.get(viewer.getUUID());
            boolean fresh = sent == null;
            if (fresh) {
                sent = new HashMap<>();
                SENT.put(viewer.getUUID(), sent);
            }
            ViewerKey key = viewerKey(viewer, nations, participations);
            Map<UUID, S2C_NationNameplatesPacket.Entry> all = perspectives.computeIfAbsent(key,
                    ignored -> entriesFor(key, targets.values(), nations));
            Map<UUID, S2C_NationNameplatesPacket.Entry> desired = fresh || stateChanged ? all : only(all, joined);
            NameplateDelta.Change<S2C_NationNameplatesPacket.Entry> change = NameplateDelta.apply(sent, desired, online);
            if (fresh) {
                send(viewer, true, List.copyOf(sent.values()), List.of());
            } else if (!change.isEmpty()) {
                send(viewer, false, change.upserts(), change.removed());
            }
        }
    }

    private static void send(ServerPlayer viewer, boolean replace, List<S2C_NationNameplatesPacket.Entry> entries,
                             List<UUID> removed) {
        for (var chunk : NameplateDelta.chunks(replace, entries, removed, S2C_NationNameplatesPacket.MAX_ENTRIES)) {
            PacketDistributor.sendToPlayer(viewer,
                    new S2C_NationNameplatesPacket(chunk.replace(), chunk.entries(), chunk.removed()));
        }
    }

    private static Map<UUID, S2C_NationNameplatesPacket.Entry> only(
            Map<UUID, S2C_NationNameplatesPacket.Entry> entries, Set<UUID> targets) {
        Map<UUID, S2C_NationNameplatesPacket.Entry> selected = new HashMap<>();
        for (UUID target : targets) {
            S2C_NationNameplatesPacket.Entry entry = entries.get(target);
            if (entry != null) selected.put(target, entry);
        }
        return selected;
    }

    /** Everything about a viewer that its nameplate colours depend on. */
    private record ViewerKey(UUID nation, UUID siegeId, UUID combatNation) { }

    private static ViewerKey viewerKey(ServerPlayer viewer, NationSavedData nations,
                                       SiegeParticipationSavedData participations) {
        SiegeParticipationSavedData.Participation participation = activeParticipation(
                viewer.server, participations.forPlayer(viewer.getUUID()).orElse(null));
        return new ViewerKey(nations.nationIdFor(viewer.getUUID()).orElse(null),
                participation == null ? null : participation.siegeId(),
                participation == null ? null : participation.combatNation());
    }

    private static Map<UUID, S2C_NationNameplatesPacket.Entry> entriesFor(ViewerKey viewer,
            java.util.Collection<TargetProfile> targets, NationSavedData nations) {
        Map<UUID, S2C_NationNameplatesPacket.Entry> entries = new HashMap<>();
        for (TargetProfile target : targets) entries.put(target.playerId(), entryFor(viewer, target, nations));
        return entries;
    }

    private static S2C_NationNameplatesPacket.Entry entryFor(ViewerKey viewer, TargetProfile target,
                                                              NationSavedData nations) {
        if (target.nationId() == null) {
            return new S2C_NationNameplatesPacket.Entry(target.playerId(), "", NationNameplateRelation.NEUTRAL);
        }
        UUID viewerNation = viewer.nation();
        boolean viewerInSameSiege = viewer.siegeId() != null && target.participation() != null
                && viewer.siegeId().equals(target.participation().siegeId());
        boolean sharedBattle = target.participation() != null && (viewerInSameSiege
                || viewerNation != null && (viewerNation.equals(target.participation().combatNation())
                || target.opponentNation() != null && viewerNation.equals(target.opponentNation())));
        UUID targetRelationNation = sharedBattle ? target.participation().combatNation() : target.nationId();
        UUID viewerRelationNation = sharedBattle && viewerInSameSiege ? viewer.combatNation() : viewerNation;
        boolean same = viewerRelationNation != null && viewerRelationNation.equals(targetRelationNation);
        boolean allied = viewerRelationNation != null && nations.isAllied(viewerRelationNation, targetRelationNation);
        boolean hostile = sharedBattle && !same || viewerRelationNation != null
                && nations.relation(viewerRelationNation, targetRelationNation)
                == NationSavedData.DiplomacyRelation.HOSTILE;
        NationNameplateRelation relation = NationNameplateRelation.resolve(same, allied, hostile);
        return new S2C_NationNameplatesPacket.Entry(target.playerId(),
                target.prefix() + (target.participation() == null ? "" : "[派遣] "), relation);
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

    /** A player who relogs must get a full list again: their client dropped its entries on logout. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        lastNameplateRevision = Long.MIN_VALUE;
        lastParticipationRevision = Long.MIN_VALUE;
        lastOnline = Set.of();
        SENT.clear();
    }

    private record TargetProfile(UUID playerId, UUID nationId, String prefix,
                                 SiegeParticipationSavedData.Participation participation,
                                 UUID opponentNation) { }
}
