package com.ruskserver.moveearth_addtional.s2;

import java.util.List;
import java.util.UUID;

/**
 * Read-only, server-authoritative view consumed by the S2 hub.
 * This deliberately contains no Minecraft types so the future nation store can
 * construct and test snapshots without depending on the client.
 */
public record S2NationSnapshot(
        long revision,
        String playerName,
        boolean serverAdmin,
        boolean member,
        String nationName,
        String nationTag,
        String roleName,
        long ownPermissionMask,
        int onlineMembers,
        int totalMembers,
        int territoryChunks,
        int activeCores,
        long upkeep,
        String siegeStatus,
        List<MemberView> members,
        List<RoleView> roles,
        List<InvitationView> invitations,
        List<CandidateView> inviteCandidates) {

    public S2NationSnapshot {
        playerName = safe(playerName);
        nationName = safe(nationName);
        nationTag = safe(nationTag);
        roleName = safe(roleName);
        siegeStatus = safe(siegeStatus);
        onlineMembers = Math.max(0, onlineMembers);
        totalMembers = Math.max(onlineMembers, totalMembers);
        territoryChunks = Math.max(0, territoryChunks);
        activeCores = Math.max(0, activeCores);
        upkeep = Math.max(0L, upkeep);
        members = members == null ? List.of() : List.copyOf(members);
        roles = roles == null ? List.of() : List.copyOf(roles);
        invitations = invitations == null ? List.of() : List.copyOf(invitations);
        inviteCandidates = inviteCandidates == null ? List.of() : List.copyOf(inviteCandidates);
    }

    public static S2NationSnapshot unaffiliated(String playerName, boolean serverAdmin) {
        return unaffiliated(playerName, serverAdmin, 0L);
    }

    public static S2NationSnapshot unaffiliated(String playerName, boolean serverAdmin, long revision) {
        return new S2NationSnapshot(revision, playerName, serverAdmin, false,
                "", "", "", 0L, 0, 0, 0, 0, 0L,
                "NO ACTIVE SIEGE", List.of(), List.of(), List.of(), List.of());
    }

    public boolean can(S2Permission permission) {
        return serverAdmin || (ownPermissionMask & permission.mask()) != 0L;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record MemberView(UUID id, String name, String roleId, String roleName, boolean online) {
        public MemberView {
            if (id == null) id = new UUID(0L, 0L);
            name = safe(name);
            roleId = safe(roleId);
            roleName = safe(roleName);
        }
    }

    public record InvitationView(UUID nationId, String nationName, String nationTag) {
        public InvitationView {
            if (nationId == null) nationId = new UUID(0L, 0L);
            nationName = safe(nationName);
            nationTag = safe(nationTag);
        }
    }

    public record CandidateView(UUID id, String name) {
        public CandidateView {
            if (id == null) id = new UUID(0L, 0L);
            name = safe(name);
        }
    }

    public record RoleView(String id, String displayName, long permissionMask, int memberCount) {
        public RoleView {
            id = safe(id);
            displayName = safe(displayName);
            memberCount = Math.max(0, memberCount);
        }

        public int permissionCount() {
            return S2Permission.fromMask(permissionMask).size();
        }
    }
}
