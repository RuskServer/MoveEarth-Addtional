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
    private static final int MAX_SIEGES = 64;
    private static final int MAX_PEACE_PROPOSALS = 32;
    private static final int MAX_TRUCES = 64;
    private static final int MAX_PRISONERS = 256;

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
        buffer.writeUtf(value.ownerName(), 32);
        buffer.writeLong(value.ownPermissionMask());
        buffer.writeVarInt(value.onlineMembers());
        buffer.writeVarInt(value.totalMembers());
        buffer.writeVarInt(value.territoryChunks());
        buffer.writeVarInt(value.activeCores());
        buffer.writeVarLong(value.upkeep());
        buffer.writeUtf(value.siegeStatus(), 160);
        buffer.writeBoolean(value.vaultConfigured());
        buffer.writeUtf(value.vaultDimension(), 128);
        buffer.writeInt(value.vaultChunkX());
        buffer.writeInt(value.vaultChunkZ());
        buffer.writeVarLong(value.vaultChangeCooldownTicks());
        buffer.writeVarInt(Math.min(value.sieges().size(), MAX_SIEGES));
        for (int index = 0; index < Math.min(value.sieges().size(), MAX_SIEGES); index++) {
            var siege = value.sieges().get(index);
            buffer.writeUUID(siege.id());
            buffer.writeUUID(siege.opponentNationId());
            buffer.writeUtf(siege.opponentName(), 64);
            buffer.writeUtf(siege.opponentTag(), 12);
            buffer.writeBoolean(siege.attacker());
            buffer.writeBoolean(siege.individualAttacker());
            buffer.writeByte(siege.phase().ordinal());
            buffer.writeVarLong(siege.remainingTicks());
            buffer.writeUtf(siege.dimension(), 128);
            buffer.writeInt(siege.coreX());
            buffer.writeInt(siege.coreY());
            buffer.writeInt(siege.coreZ());
            buffer.writeVarInt(siege.coreHealth());
            buffer.writeVarInt(siege.coreMaximumHealth());
            buffer.writeVarLong(siege.counterCaptureTicks());
            buffer.writeVarLong(siege.counterRequiredTicks());
            buffer.writeByte(siege.fallStage());
            buffer.writeBoolean(siege.offlineDefenseActive());
        }
        buffer.writeVarInt(Math.min(value.peaceProposals().size(), MAX_PEACE_PROPOSALS));
        for (int index = 0; index < Math.min(value.peaceProposals().size(), MAX_PEACE_PROPOSALS); index++) {
            var proposal = value.peaceProposals().get(index);
            buffer.writeUUID(proposal.id());
            buffer.writeUUID(proposal.opponentNationId());
            buffer.writeUtf(proposal.opponentName(), 64);
            buffer.writeUtf(proposal.opponentTag(), 12);
            buffer.writeBoolean(proposal.incoming());
            buffer.writeVarLong(proposal.goldCompensation());
            buffer.writeVarLong(proposal.remainingTicks());
        }
        buffer.writeVarInt(Math.min(value.truces().size(), MAX_TRUCES));
        for (int index = 0; index < Math.min(value.truces().size(), MAX_TRUCES); index++) {
            var truce = value.truces().get(index);
            buffer.writeUUID(truce.opponentNationId());
            buffer.writeUtf(truce.opponentName(), 64);
            buffer.writeUtf(truce.opponentTag(), 12);
            buffer.writeVarLong(truce.remainingTicks());
        }
        buffer.writeVarInt(Math.min(value.prisoners().size(), MAX_PRISONERS));
        for (int index = 0; index < Math.min(value.prisoners().size(), MAX_PRISONERS); index++) {
            var prisoner = value.prisoners().get(index);
            buffer.writeUUID(prisoner.playerId());
            buffer.writeUtf(prisoner.playerName(), 16);
            buffer.writeUUID(prisoner.opponentNationId());
            buffer.writeUtf(prisoner.opponentName(), 64);
            buffer.writeUtf(prisoner.opponentTag(), 12);
            buffer.writeBoolean(prisoner.heldByViewer());
            buffer.writeVarLong(prisoner.remainingTicks());
        }
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
        String ownerName = buffer.readUtf(32);
        long permissions = buffer.readLong();
        int onlineMembers = nonNegative(buffer.readVarInt(), "online members");
        int totalMembers = nonNegative(buffer.readVarInt(), "total members");
        int territoryChunks = nonNegative(buffer.readVarInt(), "territory chunks");
        int activeCores = nonNegative(buffer.readVarInt(), "active cores");
        long upkeep = Math.max(0L, buffer.readVarLong());
        String siegeStatus = buffer.readUtf(160);
        boolean vaultConfigured = buffer.readBoolean();
        String vaultDimension = buffer.readUtf(128);
        int vaultChunkX = buffer.readInt();
        int vaultChunkZ = buffer.readInt();
        long vaultChangeCooldownTicks = Math.max(0L, buffer.readVarLong());

        int siegeCount = checkedSize(buffer.readVarInt(), MAX_SIEGES, "siege");
        List<S2NationSnapshot.SiegeView> sieges = new ArrayList<>(siegeCount);
        for (int index = 0; index < siegeCount; index++) {
            sieges.add(new S2NationSnapshot.SiegeView(buffer.readUUID(), buffer.readUUID(), buffer.readUtf(64),
                    buffer.readUtf(12), buffer.readBoolean(), buffer.readBoolean(),
                    S2NationSnapshot.SiegePhase.fromNetworkId(buffer.readUnsignedByte()),
                    Math.max(0L, buffer.readVarLong()), buffer.readUtf(128),
                    buffer.readInt(), buffer.readInt(), buffer.readInt(),
                    nonNegative(buffer.readVarInt(), "core health"),
                    nonNegative(buffer.readVarInt(), "core maximum health"),
                    Math.max(0L, buffer.readVarLong()), Math.max(0L, buffer.readVarLong()),
                    buffer.readUnsignedByte(), buffer.readBoolean()));
        }
        int peaceCount = checkedSize(buffer.readVarInt(), MAX_PEACE_PROPOSALS, "peace proposal");
        List<S2NationSnapshot.PeaceView> peace = new ArrayList<>(peaceCount);
        for (int index = 0; index < peaceCount; index++) {
            peace.add(new S2NationSnapshot.PeaceView(buffer.readUUID(), buffer.readUUID(),
                    buffer.readUtf(64), buffer.readUtf(12), buffer.readBoolean(),
                    Math.max(0L, buffer.readVarLong()), Math.max(0L, buffer.readVarLong())));
        }
        int truceCount = checkedSize(buffer.readVarInt(), MAX_TRUCES, "truce");
        List<S2NationSnapshot.TruceView> truces = new ArrayList<>(truceCount);
        for (int index = 0; index < truceCount; index++) {
            truces.add(new S2NationSnapshot.TruceView(buffer.readUUID(), buffer.readUtf(64),
                    buffer.readUtf(12), Math.max(0L, buffer.readVarLong())));
        }
        int prisonerCount = checkedSize(buffer.readVarInt(), MAX_PRISONERS, "prisoner");
        List<S2NationSnapshot.PrisonerView> prisoners = new ArrayList<>(prisonerCount);
        for (int index = 0; index < prisonerCount; index++) {
            prisoners.add(new S2NationSnapshot.PrisonerView(buffer.readUUID(), buffer.readUtf(16),
                    buffer.readUUID(), buffer.readUtf(64), buffer.readUtf(12), buffer.readBoolean(),
                    Math.max(0L, buffer.readVarLong())));
        }

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
                nationName, nationTag, roleName, ownerName, permissions, onlineMembers, totalMembers,
                territoryChunks, activeCores, upkeep, siegeStatus, vaultConfigured, vaultDimension,
                vaultChunkX, vaultChunkZ, vaultChangeCooldownTicks, sieges, peace, truces, prisoners,
                members, roles, diplomacy,
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
