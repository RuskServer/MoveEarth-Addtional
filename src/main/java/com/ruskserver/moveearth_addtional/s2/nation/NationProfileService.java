package com.ruskserver.moveearth_addtional.s2.nation;

import com.ruskserver.moveearth_addtional.network.s2c.nation.S2C_NationProfilePacket;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Builds the profile of a nation for the diplomacy tab; see {@link S2C_NationProfilePacket} for what it holds. */
public final class NationProfileService {
    private NationProfileService() {
    }

    public static void send(ServerPlayer viewer, UUID nationId) {
        MinecraftServer server = viewer.server;
        NationSavedData nations = NationSavedData.get(server);
        NationSavedData.Nation nation = nations.nation(nationId).orElse(null);
        if (nation == null) {
            // Gone since the list was sent: an empty profile tells the panel so instead of leaving it loading.
            PacketDistributor.sendToPlayer(viewer, new S2C_NationProfilePacket(nationId, "", "", "", 0, 0, false,
                    List.of(), List.of(), List.of(), List.of()));
            return;
        }
        UUID viewerNation = nations.nationIdFor(viewer.getUUID()).orElse(null);
        boolean onlineShown = nationId.equals(viewerNation) || nations.isAllied(nationId, viewerNation);

        NationSavedData.Member owner = nation.members().get(nation.ownerId());
        List<S2C_NationProfilePacket.Member> members = nation.members().values().stream()
                .sorted(Comparator.comparing((NationSavedData.Member member) -> !member.id().equals(nation.ownerId()))
                        .thenComparing(NationSavedData.Member::lastKnownName, String.CASE_INSENSITIVE_ORDER))
                .map(member -> new S2C_NationProfilePacket.Member(member.lastKnownName(),
                        onlineShown && server.getPlayerList().getPlayer(member.id()) != null))
                .toList();

        List<S2C_NationProfilePacket.Ref> allies = new ArrayList<>();
        List<S2C_NationProfilePacket.Ref> hostiles = new ArrayList<>();
        for (NationSavedData.Nation other : nations.nations().values()) {
            if (other.id().equals(nationId)) continue;
            NationSavedData.DiplomacyRelation relation = nations.relation(nationId, other.id());
            S2C_NationProfilePacket.Ref ref = new S2C_NationProfilePacket.Ref(other.id(), other.name(), other.tag());
            if (relation == NationSavedData.DiplomacyRelation.ALLIED) allies.add(ref);
            else if (relation == NationSavedData.DiplomacyRelation.HOSTILE) hostiles.add(ref);
        }
        Comparator<S2C_NationProfilePacket.Ref> byName =
                Comparator.comparing(S2C_NationProfilePacket.Ref::name, String.CASE_INSENSITIVE_ORDER);
        allies.sort(byName);
        hostiles.sort(byName);

        List<S2C_NationProfilePacket.War> wars = new ArrayList<>();
        for (SiegeSavedData.SiegeRecord siege : SiegeSavedData.get(server).activeFor(nationId)) {
            boolean attacking = siege.attackerNation().equals(nationId);
            UUID opponentId = attacking ? siege.defenderNation() : siege.attackerNation();
            boolean solo = siege.individualAttacker() && !attacking;
            String opponentName = solo ? SiegeService.attackerName(server, nations, opponentId, true)
                    : nations.nation(opponentId).map(NationSavedData.Nation::name).orElse("?");
            String opponentTag = solo ? "SOLO" : nations.nation(opponentId).map(NationSavedData.Nation::tag).orElse("");
            wars.add(new S2C_NationProfilePacket.War(opponentName, opponentTag, attacking, solo));
        }

        TerritorySavedData territories = TerritorySavedData.get(server);
        PacketDistributor.sendToPlayer(viewer, new S2C_NationProfilePacket(nationId, nation.name(), nation.tag(),
                owner == null ? "" : owner.lastKnownName(), territories.controlledChunkCount(nationId),
                territories.activeOutpostCount(nationId), onlineShown, members, allies, hostiles, wars));
    }
}
