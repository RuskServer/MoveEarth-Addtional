package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.s2.S2NationSnapshot;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

final class S2PacketCodec {
    private static final int MAX_MEMBERS = 256;
    private static final int MAX_ROLES = 64;
    private static final int MAX_DIPLOMACY = 256;
    private static final int MAX_INVITATIONS = 8;
    private static final int MAX_CANDIDATES = 256;

    private S2PacketCodec() {
    }

    static void writeSnapshot(FriendlyByteBuf buffer, S2NationSnapshot value) {
        buffer.writeLong(value.revision());
        buffer.writeUtf(value.playerName(), 16);
        buffer.writeBoolean(value.serverAdmin());
        buffer.writeBoolean(value.member());
        buffer.writeUtf(value.nationName(), 64);
        buffer.writeUtf(value.nationTag(), 12);
        buffer.writeUtf(value.roleName(), 64);
        buffer.writeLong(value.ownPermissionMask());
        buffer.writeVarInt(value.onlineMembers());
        buffer.writeVarInt(value.totalMembers());
        buffer.writeVarInt(value.territoryChunks());
        buffer.writeVarInt(value.activeCores());
        buffer.writeVarLong(value.upkeep());
        buffer.writeUtf(value.siegeStatus(), 160);
        buffer.writeVarInt(Math.min(value.members().size(), MAX_MEMBERS));
        for (int index = 0; index < Math.min(value.members().size(), MAX_MEMBERS); index++) {
            var member = value.members().get(index);
            buffer.writeUUID(member.id());
            buffer.writeUtf(member.name(), 16);
            buffer.writeUtf(member.roleId(), 48);
            buffer.writeUtf(member.roleName(), 64);
            buffer.writeBoolean(member.online());
            buffer.writeLong(member.lastSeenAt());
        }
        buffer.writeVarInt(Math.min(value.roles().size(), MAX_ROLES));
        for (int index = 0; index < Math.min(value.roles().size(), MAX_ROLES); index++) {
            var role = value.roles().get(index);
            buffer.writeUtf(role.id(), 64);
            buffer.writeUtf(role.displayName(), 64);
            buffer.writeLong(role.permissionMask());
            buffer.writeVarInt(role.memberCount());
        }
        buffer.writeVarInt(Math.min(value.diplomacy().size(), MAX_DIPLOMACY));
        for (int index = 0; index < Math.min(value.diplomacy().size(), MAX_DIPLOMACY); index++) {
            var relation = value.diplomacy().get(index);
            buffer.writeUUID(relation.nationId());
            buffer.writeUtf(relation.nationName(), 64);
            buffer.writeUtf(relation.nationTag(), 12);
            buffer.writeByte(relation.state().ordinal());
            buffer.writeBoolean(relation.hostileByViewer());
        }
        buffer.writeVarInt(Math.min(value.invitations().size(), MAX_INVITATIONS));
        for (int index = 0; index < Math.min(value.invitations().size(), MAX_INVITATIONS); index++) {
            var invitation = value.invitations().get(index);
            buffer.writeUUID(invitation.nationId());
            buffer.writeUtf(invitation.nationName(), 64);
            buffer.writeUtf(invitation.nationTag(), 12);
        }
        buffer.writeVarInt(Math.min(value.inviteCandidates().size(), MAX_CANDIDATES));
        for (int index = 0; index < Math.min(value.inviteCandidates().size(), MAX_CANDIDATES); index++) {
            var candidate = value.inviteCandidates().get(index);
            buffer.writeUUID(candidate.id());
            buffer.writeUtf(candidate.name(), 16);
        }
    }

    static S2NationSnapshot readSnapshot(FriendlyByteBuf buffer) {
        long revision = buffer.readLong();
        String playerName = buffer.readUtf(16);
        boolean serverAdmin = buffer.readBoolean();
        boolean member = buffer.readBoolean();
        String nationName = buffer.readUtf(64);
        String nationTag = buffer.readUtf(12);
        String roleName = buffer.readUtf(64);
        long permissions = buffer.readLong();
        int onlineMembers = nonNegative(buffer.readVarInt(), "online members");
        int totalMembers = nonNegative(buffer.readVarInt(), "total members");
        int territoryChunks = nonNegative(buffer.readVarInt(), "territory chunks");
        int activeCores = nonNegative(buffer.readVarInt(), "active cores");
        long upkeep = Math.max(0L, buffer.readVarLong());
        String siegeStatus = buffer.readUtf(160);

        int memberCount = checkedSize(buffer.readVarInt(), MAX_MEMBERS, "member");
        List<S2NationSnapshot.MemberView> members = new ArrayList<>(memberCount);
        for (int index = 0; index < memberCount; index++) {
            members.add(new S2NationSnapshot.MemberView(buffer.readUUID(),
                    buffer.readUtf(16), buffer.readUtf(48), buffer.readUtf(64), buffer.readBoolean(),
                    buffer.readLong()));
        }

        int roleCount = checkedSize(buffer.readVarInt(), MAX_ROLES, "role");
        List<S2NationSnapshot.RoleView> roles = new ArrayList<>(roleCount);
        for (int index = 0; index < roleCount; index++) {
            roles.add(new S2NationSnapshot.RoleView(buffer.readUtf(64), buffer.readUtf(64),
                    buffer.readLong(), nonNegative(buffer.readVarInt(), "role members")));
        }
        int diplomacyCount = checkedSize(buffer.readVarInt(), MAX_DIPLOMACY, "diplomacy");
        List<S2NationSnapshot.DiplomacyView> diplomacy = new ArrayList<>(diplomacyCount);
        for (int index = 0; index < diplomacyCount; index++) {
            diplomacy.add(new S2NationSnapshot.DiplomacyView(buffer.readUUID(),
                    buffer.readUtf(64), buffer.readUtf(12),
                    S2NationSnapshot.DiplomacyState.fromNetworkId(buffer.readUnsignedByte()),
                    buffer.readBoolean()));
        }
        int invitationCount = checkedSize(buffer.readVarInt(), MAX_INVITATIONS, "invitation");
        List<S2NationSnapshot.InvitationView> invitations = new ArrayList<>(invitationCount);
        for (int index = 0; index < invitationCount; index++) {
            invitations.add(new S2NationSnapshot.InvitationView(
                    buffer.readUUID(), buffer.readUtf(64), buffer.readUtf(12)));
        }
        int candidateCount = checkedSize(buffer.readVarInt(), MAX_CANDIDATES, "candidate");
        List<S2NationSnapshot.CandidateView> candidates = new ArrayList<>(candidateCount);
        for (int index = 0; index < candidateCount; index++) {
            candidates.add(new S2NationSnapshot.CandidateView(buffer.readUUID(), buffer.readUtf(16)));
        }
        return new S2NationSnapshot(revision, playerName, serverAdmin, member,
                nationName, nationTag, roleName, permissions, onlineMembers, totalMembers,
                territoryChunks, activeCores, upkeep, siegeStatus, members, roles, diplomacy,
                invitations, candidates);
    }

    private static int checkedSize(int value, int maximum, String name) {
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException("Invalid S2 " + name + " count: " + value);
        }
        return value;
    }

    private static int nonNegative(int value, String name) {
        if (value < 0) throw new IllegalArgumentException("Invalid S2 " + name + ": " + value);
        return value;
    }
}
