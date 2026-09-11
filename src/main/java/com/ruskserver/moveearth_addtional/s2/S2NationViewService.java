package com.ruskserver.moveearth_addtional.s2;

import com.ruskserver.moveearth_addtional.network.S2C_S2HubSnapshotPacket;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;

/** Server-only boundary between the S2 UI and the nation persistence layer. */
public final class S2NationViewService {
    public static final S2NationViewService INSTANCE = new S2NationViewService();

    private S2NationViewService() {
    }

    public S2NationSnapshot snapshotFor(ServerPlayer player) {
        boolean serverAdmin = player.createCommandSourceStack().hasPermission(2);
        NationSavedData data = NationSavedData.get(player.server);
        data.updateKnownName(player.getUUID(), player.getGameProfile().getName());
        NationSavedData.Nation nation = data.nationFor(player.getUUID()).orElse(null);
        if (nation == null) {
            var invitations = data.invitationFor(player.getUUID()).stream()
                    .map(invite -> data.nation(invite.nationId()).orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .map(invitedNation -> new S2NationSnapshot.InvitationView(
                            invitedNation.id(), invitedNation.name(), invitedNation.tag()))
                    .toList();
            return new S2NationSnapshot(data.revision(), player.getGameProfile().getName(),
                    serverAdmin, false, "", "", "", 0L, 0, 0, 0, 0, 0L,
                    "NO ACTIVE SIEGE", java.util.List.of(), java.util.List.of(), invitations,
                    java.util.List.of());
        }

        NationSavedData.Member ownMember = nation.members().get(player.getUUID());
        NationSavedData.Role ownRole = ownMember == null ? null : nation.roles().get(ownMember.roleId());
        long permissions = ownRole == null ? 0L : ownRole.permissionMask();
        var members = nation.members().values().stream()
                .map(member -> new S2NationSnapshot.MemberView(member.id(), member.lastKnownName(), member.roleId(),
                        java.util.Optional.ofNullable(nation.roles().get(member.roleId()))
                                .map(NationSavedData.Role::displayName).orElse("Member"),
                        player.server.getPlayerList().getPlayer(member.id()) != null))
                .sorted(Comparator.comparing(S2NationSnapshot.MemberView::online).reversed()
                        .thenComparing(S2NationSnapshot.MemberView::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        var roles = nation.roles().values().stream()
                .map(role -> new S2NationSnapshot.RoleView(role.id(), role.displayName(), role.permissionMask(),
                        (int) nation.members().values().stream()
                                .filter(member -> role.id().equals(member.roleId())).count()))
                .toList();
        int online = (int) members.stream().filter(S2NationSnapshot.MemberView::online).count();
        boolean canManageMembers = (permissions & S2Permission.MANAGE_MEMBERS.mask()) != 0L;
        var candidates = canManageMembers ? player.server.getPlayerList().getPlayers().stream()
                .filter(candidate -> !candidate.getUUID().equals(player.getUUID()))
                .filter(candidate -> data.nationFor(candidate.getUUID()).isEmpty())
                .filter(candidate -> data.invitationFor(candidate.getUUID()).isEmpty())
                .map(candidate -> new S2NationSnapshot.CandidateView(
                        candidate.getUUID(), candidate.getGameProfile().getName()))
                .sorted(Comparator.comparing(S2NationSnapshot.CandidateView::name,
                        String.CASE_INSENSITIVE_ORDER)).toList() : java.util.List.<S2NationSnapshot.CandidateView>of();
        TerritorySavedData territories = TerritorySavedData.get(player.server);
        return new S2NationSnapshot(data.revision(), player.getGameProfile().getName(), serverAdmin,
                true, nation.name(), nation.tag(), ownRole == null ? "Member" : ownRole.displayName(), permissions,
                online, members.size(), territories.reservedChunkCount(nation.id()),
                territories.coreCount(nation.id()), 0L, "NO ACTIVE SIEGE", members, roles,
                java.util.List.of(), candidates);
    }

    public void sendHub(ServerPlayer player, S2HubTab tab) {
        PacketDistributor.sendToPlayer(player, new S2C_S2HubSnapshotPacket(tab, snapshotFor(player)));
    }
}
