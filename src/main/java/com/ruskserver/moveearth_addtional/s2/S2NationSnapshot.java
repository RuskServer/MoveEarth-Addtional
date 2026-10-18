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
        String ownerName,
        long ownPermissionMask,
        int onlineMembers,
        int totalMembers,
        int territoryChunks,
        int activeCores,
        long upkeep,
        String siegeStatus,
        boolean vaultConfigured,
        String vaultDimension,
        int vaultChunkX,
        int vaultChunkZ,
        long vaultChangeCooldownTicks,
        List<SiegeView> sieges,
        List<PeaceView> peaceProposals,
        List<TruceView> truces,
        List<PrisonerView> prisoners,
        List<MemberView> members,
        List<RoleView> roles,
        List<DiplomacyView> diplomacy,
        List<InvitationView> invitations,
        List<CandidateView> inviteCandidates,
        com.ruskserver.moveearth_addtional.s2.notification.NotificationAttention notificationAttention,
        HomeStatus home,
        List<ProtectionView> protections) {

    public S2NationSnapshot {
        if (home == null) home = HomeStatus.EMPTY;
        if (notificationAttention == null)
            notificationAttention = com.ruskserver.moveearth_addtional.s2.notification.NotificationAttention.NONE;
        playerName = safe(playerName);
        nationName = safe(nationName);
        nationTag = safe(nationTag);
        roleName = safe(roleName);
        ownerName = safe(ownerName);
        siegeStatus = safe(siegeStatus);
        vaultDimension = safe(vaultDimension);
        sieges = sieges == null ? List.of() : List.copyOf(sieges);
        peaceProposals = peaceProposals == null ? List.of() : List.copyOf(peaceProposals);
        truces = truces == null ? List.of() : List.copyOf(truces);
        prisoners = prisoners == null ? List.of() : List.copyOf(prisoners);
        vaultChangeCooldownTicks = Math.max(0L, vaultChangeCooldownTicks);
        onlineMembers = Math.max(0, onlineMembers);
        totalMembers = Math.max(onlineMembers, totalMembers);
        territoryChunks = Math.max(0, territoryChunks);
        activeCores = Math.max(0, activeCores);
        upkeep = Math.max(0L, upkeep);
        members = members == null ? List.of() : List.copyOf(members);
        roles = roles == null ? List.of() : List.copyOf(roles);
        diplomacy = diplomacy == null ? List.of() : List.copyOf(diplomacy);
        invitations = invitations == null ? List.of() : List.copyOf(invitations);
        inviteCandidates = inviteCandidates == null ? List.of() : List.copyOf(inviteCandidates);
        protections = protections == null ? List.of() : List.copyOf(protections);
    }

    public S2NationSnapshot(long revision, String playerName, boolean serverAdmin, boolean member,
                            String nationName, String nationTag, String roleName, long ownPermissionMask,
                            int onlineMembers, int totalMembers, int territoryChunks, int activeCores,
                            long upkeep, String siegeStatus, List<SiegeView> sieges,
                            List<MemberView> members, List<RoleView> roles,
                            List<DiplomacyView> diplomacy, List<InvitationView> invitations,
                            List<CandidateView> inviteCandidates) {
        this(revision, playerName, serverAdmin, member, nationName, nationTag, roleName, "",
                ownPermissionMask, onlineMembers, totalMembers, territoryChunks, activeCores,
                upkeep, siegeStatus, false, "", 0, 0, 0L, sieges, List.of(), List.of(), List.of(), members, roles,
                diplomacy, invitations, inviteCandidates,
                com.ruskserver.moveearth_addtional.s2.notification.NotificationAttention.NONE,
                HomeStatus.EMPTY, List.of());
    }

    /**
     * Figures the hub home summarizes. {@code upkeepDueInMillis} is relative to when the snapshot was
     * built, so client and server clocks never need to agree; 0 means no upkeep is scheduled.
     * {@code pendingApplications} is only filled for members who can decide them.
     */
    public record HomeStatus(long treasury, long upkeepDueInMillis, boolean upkeepOverdue, int pendingApplications,
                             CapitalWall capitalWall) {
        public static final HomeStatus EMPTY = new HomeStatus(0L, 0L, false, 0, CapitalWall.FINE);

        public HomeStatus {
            treasury = Math.max(0L, treasury);
            pendingApplications = Math.max(0, pendingApplications);
            if (capitalWall == null) capitalWall = CapitalWall.FINE;
        }

        public HomeStatus(long treasury, long upkeepDueInMillis, boolean upkeepOverdue, int pendingApplications) {
            this(treasury, upkeepDueInMillis, upkeepOverdue, pendingApplications, CapitalWall.FINE);
        }
    }

    /**
     * The capital's wall at its thinnest point when it is below {@code target} layers; {@code layers} is -1
     * when there is nothing to warn about (thick enough, open, not counted yet, or no capital).
     */
    public record CapitalWall(int layers, int target, boolean weakKnown, int weakX, int weakY, int weakZ) {
        public static final CapitalWall FINE = new CapitalWall(-1, 0, false, 0, 0, 0);

        public CapitalWall {
            layers = Math.max(-1, layers);
            target = Math.max(0, target);
        }

        public boolean warn() {
            return layers >= 1 && layers < target;
        }
    }

    public static S2NationSnapshot unaffiliated(String playerName, boolean serverAdmin) {
        return unaffiliated(playerName, serverAdmin, 0L);
    }

    public static S2NationSnapshot unaffiliated(String playerName, boolean serverAdmin, long revision) {
        return new S2NationSnapshot(revision, playerName, serverAdmin, false,
                "", "", "", 0L, 0, 0, 0, 0, 0L,
                "NO ACTIVE SIEGE", List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public boolean can(S2Permission permission) {
        return serverAdmin || (ownPermissionMask & permission.mask()) != 0L;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record MemberView(UUID id, String name, String roleId, String roleName,
                             boolean online, long lastSeenAt) {
        public MemberView {
            if (id == null) id = new UUID(0L, 0L);
            name = safe(name);
            roleId = safe(roleId);
            roleName = safe(roleName);
            lastSeenAt = Math.max(0L, lastSeenAt);
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

    /**
     * One other nation as the viewer's nation sees it. For an alliance, {@code allianceEndRemainingTicks}
     * is the open time left on a termination notice (0 when none is pending), {@code grantMask} is
     * what the viewer's nation grants all of that ally's members in its territory, and
     * {@code allyMembers} (only sent to diplomacy managers) carries per-member grants.
     */
    public record DiplomacyView(UUID nationId, String nationName, String nationTag,
                                DiplomacyState state, boolean hostileByViewer,
                                long allianceEndRemainingTicks, boolean allianceEndByViewer,
                                int grantMask, List<AllyMemberView> allyMembers) {
        public DiplomacyView {
            if (nationId == null) nationId = new UUID(0L, 0L);
            nationName = safe(nationName);
            nationTag = safe(nationTag);
            if (state == null) state = DiplomacyState.NEUTRAL;
            allianceEndRemainingTicks = Math.max(0L, allianceEndRemainingTicks);
            allyMembers = allyMembers == null ? List.of() : List.copyOf(allyMembers);
        }

        public DiplomacyView(UUID nationId, String nationName, String nationTag,
                             DiplomacyState state, boolean hostileByViewer) {
            this(nationId, nationName, nationTag, state, hostileByViewer, 0L, false, 0, List.of());
        }

        public boolean allianceEnding() {
            return state == DiplomacyState.ALLIED && allianceEndRemainingTicks > 0L;
        }
    }

    public record AllyMemberView(UUID id, String name, int grantMask) {
        public AllyMemberView {
            if (id == null) id = new UUID(0L, 0L);
            name = safe(name);
        }
    }

    public record SiegeView(UUID id, UUID opponentNationId, String opponentName, String opponentTag, boolean attacker,
                            boolean individualAttacker,
                            SiegePhase phase, long remainingTicks, String dimension,
                            int coreX, int coreY, int coreZ, int coreHealth, int coreMaximumHealth,
                            long counterCaptureTicks, long counterRequiredTicks, int fallStage,
                            boolean offlineDefenseActive) {
        public SiegeView {
            if (id == null) id = new UUID(0L, 0L);
            if (opponentNationId == null) opponentNationId = new UUID(0L, 0L);
            opponentName = safe(opponentName);
            opponentTag = safe(opponentTag);
            if (phase == null) phase = SiegePhase.INITIAL_LOCK;
            remainingTicks = Math.max(0L, remainingTicks);
            dimension = safe(dimension);
            coreMaximumHealth = Math.max(1, coreMaximumHealth);
            coreHealth = Math.max(0, Math.min(coreMaximumHealth, coreHealth));
            counterCaptureTicks = Math.max(0L, counterCaptureTicks);
            counterRequiredTicks = Math.max(0L, counterRequiredTicks);
            fallStage = Math.max(0, Math.min(3, fallStage));
        }

        public SiegeView(UUID id, String opponentName, String opponentTag, boolean attacker,
                         SiegePhase phase, long remainingTicks, String dimension,
                         int coreX, int coreY, int coreZ, int coreHealth, int coreMaximumHealth,
                         long counterCaptureTicks, long counterRequiredTicks, int fallStage,
                         boolean offlineDefenseActive) {
            this(id, new UUID(0L, 0L), opponentName, opponentTag, attacker, false, phase, remainingTicks,
                    dimension, coreX, coreY, coreZ, coreHealth, coreMaximumHealth,
                    counterCaptureTicks, counterRequiredTicks, fallStage, offlineDefenseActive);
        }
    }

    public record PeaceView(UUID id, UUID opponentNationId, String opponentName, String opponentTag,
                            boolean incoming, long goldCompensation, long remainingTicks) {
        public PeaceView {
            if (id == null) id = new UUID(0L, 0L);
            if (opponentNationId == null) opponentNationId = new UUID(0L, 0L);
            opponentName = safe(opponentName);
            opponentTag = safe(opponentTag);
            goldCompensation = Math.max(0L, goldCompensation);
            remainingTicks = Math.max(0L, remainingTicks);
        }
    }

    /**
     * A limit on attacking that the viewer should be able to see. {@code own} marks one on the viewer's
     * own nation; otherwise {@code nationId} is the other nation it concerns. {@code count} is the number
     * of cores it covers (1 for a whole nation), and {@code remainingTicks} counts server-open time.
     */
    public record ProtectionView(UUID nationId, String nationName, String nationTag, ProtectionKind kind,
                                 boolean own, int count, long remainingTicks) {
        public ProtectionView {
            if (nationId == null) nationId = new UUID(0L, 0L);
            nationName = safe(nationName);
            nationTag = safe(nationTag);
            if (kind == null) kind = ProtectionKind.CAPITAL_REBUILDING;
            count = Math.max(1, count);
            remainingTicks = Math.max(0L, remainingTicks);
        }
    }

    public enum ProtectionKind {
        /** The nation's capital fell: none of its land may be damaged until the rebuilding truce ends. */
        CAPITAL_REBUILDING,
        /** Outposts that fell are held by a settlement truce and may not be damaged. */
        OUTPOSTS,
        /** The viewer's side failed a siege on these cores and may not attack them again yet. */
        FAILED_LOCKOUT,
        /** The viewer's nation is recovering under protection and cannot start sieges. */
        RECOVERY_RESTRICTION;

        public static ProtectionKind fromNetworkId(int id) {
            return id >= 0 && id < values().length ? values()[id] : CAPITAL_REBUILDING;
        }
    }

    public record TruceView(UUID opponentNationId, String opponentName, String opponentTag,
                            long remainingTicks) {
        public TruceView {
            if (opponentNationId == null) opponentNationId = new UUID(0L, 0L);
            opponentName = safe(opponentName);
            opponentTag = safe(opponentTag);
            remainingTicks = Math.max(0L, remainingTicks);
        }
    }

    public record PrisonerView(UUID playerId, String playerName, UUID opponentNationId,
                               String opponentName, String opponentTag, boolean heldByViewer,
                               long remainingTicks) {
        public PrisonerView {
            if (playerId == null) playerId = new UUID(0L, 0L);
            if (opponentNationId == null) opponentNationId = new UUID(0L, 0L);
            playerName = safe(playerName);
            opponentName = safe(opponentName);
            opponentTag = safe(opponentTag);
            remainingTicks = Math.max(0L, remainingTicks);
        }

        public PrisonerView(UUID playerId, String playerName, UUID opponentNationId,
                            String opponentName, String opponentTag, boolean heldByViewer) {
            this(playerId, playerName, opponentNationId, opponentName, opponentTag, heldByViewer, 0L);
        }
    }

    public enum SiegePhase {
        INITIAL_LOCK, ROLLING, FALLEN;

        public static SiegePhase fromNetworkId(int id) {
            return id >= 0 && id < values().length ? values()[id] : INITIAL_LOCK;
        }
    }

    public enum DiplomacyState {
        NEUTRAL, OUTGOING_REQUEST, INCOMING_REQUEST, ALLIED, HOSTILE;

        public static DiplomacyState fromNetworkId(int id) {
            return id >= 0 && id < values().length ? values()[id] : NEUTRAL;
        }
    }
}
