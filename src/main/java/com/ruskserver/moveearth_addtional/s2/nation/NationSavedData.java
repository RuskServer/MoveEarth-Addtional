package com.ruskserver.moveearth_addtional.s2.nation;

import com.ruskserver.moveearth_addtional.s2.S2Permission;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class NationSavedData extends SavedData {
    public static final String OWNER_ROLE = "owner";
    public static final String MEMBER_ROLE = "member";
    public static final int MAX_CUSTOM_ROLES = 16;

    private final Map<UUID, Nation> nations = new LinkedHashMap<>();
    private final Map<UUID, Nation> nationView = Collections.unmodifiableMap(nations);
    private final Map<UUID, UUID> nationByMember = new LinkedHashMap<>();
    private final Map<UUID, Invitation> invitations = new LinkedHashMap<>();
    private final Map<UUID, JoinApplication> joinApplications = new LinkedHashMap<>();
    private final Map<NationPair, DiplomacyRecord> diplomacy = new LinkedHashMap<>();
    /**
     * Memberships each player recently lost, oldest first; see {@link MembershipCooldownPolicy}. A later
     * loss is appended rather than replacing an earlier one, so the earliest binding holds until it ends.
     */
    private final Map<UUID, java.util.List<MembershipCooldownPolicy.Entry>> membershipCooldowns = new LinkedHashMap<>();
    private long revision;
    // Nameplate revision and join-application cooldown: in memory only, see nameplateRevision()
    // and applyToNation().
    private final NameplateRevisionTracker<NameplateState> nameplateRevision = new NameplateRevisionTracker<>();
    private final Map<UUID, Long> applicationChangedAt = new java.util.HashMap<>();

    public Optional<Nation> nationFor(UUID playerId) {
        UUID nationId = nationByMember.get(playerId);
        return Optional.ofNullable(nationId == null ? null : nations.get(nationId));
    }

    public Optional<Nation> nation(UUID nationId) {
        return Optional.ofNullable(nations.get(nationId));
    }

    public Map<UUID, Nation> nations() {
        return nationView;
    }

    public Optional<UUID> nationIdFor(UUID playerId) {
        return Optional.ofNullable(nationByMember.get(playerId));
    }

    public boolean can(UUID playerId, S2Permission permission) {
        Nation nation = nationFor(playerId).orElse(null);
        return nation != null && hasPermission(nation, playerId, permission);
    }

    public boolean isAllied(UUID firstNation, UUID secondNation) {
        if (firstNation == null || secondNation == null || firstNation.equals(secondNation)) return false;
        DiplomacyRecord record = diplomacy.get(NationPair.of(firstNation, secondNation));
        return record != null && record.allied;
    }

    public DiplomacyRelation relation(UUID viewerNation, UUID otherNation) {
        if (viewerNation == null || otherNation == null || viewerNation.equals(otherNation)) {
            return DiplomacyRelation.NEUTRAL;
        }
        DiplomacyRecord record = diplomacy.get(NationPair.of(viewerNation, otherNation));
        if (record == null) return DiplomacyRelation.NEUTRAL;
        if (record.allied) return DiplomacyRelation.ALLIED;
        if (record.isHostile(viewerNation, otherNation)) return DiplomacyRelation.HOSTILE;
        if (viewerNation.equals(record.requestFrom)) return DiplomacyRelation.OUTGOING_REQUEST;
        if (otherNation.equals(record.requestFrom)) return DiplomacyRelation.INCOMING_REQUEST;
        return DiplomacyRelation.NEUTRAL;
    }

    public boolean isHostileFrom(UUID viewerNation, UUID otherNation) {
        if (viewerNation == null || otherNation == null || viewerNation.equals(otherNation)) return false;
        DiplomacyRecord record = diplomacy.get(NationPair.of(viewerNation, otherNation));
        return record != null && record.isHostileFrom(viewerNation);
    }

    /**
     * Changes the relation between the actor's nation and another nation. {@code openNow} is the
     * server-opening clock; ending an alliance only gives notice, and the alliance itself lasts
     * until {@link #expireAlliances} runs after {@link AllianceTerminationPolicy#NOTICE_OPEN_TICKS}.
     */
    public DiplomacyResult changeDiplomacy(UUID actorId, UUID targetNationId,
                                             DiplomacyAction action, long expectedRevision, long openNow) {
        if (expectedRevision != revision) return diplomacyResult(DiplomacyStatus.STALE);
        Nation actorNation = nationFor(actorId).orElse(null);
        if (actorNation == null || !hasPermission(actorNation, actorId, S2Permission.MANAGE_DIPLOMACY)) {
            return diplomacyResult(DiplomacyStatus.NO_PERMISSION);
        }
        if (targetNationId == null || actorNation.id.equals(targetNationId) || !nations.containsKey(targetNationId)) {
            return diplomacyResult(DiplomacyStatus.NOT_FOUND);
        }
        if (action == null || action == DiplomacyAction.UNKNOWN) {
            return diplomacyResult(DiplomacyStatus.INVALID);
        }
        NationPair pair = NationPair.of(actorNation.id, targetNationId);
        DiplomacyRecord record = diplomacy.get(pair);
        if (record == null) record = new DiplomacyRecord(pair);
        DiplomacyStatus status;
        switch (action) {
            case REQUEST_ALLIANCE -> {
                if (record.allied) return diplomacyResult(DiplomacyStatus.ALREADY_ALLIED);
                if (record.anyHostile()) return diplomacyResult(DiplomacyStatus.HOSTILE_CONFLICT);
                if (record.requestFrom != null) return diplomacyResult(DiplomacyStatus.REQUEST_EXISTS);
                record.requestFrom = actorNation.id;
                status = DiplomacyStatus.REQUESTED;
            }
            case ACCEPT_ALLIANCE -> {
                if (record.requestFrom == null || !record.requestFrom.equals(targetNationId)) {
                    return diplomacyResult(DiplomacyStatus.REQUEST_NOT_FOUND);
                }
                record.requestFrom = null;
                record.allied = true;
                record.hostileFirstToSecond = false;
                record.hostileSecondToFirst = false;
                // A new alliance starts with nothing granted in either territory.
                record.clearAllianceState();
                status = DiplomacyStatus.ALLIED;
            }
            case DECLINE_ALLIANCE -> {
                if (record.requestFrom == null || !record.requestFrom.equals(targetNationId)) {
                    return diplomacyResult(DiplomacyStatus.REQUEST_NOT_FOUND);
                }
                record.requestFrom = null;
                status = DiplomacyStatus.DECLINED;
            }
            case END_ALLIANCE -> {
                AllianceTerminationPolicy.Decision decision = AllianceTerminationPolicy.declare(
                        record.allied, AllianceTerminationPolicy.pending(record.allianceEndsAt));
                if (decision != AllianceTerminationPolicy.Decision.ALLOWED) return diplomacyResult(map(decision));
                record.allianceEndsAt = AllianceTerminationPolicy.endsAt(openNow);
                record.allianceEndFrom = actorNation.id;
                // The declarer loses at once what the other nation let it do in the other's territory,
                // so the notice cannot be used to loot or wreck a soon-former ally at a chosen moment.
                // What the declarer granted stays until expiry; the other nation may re-grant if it wants.
                record.grantsFrom(targetNationId).clear();
                status = DiplomacyStatus.ALLIANCE_END_DECLARED;
            }
            case CANCEL_ALLIANCE_END -> {
                AllianceTerminationPolicy.Decision decision = AllianceTerminationPolicy.cancel(
                        record.allied, AllianceTerminationPolicy.pending(record.allianceEndsAt),
                        actorNation.id.equals(record.allianceEndFrom));
                if (decision != AllianceTerminationPolicy.Decision.ALLOWED) return diplomacyResult(map(decision));
                record.allianceEndsAt = 0L;
                record.allianceEndFrom = null;
                status = DiplomacyStatus.ALLIANCE_END_CANCELLED;
            }
            case DECLARE_HOSTILE -> {
                // Hostility would end the alliance at once and skip the termination notice.
                if (record.allied) return diplomacyResult(DiplomacyStatus.ALLIANCE_ACTIVE);
                record.allied = false;
                record.requestFrom = null;
                record.setHostile(actorNation.id, targetNationId, true);
                status = DiplomacyStatus.HOSTILE_DECLARED;
            }
            case SET_NEUTRAL -> {
                if (!record.isHostileFrom(actorNation.id)) return diplomacyResult(DiplomacyStatus.NOT_HOSTILE);
                record.setHostile(actorNation.id, targetNationId, false);
                status = DiplomacyStatus.NEUTRAL;
            }
            default -> throw new IllegalStateException("Unhandled diplomacy action: " + action);
        }
        if (record.isEmpty()) diplomacy.remove(pair); else diplomacy.put(pair, record);
        changed();
        return new DiplomacyResult(status, revision);
    }

    private DiplomacyResult diplomacyResult(DiplomacyStatus status) {
        return new DiplomacyResult(status, revision);
    }

    private static DiplomacyStatus map(AllianceTerminationPolicy.Decision decision) {
        return switch (decision) {
            case NOT_ALLIED -> DiplomacyStatus.NOT_ALLIED;
            case ALREADY_PENDING -> DiplomacyStatus.ALLIANCE_END_PENDING;
            case NOT_PENDING -> DiplomacyStatus.ALLIANCE_END_NOT_PENDING;
            case NOT_DECLARER -> DiplomacyStatus.ALLIANCE_END_NOT_DECLARER;
            case ALLOWED -> DiplomacyStatus.INVALID;
        };
    }

    /** The pending termination of an alliance, if one of the two nations has given notice. */
    public Optional<AllianceEnd> allianceEnd(UUID firstNation, UUID secondNation) {
        if (firstNation == null || secondNation == null || firstNation.equals(secondNation)) return Optional.empty();
        DiplomacyRecord record = diplomacy.get(NationPair.of(firstNation, secondNation));
        if (record == null || !record.allied || !AllianceTerminationPolicy.pending(record.allianceEndsAt)) {
            return Optional.empty();
        }
        return Optional.of(new AllianceEnd(record.allianceEndFrom, record.allianceEndsAt));
    }

    /** Ends every alliance whose notice has run out; returns them so both nations can be told. */
    public java.util.List<EndedAlliance> expireAlliances(long openNow) {
        java.util.List<EndedAlliance> ended = new java.util.ArrayList<>();
        var iterator = diplomacy.values().iterator();
        while (iterator.hasNext()) {
            DiplomacyRecord record = iterator.next();
            if (!record.allied || !AllianceTerminationPolicy.expired(record.allianceEndsAt, openNow)) continue;
            UUID declaredBy = record.allianceEndFrom;
            UUID other = record.pair.first.equals(declaredBy) ? record.pair.second : record.pair.first;
            ended.add(new EndedAlliance(declaredBy == null ? record.pair.first : declaredBy,
                    declaredBy == null ? record.pair.second : other));
            record.allied = false;
            record.clearAllianceState();
            if (record.isEmpty()) iterator.remove();
        }
        if (!ended.isEmpty()) changed();
        return java.util.List.copyOf(ended);
    }

    /**
     * Whether {@code playerId} holds {@code permission} in the territory of {@code hostNation} as a
     * member of an allied nation. Callers still check the player's own-nation role permission.
     */
    public boolean allyPermits(UUID hostNation, UUID playerId, AllyPermission permission) {
        UUID actorNation = nationByMember.get(playerId);
        if (hostNation == null || actorNation == null || hostNation.equals(actorNation)) return false;
        DiplomacyRecord record = diplomacy.get(NationPair.of(hostNation, actorNation));
        if (record == null) return false;
        AllyGrants grants = record.grantsFrom(hostNation);
        return AllyPermissionPolicy.granted(record.allied, grants.nationMask,
                grants.players.getOrDefault(playerId, 0), permission);
    }

    /** Cheap pre-check: does any allied host grant {@code permission} to this player at all? */
    public boolean hasAnyAllyGrant(UUID playerId, AllyPermission permission) {
        UUID actorNation = nationByMember.get(playerId);
        if (actorNation == null) return false;
        for (DiplomacyRecord record : diplomacy.values()) {
            if (!record.allied || !record.pair.contains(actorNation)) continue;
            AllyGrants grants = record.grantsFrom(record.pair.other(actorNation));
            if (AllyPermissionPolicy.granted(true, grants.nationMask,
                    grants.players.getOrDefault(playerId, 0), permission)) return true;
        }
        return false;
    }

    /** Nation-wide grant mask that {@code hostNation} gives members of {@code allyNation}. */
    public int allyNationGrant(UUID hostNation, UUID allyNation) {
        DiplomacyRecord record = alliedRecord(hostNation, allyNation);
        return record == null ? 0 : record.grantsFrom(hostNation).nationMask;
    }

    /** Personal grant mask that {@code hostNation} gives one member of {@code allyNation}. */
    public int allyPlayerGrant(UUID hostNation, UUID allyNation, UUID playerId) {
        DiplomacyRecord record = alliedRecord(hostNation, allyNation);
        return record == null ? 0 : record.grantsFrom(hostNation).players.getOrDefault(playerId, 0);
    }

    private DiplomacyRecord alliedRecord(UUID hostNation, UUID allyNation) {
        if (hostNation == null || allyNation == null || hostNation.equals(allyNation)) return null;
        DiplomacyRecord record = diplomacy.get(NationPair.of(hostNation, allyNation));
        return record != null && record.allied ? record : null;
    }

    /**
     * Sets one grant the actor's nation gives an allied nation in its own territory: nation-wide
     * when {@code targetPlayerId} is null, else for that member of the ally only.
     */
    public DiplomacyResult setAllyPermission(UUID actorId, UUID allyNationId, UUID targetPlayerId,
                                             AllyPermission permission, boolean enabled, long expectedRevision) {
        if (expectedRevision != revision) return diplomacyResult(DiplomacyStatus.STALE);
        if (permission == null) return diplomacyResult(DiplomacyStatus.INVALID);
        Nation actorNation = nationFor(actorId).orElse(null);
        if (actorNation == null) return diplomacyResult(DiplomacyStatus.NO_PERMISSION);
        if (allyNationId == null || actorNation.id.equals(allyNationId) || !nations.containsKey(allyNationId)) {
            return diplomacyResult(DiplomacyStatus.NOT_FOUND);
        }
        DiplomacyRecord record = alliedRecord(actorNation.id, allyNationId);
        AllyPermissionPolicy.EditDecision decision = AllyPermissionPolicy.edit(
                hasPermission(actorNation, actorId, S2Permission.MANAGE_DIPLOMACY), record != null,
                targetPlayerId != null, targetPlayerId != null && allyNationId.equals(nationByMember.get(targetPlayerId)));
        switch (decision) {
            case NO_PERMISSION -> { return diplomacyResult(DiplomacyStatus.NO_PERMISSION); }
            case NOT_ALLIED -> { return diplomacyResult(DiplomacyStatus.NOT_ALLIED); }
            case TARGET_NOT_MEMBER -> { return diplomacyResult(DiplomacyStatus.TARGET_NOT_MEMBER); }
            case ALLOWED -> { }
        }
        AllyGrants grants = record.grantsFrom(actorNation.id);
        if (targetPlayerId == null) {
            int updated = AllyPermissionPolicy.with(grants.nationMask, permission, enabled);
            if (updated == grants.nationMask) return diplomacyResult(DiplomacyStatus.PERMISSION_UNCHANGED);
            grants.nationMask = updated;
        } else {
            int current = grants.players.getOrDefault(targetPlayerId, 0);
            int updated = AllyPermissionPolicy.with(current, permission, enabled);
            if (updated == current) return diplomacyResult(DiplomacyStatus.PERMISSION_UNCHANGED);
            if (updated == 0) grants.players.remove(targetPlayerId); else grants.players.put(targetPlayerId, updated);
        }
        changed();
        return diplomacyResult(DiplomacyStatus.PERMISSION_UPDATED);
    }

    /** Personal grants follow the player's membership: they are dropped when the player leaves. */
    private void dropPersonalAllyGrants(UUID playerId) {
        for (DiplomacyRecord record : diplomacy.values()) {
            record.firstGrants.players.remove(playerId);
            record.secondGrants.players.remove(playerId);
        }
    }

    /** Open-time ticks before {@code playerId} may join or found a nation again; 0 when free. */
    public long membershipCooldownRemaining(UUID playerId, long openNow) {
        return playerId == null ? 0L
                : MembershipCooldownPolicy.joinLockRemaining(membershipCooldowns.get(playerId), openNow);
    }

    /** Open-time ticks a former member stays bound to the truces and alliances of {@link #cooldownFormerNation}. */
    public long formerNationBindingRemaining(UUID playerId, long openNow) {
        return playerId == null ? 0L : MembershipCooldownPolicy.bindingRemaining(
                membershipCooldowns.get(playerId), openNow, nations::containsKey);
    }

    /**
     * The nation whose ceasefires and alliances still bind a player who left it: the earliest
     * membership loss whose cooldown still runs and whose nation still exists.
     */
    public Optional<UUID> cooldownFormerNation(UUID playerId, long openNow) {
        if (playerId == null) return Optional.empty();
        return MembershipCooldownPolicy.binding(membershipCooldowns.get(playerId), openNow, nations::containsKey)
                .map(MembershipCooldownPolicy.Entry::formerNationId);
    }

    /** Forgets cooldowns that have run out. Does not change the revision: nothing visible changes. */
    public void pruneMembershipCooldowns(long openNow) {
        boolean pruned = false;
        var iterator = membershipCooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            java.util.List<MembershipCooldownPolicy.Entry> kept =
                    MembershipCooldownPolicy.record(entry.getValue(), null, openNow);
            if (kept.size() == entry.getValue().size()) continue;
            pruned = true;
            if (kept.isEmpty()) iterator.remove(); else entry.setValue(kept);
        }
        if (pruned) setDirty();
    }

    /**
     * Records a lost membership after any the player is still serving; an earlier binding that still
     * runs is kept, never replaced.
     *
     * @param joinLocked false for a kick: the player chose nothing, so a hostile nation must not be
     *                   able to accept a newcomer and kick them to lock them out for a night. The
     *                   truce/alliance binding still applies, so a staged kick cannot dodge a ceasefire.
     */
    private void startMembershipCooldown(UUID playerId, UUID formerNationId, long openNow, boolean joinLocked) {
        membershipCooldowns.put(playerId, MembershipCooldownPolicy.record(membershipCooldowns.get(playerId),
                new MembershipCooldownPolicy.Entry(formerNationId, MembershipCooldownPolicy.endsAt(openNow),
                        joinLocked), openNow));
    }

    private boolean inMembershipCooldown(UUID playerId, long openNow) {
        return membershipCooldownRemaining(playerId, openNow) > 0L;
    }

    public long revision() {
        return revision;
    }

    /**
     * Changes only when something a nameplate shows may have changed: which nation each player is in,
     * a nation's name or tag, or an alliance or hostility. Role edits, applications, invitations and other
     * revision bumps leave it alone, so they no longer resend every nameplate to every player. Derived
     * lazily from the state itself, so a new mutation path cannot forget to bump it.
     */
    public long nameplateRevision() {
        return nameplateRevision.revision(revision, this::nameplateState);
    }

    private NameplateState nameplateState() {
        Map<UUID, String> labels = new java.util.HashMap<>();
        nations.values().forEach(nation -> labels.put(nation.id, nation.name + '\u0000' + nation.tag));
        Map<NationPair, Integer> relations = new java.util.HashMap<>();
        diplomacy.values().forEach(record -> {
            int bits = (record.allied ? 1 : 0) | (record.hostileFirstToSecond ? 2 : 0)
                    | (record.hostileSecondToFirst ? 4 : 0);
            if (bits != 0) relations.put(record.pair, bits);
        });
        return new NameplateState(Map.copyOf(nationByMember), Map.copyOf(labels), Map.copyOf(relations));
    }

    /** What nameplates are derived from; compared by value. Transient, never saved. */
    private record NameplateState(Map<UUID, UUID> memberNations, Map<UUID, String> nationLabels,
                                  Map<NationPair, Integer> relations) { }

    public Optional<Invitation> invitationFor(UUID playerId) {
        return Optional.ofNullable(invitations.get(playerId));
    }

    public Optional<JoinApplication> joinApplicationFor(UUID playerId) {
        return Optional.ofNullable(joinApplications.get(playerId));
    }

    public java.util.List<JoinApplication> joinApplicationsFor(UUID nationId) {
        return joinApplications.values().stream()
                .filter(application -> application.nationId.equals(nationId))
                .sorted(java.util.Comparator.comparingLong(JoinApplication::requestedAt))
                .toList();
    }

    public ApplicationResult applyToNation(UUID applicantId, String applicantName, UUID nationId,
                                           long expectedRevision, long openNow) {
        if (expectedRevision != revision) return applicationResult(ApplicationStatus.STALE);
        // Each application pings the nation's managers and records analytics, so apply/cancel loops are
        // paced per player. Only successful changes start the cooldown.
        long changeAt = System.currentTimeMillis();
        if (!JoinApplicationCooldownPolicy.applyAllowed(applicationChangedAt.get(applicantId), changeAt)) {
            return applicationResult(ApplicationStatus.APPLICATION_COOLDOWN);
        }
        if (!nationByMember.containsKey(applicantId) && inMembershipCooldown(applicantId, openNow)) {
            return applicationResult(ApplicationStatus.MEMBERSHIP_COOLDOWN);
        }
        NationApplicationPolicy.Decision decision = NationApplicationPolicy.apply(
                nationByMember.containsKey(applicantId), nations.containsKey(nationId),
                joinApplications.containsKey(applicantId));
        if (decision != NationApplicationPolicy.Decision.ALLOW_APPLY) {
            return applicationResult(mapApplicationDecision(decision));
        }
        joinApplications.put(applicantId, new JoinApplication(nationId, applicantId,
                safePlayerName(applicantName), System.currentTimeMillis()));
        recordApplicationChange(applicantId, changeAt);
        changed();
        return applicationResult(ApplicationStatus.APPLIED);
    }

    public ApplicationResult cancelApplication(UUID applicantId, long expectedRevision) {
        if (expectedRevision != revision) return applicationResult(ApplicationStatus.STALE);
        if (joinApplications.remove(applicantId) == null) {
            return applicationResult(ApplicationStatus.APPLICATION_NOT_FOUND);
        }
        // Cancelling stays possible at once (undoing a misclick); it only delays the next application.
        recordApplicationChange(applicantId, System.currentTimeMillis());
        changed();
        return applicationResult(ApplicationStatus.CANCELLED);
    }

    private void recordApplicationChange(UUID applicantId, long changeAt) {
        if (applicationChangedAt.size() >= 256) {
            applicationChangedAt.values().removeIf(at -> JoinApplicationCooldownPolicy.applyAllowed(at, changeAt));
        }
        applicationChangedAt.put(applicantId, changeAt);
    }

    public ApplicationResult decideApplication(UUID actorId, UUID applicantId, boolean approve,
                                               long expectedRevision, long openNow) {
        if (expectedRevision != revision) return applicationResult(ApplicationStatus.STALE);
        Nation actorNation = nationFor(actorId).orElse(null);
        JoinApplication application = joinApplications.get(applicantId);
        boolean canManage = actorNation != null
                && hasPermission(actorNation, actorId, S2Permission.MANAGE_MEMBERS);
        boolean applicationExists = application != null && actorNation != null
                && application.nationId.equals(actorNation.id);
        NationApplicationPolicy.Decision decision = NationApplicationPolicy.decide(
                canManage, applicationExists, nationByMember.containsKey(applicantId), approve);
        if (decision == NationApplicationPolicy.Decision.ALREADY_MEMBER && applicationExists) {
            joinApplications.remove(applicantId);
            changed();
            return applicationResult(ApplicationStatus.ALREADY_MEMBER);
        }
        if (decision != NationApplicationPolicy.Decision.ALLOW_APPROVE
                && decision != NationApplicationPolicy.Decision.ALLOW_REJECT) {
            return applicationResult(mapApplicationDecision(decision));
        }
        // The application stays open, so it can still be approved once the cooldown has run out.
        if (approve && inMembershipCooldown(applicantId, openNow)) {
            return applicationResult(ApplicationStatus.APPLICANT_COOLDOWN);
        }
        joinApplications.remove(applicantId);
        if (approve) {
            actorNation.members.put(applicantId, new Member(applicantId, application.applicantName,
                    MEMBER_ROLE, System.currentTimeMillis()));
            nationByMember.put(applicantId, actorNation.id);
            invitations.remove(applicantId);
        }
        changed();
        return applicationResult(approve ? ApplicationStatus.APPROVED : ApplicationStatus.REJECTED);
    }

    private ApplicationResult applicationResult(ApplicationStatus status) {
        return new ApplicationResult(status, revision);
    }

    private static ApplicationStatus mapApplicationDecision(NationApplicationPolicy.Decision decision) {
        return switch (decision) {
            case NO_PERMISSION -> ApplicationStatus.NO_PERMISSION;
            case ALREADY_MEMBER -> ApplicationStatus.ALREADY_MEMBER;
            case ALREADY_APPLIED -> ApplicationStatus.ALREADY_APPLIED;
            case NATION_NOT_FOUND -> ApplicationStatus.NATION_NOT_FOUND;
            case APPLICATION_NOT_FOUND -> ApplicationStatus.APPLICATION_NOT_FOUND;
            case ALLOW_APPLY -> ApplicationStatus.APPLIED;
            case ALLOW_APPROVE -> ApplicationStatus.APPROVED;
            case ALLOW_REJECT -> ApplicationStatus.REJECTED;
        };
    }

    private static String safePlayerName(String name) {
        String value = name == null ? "" : name;
        return value.length() <= 16 ? value : value.substring(0, 16);
    }

    public MembershipResult invite(UUID actorId, UUID targetId, String targetName, long expectedRevision) {
        if (expectedRevision != revision) return membershipResult(MembershipStatus.STALE);
        Nation nation = nationFor(actorId).orElse(null);
        if (nation == null || !hasPermission(nation, actorId, S2Permission.MANAGE_MEMBERS)) {
            return membershipResult(MembershipStatus.NO_PERMISSION);
        }
        if (actorId.equals(targetId) || nationByMember.containsKey(targetId)) {
            return membershipResult(MembershipStatus.TARGET_ALREADY_MEMBER);
        }
        if (invitations.containsKey(targetId)) return membershipResult(MembershipStatus.ALREADY_INVITED);
        invitations.put(targetId, new Invitation(nation.id, targetId, targetName));
        changed();
        return membershipResult(MembershipStatus.INVITED);
    }

    public MembershipResult accept(UUID playerId, UUID nationId, String playerName, long expectedRevision,
                                   long openNow) {
        if (expectedRevision != revision) return membershipResult(MembershipStatus.STALE);
        if (nationByMember.containsKey(playerId)) return membershipResult(MembershipStatus.TARGET_ALREADY_MEMBER);
        if (inMembershipCooldown(playerId, openNow)) return membershipResult(MembershipStatus.MEMBERSHIP_COOLDOWN);
        Invitation invitation = invitations.get(playerId);
        Nation nation = nations.get(nationId);
        if (invitation == null || !invitation.nationId.equals(nationId) || nation == null) {
            return membershipResult(MembershipStatus.INVITE_NOT_FOUND);
        }
        nation.members.put(playerId, new Member(
                playerId, playerName, MEMBER_ROLE, System.currentTimeMillis()));
        nationByMember.put(playerId, nationId);
        invitations.remove(playerId);
        joinApplications.remove(playerId);
        changed();
        return membershipResult(MembershipStatus.JOINED);
    }

    public MembershipResult decline(UUID playerId, UUID nationId, long expectedRevision) {
        if (expectedRevision != revision) return membershipResult(MembershipStatus.STALE);
        Invitation invitation = invitations.get(playerId);
        if (invitation == null || !invitation.nationId.equals(nationId)) {
            return membershipResult(MembershipStatus.INVITE_NOT_FOUND);
        }
        invitations.remove(playerId);
        changed();
        return membershipResult(MembershipStatus.DECLINED);
    }

    public MembershipResult leave(UUID playerId, long expectedRevision, long openNow) {
        if (expectedRevision != revision) return membershipResult(MembershipStatus.STALE);
        Nation nation = nationFor(playerId).orElse(null);
        if (nation == null) return membershipResult(MembershipStatus.NOT_MEMBER);
        if (nation.ownerId.equals(playerId)) return membershipResult(MembershipStatus.OWNER_CANNOT_LEAVE);
        nation.members.remove(playerId);
        nationByMember.remove(playerId);
        dropPersonalAllyGrants(playerId);
        startMembershipCooldown(playerId, nation.id, openNow, true);
        changed();
        return membershipResult(MembershipStatus.LEFT);
    }

    public MembershipResult kick(UUID actorId, UUID targetId, long expectedRevision, long openNow) {
        if (expectedRevision != revision) return membershipResult(MembershipStatus.STALE);
        Nation nation = nationFor(actorId).orElse(null);
        if (nation == null || !hasPermission(nation, actorId, S2Permission.MANAGE_MEMBERS)) {
            return membershipResult(MembershipStatus.NO_PERMISSION);
        }
        if (nation.ownerId.equals(targetId)) return membershipResult(MembershipStatus.OWNER_CANNOT_LEAVE);
        // Leaving has its own path and checks; kicking oneself would sidestep them.
        if (actorId.equals(targetId)) return membershipResult(MembershipStatus.NO_PERMISSION);
        if (!nation.members.containsKey(targetId)) return membershipResult(MembershipStatus.NOT_MEMBER);
        if (!RoleAuthorityPolicy.mayKick(nation.ownerId.equals(actorId), false,
                heldMask(nation, actorId), heldMask(nation, targetId))) {
            return membershipResult(MembershipStatus.TARGET_OUTRANKS);
        }
        nation.members.remove(targetId);
        nationByMember.remove(targetId);
        dropPersonalAllyGrants(targetId);
        startMembershipCooldown(targetId, nation.id, openNow, false);
        changed();
        return membershipResult(MembershipStatus.KICKED);
    }

    public RoleResult saveRole(UUID actorId, String roleId, String rawDisplayName,
                               long permissionMask, long expectedRevision) {
        if (expectedRevision != revision) return roleResult(RoleStatus.STALE);
        Nation nation = nationFor(actorId).orElse(null);
        if (nation == null || !hasPermission(nation, actorId, S2Permission.MANAGE_ROLES)) {
            return roleResult(RoleStatus.NO_PERMISSION);
        }
        RoleNamePolicy.Validation validation = RoleNamePolicy.validate(rawDisplayName);
        if (!validation.valid()) return roleResult(RoleStatus.INVALID);
        String normalizedId = roleId == null ? "" : roleId.trim();
        boolean creating = normalizedId.isEmpty();
        if (!creating && (OWNER_ROLE.equals(normalizedId) || MEMBER_ROLE.equals(normalizedId))) {
            return roleResult(RoleStatus.BUILT_IN);
        }
        if (!creating && !nation.roles.containsKey(normalizedId)) return roleResult(RoleStatus.NOT_FOUND);
        if (creating && nation.roles.size() - 2 >= MAX_CUSTOM_ROLES) return roleResult(RoleStatus.LIMIT_REACHED);
        boolean duplicate = nation.roles.values().stream()
                .filter(role -> !role.id.equals(normalizedId))
                .anyMatch(role -> role.displayName.equalsIgnoreCase(validation.name()));
        if (duplicate) return roleResult(RoleStatus.DUPLICATE);
        long allowedMask = permissionMask & ~(S2Permission.OWNER.mask());
        long previousMask = creating ? 0L : nation.roles.get(normalizedId).permissionMask;
        if (!RoleAuthorityPolicy.maySave(nation.ownerId.equals(actorId), heldMask(nation, actorId),
                previousMask, allowedMask)) {
            return roleResult(RoleStatus.NO_PERMISSION);
        }
        String savedId = creating ? "custom_" + UUID.randomUUID() : normalizedId;
        nation.roles.put(savedId, new Role(savedId, validation.name(), allowedMask));
        changed();
        return new RoleResult(creating ? RoleStatus.CREATED : RoleStatus.UPDATED, revision, savedId);
    }

    public RoleResult assignRole(UUID actorId, UUID targetId, String roleId, long expectedRevision) {
        if (expectedRevision != revision) return roleResult(RoleStatus.STALE);
        Nation nation = nationFor(actorId).orElse(null);
        if (nation == null || !hasPermission(nation, actorId, S2Permission.MANAGE_ROLES)) {
            return roleResult(RoleStatus.NO_PERMISSION);
        }
        if (nation.ownerId.equals(targetId)) return roleResult(RoleStatus.OWNER_IMMUTABLE);
        Member member = nation.members.get(targetId);
        Role role = nation.roles.get(roleId);
        if (member == null || role == null || OWNER_ROLE.equals(role.id)) return roleResult(RoleStatus.NOT_FOUND);
        Role current = nation.roles.get(member.roleId);
        if (!RoleAuthorityPolicy.mayAssign(nation.ownerId.equals(actorId), targetId.equals(actorId),
                heldMask(nation, actorId), current == null ? 0L : current.permissionMask, role.permissionMask)) {
            return roleResult(RoleStatus.NO_PERMISSION);
        }
        member.roleId = role.id;
        changed();
        return new RoleResult(RoleStatus.ASSIGNED, revision, role.id);
    }

    public RoleResult deleteRole(UUID actorId, String roleId, long expectedRevision) {
        if (expectedRevision != revision) return roleResult(RoleStatus.STALE);
        Nation nation = nationFor(actorId).orElse(null);
        if (nation == null || !hasPermission(nation, actorId, S2Permission.MANAGE_ROLES)) {
            return roleResult(RoleStatus.NO_PERMISSION);
        }
        String normalizedId = roleId == null ? "" : roleId.trim();
        if (OWNER_ROLE.equals(normalizedId) || MEMBER_ROLE.equals(normalizedId)) {
            return roleResult(RoleStatus.BUILT_IN);
        }
        if (!nation.roles.containsKey(normalizedId)) return roleResult(RoleStatus.NOT_FOUND);
        if (!RoleAuthorityPolicy.withinAuthority(nation.ownerId.equals(actorId), heldMask(nation, actorId),
                nation.roles.get(normalizedId).permissionMask)) {
            return roleResult(RoleStatus.NO_PERMISSION);
        }
        nation.members.values().stream()
                .filter(member -> normalizedId.equals(member.roleId))
                .forEach(member -> member.roleId = MEMBER_ROLE);
        nation.roles.remove(normalizedId);
        changed();
        return new RoleResult(RoleStatus.DELETED, revision, normalizedId);
    }

    public NationAdminResult updateIdentity(UUID actorId, String rawName, String rawTag,
                                            long expectedRevision) {
        if (expectedRevision != revision) return adminResult(NationAdminStatus.STALE);
        Nation nation = nationFor(actorId).orElse(null);
        if (nation == null || !nation.ownerId.equals(actorId)) return adminResult(NationAdminStatus.OWNER_ONLY);
        NationNamePolicy.Validation validation = NationNamePolicy.validate(rawName, rawTag);
        if (!validation.valid()) return adminResult(NationAdminStatus.INVALID);
        boolean duplicate = nations.values().stream().filter(other -> !other.id.equals(nation.id))
                .anyMatch(other -> NationNamePolicy.normalizedName(other.name)
                        .equals(NationNamePolicy.normalizedName(validation.name()))
                        || other.tag.equalsIgnoreCase(validation.tag()));
        if (duplicate) return adminResult(NationAdminStatus.DUPLICATE);
        if (nation.name.equals(validation.name()) && nation.tag.equals(validation.tag())) {
            return adminResult(NationAdminStatus.UNCHANGED);
        }
        nation.name = validation.name();
        nation.tag = validation.tag();
        changed();
        return adminResult(NationAdminStatus.UPDATED);
    }

    public NationAdminResult transferOwner(UUID actorId, UUID targetId, long expectedRevision,
                                           boolean siegeLocked) {
        if (expectedRevision != revision) return adminResult(NationAdminStatus.STALE);
        Nation nation = nationFor(actorId).orElse(null);
        NationLifecyclePolicy.Decision decision = NationLifecyclePolicy.transfer(
                nation != null && nation.ownerId.equals(actorId),
                nation != null && nation.members.containsKey(targetId),
                nation != null && nation.ownerId.equals(targetId), siegeLocked);
        if (decision != NationLifecyclePolicy.Decision.ALLOWED) return adminResult(map(decision));
        Member previous = nation.members.get(actorId);
        Member next = nation.members.get(targetId);
        previous.roleId = MEMBER_ROLE;
        next.roleId = OWNER_ROLE;
        nation.ownerId = targetId;
        changed();
        return adminResult(NationAdminStatus.OWNER_TRANSFERRED);
    }

    /** Whether the nation is allied with anyone; an alliance under termination notice still counts. */
    public boolean hasAlliance(UUID nationId) {
        if (nationId == null) return false;
        for (DiplomacyRecord record : diplomacy.values()) {
            if (record.allied && record.pair.contains(nationId)) return true;
        }
        return false;
    }

    /**
     * @param hasPeaceTruce whether Siege holds an active peace truce between this nation and any other
     */
    public NationAdminResult validateDisband(UUID actorId, long expectedRevision,
                                             boolean siegeLocked, boolean hasPrisoners, boolean hasPeaceTruce) {
        if (expectedRevision != revision) return adminResult(NationAdminStatus.STALE);
        Nation nation = nationFor(actorId).orElse(null);
        NationLifecyclePolicy.Decision decision = NationLifecyclePolicy.disband(
                nation != null && nation.ownerId.equals(actorId), siegeLocked, hasPrisoners,
                nation != null && hasAlliance(nation.id), hasPeaceTruce);
        return adminResult(decision == NationLifecyclePolicy.Decision.ALLOWED
                ? NationAdminStatus.ALLOWED : map(decision));
    }

    public NationAdminResult disband(UUID actorId, long expectedRevision, long openNow) {
        if (expectedRevision != revision) return adminResult(NationAdminStatus.STALE);
        Nation nation = nationFor(actorId).orElse(null);
        if (nation == null || !nation.ownerId.equals(actorId)) return adminResult(NationAdminStatus.OWNER_ONLY);
        UUID nationId = nation.id;
        nation.members.keySet().forEach(nationByMember::remove);
        UUID ownerId = nation.ownerId;
        // Only the owner chose this; see MembershipCooldownPolicy.onDisband.
        nation.members.keySet().forEach(memberId -> MembershipCooldownPolicy
                .onDisband(memberId.equals(ownerId), nationId, openNow)
                .ifPresent(entry -> membershipCooldowns.put(memberId, MembershipCooldownPolicy.record(
                        membershipCooldowns.get(memberId), entry, openNow))));
        invitations.values().removeIf(invitation -> invitation.nationId.equals(nationId));
        joinApplications.values().removeIf(application -> application.nationId.equals(nationId));
        diplomacy.entrySet().removeIf(entry -> entry.getKey().first.equals(nationId)
                || entry.getKey().second.equals(nationId));
        nations.remove(nationId);
        changed();
        return adminResult(NationAdminStatus.DISBANDED);
    }

    private NationAdminResult adminResult(NationAdminStatus status) {
        return new NationAdminResult(status, revision);
    }

    private static NationAdminStatus map(NationLifecyclePolicy.Decision decision) {
        return switch (decision) {
            case OWNER_ONLY -> NationAdminStatus.OWNER_ONLY;
            case TARGET_NOT_MEMBER -> NationAdminStatus.TARGET_NOT_MEMBER;
            case TARGET_IS_OWNER -> NationAdminStatus.TARGET_IS_OWNER;
            case SIEGE_LOCKED -> NationAdminStatus.SIEGE_LOCKED;
            case PRISONERS_EXIST -> NationAdminStatus.PRISONERS_EXIST;
            case ALLIANCE_ACTIVE -> NationAdminStatus.ALLIANCE_ACTIVE;
            case PEACE_TRUCE_ACTIVE -> NationAdminStatus.PEACE_TRUCE_ACTIVE;
            case ALLOWED -> NationAdminStatus.ALLOWED;
        };
    }

    private RoleResult roleResult(RoleStatus status) {
        return new RoleResult(status, revision, "");
    }

    private static long heldMask(Nation nation, UUID playerId) {
        Member member = nation.members.get(playerId);
        Role role = member == null ? null : nation.roles.get(member.roleId);
        return role == null ? 0L : role.permissionMask;
    }

    private static boolean hasPermission(Nation nation, UUID playerId, S2Permission permission) {
        if (nation.ownerId.equals(playerId)) return true;
        Member member = nation.members.get(playerId);
        Role role = member == null ? null : nation.roles.get(member.roleId);
        return role != null && (role.permissionMask & permission.mask()) != 0L;
    }

    private MembershipResult membershipResult(MembershipStatus status) {
        return new MembershipResult(status, revision);
    }

    private void changed() {
        revision++;
        setDirty();
    }

    public CreateResult create(UUID ownerId, String ownerName, String rawName, String rawTag,
                               long expectedRevision, long openNow) {
        Status status = validateCreate(ownerId, rawName, rawTag, expectedRevision, openNow);
        if (status != Status.CREATED) return new CreateResult(status, null, revision);
        NationNamePolicy.Validation validation = NationNamePolicy.validate(rawName, rawTag);

        UUID nationId = UUID.randomUUID();
        Nation nation = new Nation(nationId, validation.name(), validation.tag(), ownerId);
        nation.members.put(ownerId, new Member(
                ownerId, ownerName, OWNER_ROLE, System.currentTimeMillis()));
        nations.put(nationId, nation);
        nationByMember.put(ownerId, nationId);
        joinApplications.remove(ownerId);
        changed();
        return new CreateResult(Status.CREATED, nation, revision);
    }

    /** Performs every nation-side creation check without mutating persistent state. */
    public Status validateCreate(UUID ownerId, String rawName, String rawTag, long expectedRevision,
                                 long openNow) {
        if (expectedRevision != revision) return Status.STALE;
        if (nationByMember.containsKey(ownerId)) return Status.ALREADY_MEMBER;
        switch (MembershipCooldownPolicy.founding(membershipCooldowns.get(ownerId), openNow, nations::containsKey)) {
            case MEMBERSHIP_COOLDOWN -> { return Status.MEMBERSHIP_COOLDOWN; }
            case FORMER_NATION_BOUND -> { return Status.FORMER_NATION_BOUND; }
            case ALLOWED -> { }
        }
        NationNamePolicy.Validation validation = NationNamePolicy.validate(rawName, rawTag);
        if (!validation.valid()) return Status.INVALID;
        boolean duplicate = nations.values().stream().anyMatch(nation ->
                NationNamePolicy.normalizedName(nation.name).equals(NationNamePolicy.normalizedName(validation.name()))
                        || nation.tag.equalsIgnoreCase(validation.tag()));
        return duplicate ? Status.DUPLICATE : Status.CREATED;
    }

    /** Internal compensation used when capital-core creation fails in the same server task. */
    boolean rollbackFreshCreation(UUID nationId, UUID ownerId) {
        Nation nation = nations.get(nationId);
        if (nation == null || !nation.ownerId.equals(ownerId) || nation.members.size() != 1
                || !nation.members.containsKey(ownerId)) return false;
        nations.remove(nationId);
        nationByMember.remove(ownerId, nationId);
        revision = Math.max(0L, revision - 1L);
        setDirty();
        return true;
    }

    public void updateKnownName(UUID playerId, String name) {
        Nation nation = nationFor(playerId).orElse(null);
        if (nation == null) return;
        Member member = nation.members.get(playerId);
        if (member != null && !member.lastKnownName.equals(name)) {
            member.lastKnownName = name;
            setDirty();
        }
    }

    public void updatePresence(UUID playerId, String name, long lastSeenAt) {
        Nation nation = nationFor(playerId).orElse(null);
        if (nation == null) return;
        Member member = nation.members.get(playerId);
        if (member == null) return;
        boolean updated = false;
        String safeName = name == null ? "" : name;
        if (!safeName.isBlank() && !member.lastKnownName.equals(safeName)) {
            member.lastKnownName = safeName;
            updated = true;
        }
        long safeLastSeen = Math.max(0L, lastSeenAt);
        if (safeLastSeen > member.lastSeenAt) {
            member.lastSeenAt = safeLastSeen;
            updated = true;
        }
        if (updated) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("Revision", revision);
        ListTag nationList = new ListTag();
        for (Nation nation : nations.values()) {
            CompoundTag nationTag = new CompoundTag();
            nationTag.putString("Id", nation.id.toString());
            nationTag.putString("Name", nation.name);
            nationTag.putString("Tag", nation.tag);
            nationTag.putString("Owner", nation.ownerId.toString());
            ListTag roleList = new ListTag();
            for (Role role : nation.roles.values()) {
                CompoundTag roleTag = new CompoundTag();
                roleTag.putString("Id", role.id);
                roleTag.putString("Name", role.displayName);
                roleTag.putLong("Permissions", role.permissionMask);
                roleList.add(roleTag);
            }
            nationTag.put("Roles", roleList);
            ListTag memberList = new ListTag();
            for (Member member : nation.members.values()) {
                CompoundTag memberTag = new CompoundTag();
                memberTag.putString("Id", member.id.toString());
                memberTag.putString("Name", member.lastKnownName);
                memberTag.putString("Role", member.roleId);
                memberTag.putLong("LastSeenAt", member.lastSeenAt);
                memberList.add(memberTag);
            }
            nationTag.put("Members", memberList);
            nationList.add(nationTag);
        }
        tag.put("Nations", nationList);
        ListTag inviteList = new ListTag();
        for (Invitation invitation : invitations.values()) {
            CompoundTag inviteTag = new CompoundTag();
            inviteTag.putString("Nation", invitation.nationId.toString());
            inviteTag.putString("Target", invitation.targetId.toString());
            inviteTag.putString("TargetName", invitation.targetName);
            inviteList.add(inviteTag);
        }
        tag.put("Invitations", inviteList);
        ListTag applicationList = new ListTag();
        for (JoinApplication application : joinApplications.values()) {
            CompoundTag applicationTag = new CompoundTag();
            applicationTag.putUUID("Nation", application.nationId);
            applicationTag.putUUID("Applicant", application.applicantId);
            applicationTag.putString("ApplicantName", application.applicantName);
            applicationTag.putLong("RequestedAt", application.requestedAt);
            applicationList.add(applicationTag);
        }
        tag.put("JoinApplications", applicationList);
        ListTag diplomacyList = new ListTag();
        for (DiplomacyRecord record : diplomacy.values()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("First", record.pair.first);
            value.putUUID("Second", record.pair.second);
            value.putBoolean("Allied", record.allied);
            if (record.requestFrom != null) value.putUUID("RequestFrom", record.requestFrom);
            value.putBoolean("HostileFirstToSecond", record.hostileFirstToSecond);
            value.putBoolean("HostileSecondToFirst", record.hostileSecondToFirst);
            if (record.allied) {
                if (AllianceTerminationPolicy.pending(record.allianceEndsAt)) {
                    value.putLong("AllianceEndsAt", record.allianceEndsAt);
                    if (record.allianceEndFrom != null) value.putUUID("AllianceEndFrom", record.allianceEndFrom);
                }
                value.put("FirstGrants", record.firstGrants.save());
                value.put("SecondGrants", record.secondGrants.save());
            }
            diplomacyList.add(value);
        }
        tag.put("Diplomacy", diplomacyList);
        ListTag cooldownList = new ListTag();
        // One compound per lost membership, oldest first; a player may appear more than once.
        membershipCooldowns.forEach((playerId, cooldowns) -> {
            for (MembershipCooldownPolicy.Entry cooldown : cooldowns) {
                CompoundTag value = new CompoundTag();
                value.putUUID("Player", playerId);
                if (cooldown.formerNationId() != null) value.putUUID("FormerNation", cooldown.formerNationId());
                value.putLong("EndsAt", cooldown.endsAt());
                value.putBoolean("JoinLocked", cooldown.joinLocked());
                cooldownList.add(value);
            }
        });
        tag.put("MembershipCooldowns", cooldownList);
        return tag;
    }

    public static NationSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        NationSavedData data = new NationSavedData();
        data.revision = Math.max(0L, tag.getLong("Revision"));
        ListTag nationList = tag.getList("Nations", Tag.TAG_COMPOUND);
        for (int index = 0; index < nationList.size(); index++) {
            CompoundTag nationTag = nationList.getCompound(index);
            try {
                UUID id = UUID.fromString(nationTag.getString("Id"));
                UUID owner = UUID.fromString(nationTag.getString("Owner"));
                Nation nation = new Nation(id, nationTag.getString("Name"), nationTag.getString("Tag"), owner);
                ListTag roleList = nationTag.getList("Roles", Tag.TAG_COMPOUND);
                for (int roleIndex = 0; roleIndex < roleList.size(); roleIndex++) {
                    CompoundTag roleTag = roleList.getCompound(roleIndex);
                    String roleId = roleTag.getString("Id");
                    RoleNamePolicy.Validation validation = RoleNamePolicy.validate(roleTag.getString("Name"));
                    if (!validation.valid() || roleId.isBlank() || OWNER_ROLE.equals(roleId) || MEMBER_ROLE.equals(roleId)) {
                        continue;
                    }
                    long mask = roleTag.getLong("Permissions")
                            & ~(S2Permission.OWNER.mask());
                    nation.roles.put(roleId, new Role(roleId, validation.name(), mask));
                }
                ListTag memberList = nationTag.getList("Members", Tag.TAG_COMPOUND);
                for (int memberIndex = 0; memberIndex < memberList.size(); memberIndex++) {
                    CompoundTag memberTag = memberList.getCompound(memberIndex);
                    UUID memberId = UUID.fromString(memberTag.getString("Id"));
                    String loadedRole = memberTag.getString("Role");
                    if (!nation.roles.containsKey(loadedRole)) loadedRole = MEMBER_ROLE;
                    Member member = new Member(memberId, memberTag.getString("Name"), loadedRole,
                            memberTag.getLong("LastSeenAt"));
                    nation.members.put(memberId, member);
                }
                if (!nation.members.containsKey(owner)) continue;
                data.nations.put(id, nation);
                for (UUID memberId : nation.members.keySet()) data.nationByMember.put(memberId, id);
            } catch (IllegalArgumentException ignored) {
                // Ignore a malformed record without preventing other nations from loading.
            }
        }
        ListTag inviteList = tag.getList("Invitations", Tag.TAG_COMPOUND);
        for (int index = 0; index < inviteList.size(); index++) {
            CompoundTag inviteTag = inviteList.getCompound(index);
            try {
                UUID nationId = UUID.fromString(inviteTag.getString("Nation"));
                UUID targetId = UUID.fromString(inviteTag.getString("Target"));
                if (data.nations.containsKey(nationId) && !data.nationByMember.containsKey(targetId)) {
                    data.invitations.put(targetId, new Invitation(
                            nationId, targetId, inviteTag.getString("TargetName")));
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        ListTag applicationList = tag.getList("JoinApplications", Tag.TAG_COMPOUND);
        for (int index = 0; index < applicationList.size(); index++) {
            CompoundTag applicationTag = applicationList.getCompound(index);
            if (!applicationTag.hasUUID("Nation") || !applicationTag.hasUUID("Applicant")) continue;
            UUID nationId = applicationTag.getUUID("Nation");
            UUID applicantId = applicationTag.getUUID("Applicant");
            if (data.nations.containsKey(nationId) && !data.nationByMember.containsKey(applicantId)) {
                data.joinApplications.put(applicantId, new JoinApplication(nationId, applicantId,
                        safePlayerName(applicationTag.getString("ApplicantName")),
                        Math.max(0L, applicationTag.getLong("RequestedAt"))));
            }
        }
        ListTag diplomacyList = tag.getList("Diplomacy", Tag.TAG_COMPOUND);
        for (int index = 0; index < diplomacyList.size(); index++) {
            CompoundTag value = diplomacyList.getCompound(index);
            if (!value.hasUUID("First") || !value.hasUUID("Second")) continue;
            UUID first = value.getUUID("First");
            UUID second = value.getUUID("Second");
            if (first.equals(second) || !data.nations.containsKey(first) || !data.nations.containsKey(second)) continue;
            NationPair pair = NationPair.of(first, second);
            DiplomacyRecord record = new DiplomacyRecord(pair);
            record.allied = value.getBoolean("Allied");
            if (value.hasUUID("RequestFrom")) {
                UUID requester = value.getUUID("RequestFrom");
                if (requester.equals(pair.first) || requester.equals(pair.second)) record.requestFrom = requester;
            }
            record.hostileFirstToSecond = value.getBoolean("HostileFirstToSecond");
            record.hostileSecondToFirst = value.getBoolean("HostileSecondToFirst");
            // Saves from before alliance notice and ally grants carry neither: every grant starts off.
            if (record.allied) {
                long endsAt = Math.max(0L, value.getLong("AllianceEndsAt"));
                UUID endFrom = value.hasUUID("AllianceEndFrom") ? value.getUUID("AllianceEndFrom") : null;
                if (endsAt > 0L && endFrom != null && pair.contains(endFrom)) {
                    record.allianceEndsAt = endsAt;
                    record.allianceEndFrom = endFrom;
                }
                record.firstGrants.load(value.getCompound("FirstGrants"), data.nations.get(pair.second));
                record.secondGrants.load(value.getCompound("SecondGrants"), data.nations.get(pair.first));
            }
            if (!record.isEmpty()) data.diplomacy.put(pair, record);
        }
        ListTag cooldownList = tag.getList("MembershipCooldowns", Tag.TAG_COMPOUND);
        for (int index = 0; index < cooldownList.size(); index++) {
            CompoundTag value = cooldownList.getCompound(index);
            if (!value.hasUUID("Player")) continue;
            long endsAt = Math.max(0L, value.getLong("EndsAt"));
            if (endsAt <= 0L) continue;
            // Appended in saved order, which is the order the memberships were lost.
            data.membershipCooldowns.computeIfAbsent(value.getUUID("Player"), ignored -> new java.util.ArrayList<>())
                    .add(new MembershipCooldownPolicy.Entry(
                            value.hasUUID("FormerNation") ? value.getUUID("FormerNation") : null, endsAt,
                            !value.contains("JoinLocked") || value.getBoolean("JoinLocked")));
        }
        return data;
    }

    public static NationSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(NationSavedData::new, NationSavedData::load, null),
                "moveearth_nations");
    }

    public enum Status { CREATED, INVALID, DUPLICATE, ALREADY_MEMBER, STALE, MEMBERSHIP_COOLDOWN, FORMER_NATION_BOUND }

    public enum MembershipStatus {
        INVITED, JOINED, DECLINED, LEFT, KICKED,
        STALE, NO_PERMISSION, TARGET_ALREADY_MEMBER, ALREADY_INVITED,
        INVITE_NOT_FOUND, NOT_MEMBER, OWNER_CANNOT_LEAVE, TARGET_OFFLINE, SIEGE_LOCKED,
        MEMBERSHIP_COOLDOWN, TARGET_OUTRANKS
    }

    public enum ApplicationStatus {
        APPLIED, CANCELLED, APPROVED, REJECTED, STALE, NO_PERMISSION,
        ALREADY_MEMBER, ALREADY_APPLIED, NATION_NOT_FOUND, APPLICATION_NOT_FOUND,
        MEMBERSHIP_COOLDOWN, APPLICANT_COOLDOWN, APPLICATION_COOLDOWN
    }

    public enum RoleStatus {
        CREATED, UPDATED, ASSIGNED, DELETED, INVALID, DUPLICATE, LIMIT_REACHED,
        BUILT_IN, NOT_FOUND, OWNER_IMMUTABLE, STALE, NO_PERMISSION
    }

    public enum NationAdminStatus {
        ALLOWED, UPDATED, OWNER_TRANSFERRED, DISBANDED, UNCHANGED, INVALID, DUPLICATE,
        OWNER_ONLY, TARGET_NOT_MEMBER, TARGET_IS_OWNER, SIEGE_LOCKED, PRISONERS_EXIST, STALE,
        ALLIANCE_ACTIVE, PEACE_TRUCE_ACTIVE
    }

    public record NationAdminResult(NationAdminStatus status, long revision) {
        public boolean success() {
            return status == NationAdminStatus.UPDATED || status == NationAdminStatus.OWNER_TRANSFERRED
                    || status == NationAdminStatus.DISBANDED;
        }
    }

    public enum DiplomacyAction {
        REQUEST_ALLIANCE, ACCEPT_ALLIANCE, DECLINE_ALLIANCE, END_ALLIANCE,
        DECLARE_HOSTILE, SET_NEUTRAL, CANCEL_ALLIANCE_END, UNKNOWN
    }

    public enum DiplomacyRelation {
        NEUTRAL, OUTGOING_REQUEST, INCOMING_REQUEST, ALLIED, HOSTILE
    }

    public enum DiplomacyStatus {
        REQUESTED, ALLIED, DECLINED, ALLIANCE_ENDED, HOSTILE_DECLARED, NEUTRAL,
        STALE, NO_PERMISSION, NOT_FOUND, ALREADY_ALLIED, HOSTILE_CONFLICT,
        REQUEST_EXISTS, REQUEST_NOT_FOUND, NOT_ALLIED, NOT_HOSTILE, INVALID,
        ALLIANCE_END_DECLARED, ALLIANCE_END_CANCELLED, ALLIANCE_END_PENDING, ALLIANCE_END_NOT_PENDING,
        ALLIANCE_END_NOT_DECLARER, ALLIANCE_ACTIVE, PERMISSION_UPDATED, PERMISSION_UNCHANGED,
        TARGET_NOT_MEMBER
    }

    public record CreateResult(Status status, Nation nation, long revision) {
    }

    public record MembershipResult(MembershipStatus status, long revision) {
        public boolean success() {
            return status == MembershipStatus.INVITED || status == MembershipStatus.JOINED
                    || status == MembershipStatus.DECLINED || status == MembershipStatus.LEFT
                    || status == MembershipStatus.KICKED;
        }
    }

    public record Invitation(UUID nationId, UUID targetId, String targetName) {
    }

    public record JoinApplication(UUID nationId, UUID applicantId, String applicantName, long requestedAt) {
    }

    public record ApplicationResult(ApplicationStatus status, long revision) {
        public boolean success() {
            return status == ApplicationStatus.APPLIED || status == ApplicationStatus.CANCELLED
                    || status == ApplicationStatus.APPROVED || status == ApplicationStatus.REJECTED;
        }
    }

    public record RoleResult(RoleStatus status, long revision, String roleId) {
        public boolean success() {
            return status == RoleStatus.CREATED || status == RoleStatus.UPDATED
                    || status == RoleStatus.ASSIGNED || status == RoleStatus.DELETED;
        }
    }

    public record DiplomacyResult(DiplomacyStatus status, long revision) {
        public boolean success() {
            return status == DiplomacyStatus.REQUESTED || status == DiplomacyStatus.ALLIED
                    || status == DiplomacyStatus.DECLINED || status == DiplomacyStatus.ALLIANCE_ENDED
                    || status == DiplomacyStatus.HOSTILE_DECLARED || status == DiplomacyStatus.NEUTRAL
                    || status == DiplomacyStatus.ALLIANCE_END_DECLARED
                    || status == DiplomacyStatus.ALLIANCE_END_CANCELLED
                    || status == DiplomacyStatus.PERMISSION_UPDATED;
        }
    }

    /** Notice given by {@code declaringNation}; the alliance lasts until open time reaches {@code endsAt}. */
    public record AllianceEnd(UUID declaringNation, long endsAt) { }

    public record EndedAlliance(UUID declaringNation, UUID otherNation) { }

    public static final class Nation {
        private final UUID id;
        private String name;
        private String tag;
        private UUID ownerId;
        private final Map<UUID, Member> members = new LinkedHashMap<>();
        private final Map<String, Role> roles = new LinkedHashMap<>();
        private final Map<UUID, Member> memberView = Collections.unmodifiableMap(members);
        private final Map<String, Role> roleView = Collections.unmodifiableMap(roles);

        private Nation(UUID id, String name, String tag, UUID ownerId) {
            this.id = id;
            this.name = name;
            this.tag = tag;
            this.ownerId = ownerId;
            roles.put(OWNER_ROLE, new Role(OWNER_ROLE, "Owner",
                    S2Permission.toMask(java.util.EnumSet.allOf(S2Permission.class))));
            roles.put(MEMBER_ROLE, new Role(MEMBER_ROLE, "Member", S2Permission.BASTION_ACCESS.mask()));
        }

        public UUID id() { return id; }
        public String name() { return name; }
        public String tag() { return tag; }
        public UUID ownerId() { return ownerId; }
        public Map<UUID, Member> members() { return memberView; }
        public Map<String, Role> roles() { return roleView; }
    }

    public static final class Member {
        private final UUID id;
        private String lastKnownName;
        private String roleId;
        private long lastSeenAt;

        private Member(UUID id, String lastKnownName, String roleId, long lastSeenAt) {
            this.id = id;
            this.lastKnownName = lastKnownName == null ? "" : lastKnownName;
            this.roleId = roleId == null || roleId.isBlank() ? MEMBER_ROLE : roleId;
            this.lastSeenAt = Math.max(0L, lastSeenAt);
        }

        public UUID id() { return id; }
        public String lastKnownName() { return lastKnownName; }
        public String roleId() { return roleId; }
        public long lastSeenAt() { return lastSeenAt; }
    }

    public static final class Role {
        private final String id;
        private final String displayName;
        private final long permissionMask;

        private Role(String id, String displayName, long permissionMask) {
            this.id = id;
            this.displayName = displayName;
            this.permissionMask = permissionMask;
        }

        public String id() { return id; }
        public String displayName() { return displayName; }
        public long permissionMask() { return permissionMask; }
    }

    private record NationPair(UUID first, UUID second) {
        private static NationPair of(UUID first, UUID second) {
            return first.compareTo(second) <= 0 ? new NationPair(first, second) : new NationPair(second, first);
        }

        private boolean contains(UUID nation) {
            return first.equals(nation) || second.equals(nation);
        }

        private UUID other(UUID nation) {
            return first.equals(nation) ? second : first;
        }
    }

    /** Grants one host nation gives the members of the other nation in a pair. */
    private static final class AllyGrants {
        private int nationMask;
        private final Map<UUID, Integer> players = new LinkedHashMap<>();

        private void clear() {
            nationMask = 0;
            players.clear();
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Nation", nationMask);
            ListTag list = new ListTag();
            players.forEach((playerId, mask) -> {
                CompoundTag value = new CompoundTag();
                value.putUUID("Player", playerId);
                value.putInt("Mask", mask);
                list.add(value);
            });
            tag.put("Players", list);
            return tag;
        }

        /** Personal grants are only kept for players who are still members of the ally. */
        private void load(CompoundTag tag, Nation ally) {
            clear();
            nationMask = AllyPermissionPolicy.sanitize(tag.getInt("Nation"));
            ListTag list = tag.getList("Players", Tag.TAG_COMPOUND);
            for (int index = 0; index < list.size(); index++) {
                CompoundTag value = list.getCompound(index);
                if (!value.hasUUID("Player")) continue;
                UUID playerId = value.getUUID("Player");
                int mask = AllyPermissionPolicy.sanitize(value.getInt("Mask"));
                if (mask != 0 && ally != null && ally.members.containsKey(playerId)) players.put(playerId, mask);
            }
        }
    }

    private static final class DiplomacyRecord {
        private final NationPair pair;
        private UUID requestFrom;
        private boolean allied;
        private boolean hostileFirstToSecond;
        private boolean hostileSecondToFirst;
        /** Open-time tick at which a declared termination takes effect; 0 when none is pending. */
        private long allianceEndsAt;
        private UUID allianceEndFrom;
        /** What the first nation grants the second nation's members in the first nation's territory. */
        private final AllyGrants firstGrants = new AllyGrants();
        /** What the second nation grants the first nation's members in the second nation's territory. */
        private final AllyGrants secondGrants = new AllyGrants();

        private DiplomacyRecord(NationPair pair) {
            this.pair = pair;
        }

        private AllyGrants grantsFrom(UUID hostNation) {
            return pair.first.equals(hostNation) ? firstGrants : secondGrants;
        }

        private void clearAllianceState() {
            allianceEndsAt = 0L;
            allianceEndFrom = null;
            firstGrants.clear();
            secondGrants.clear();
        }

        private boolean isHostile(UUID viewer, UUID other) {
            return isHostileFrom(viewer) || isHostileFrom(other);
        }

        private boolean isHostileFrom(UUID nation) {
            return pair.first.equals(nation) ? hostileFirstToSecond : hostileSecondToFirst;
        }

        private void setHostile(UUID from, UUID to, boolean hostile) {
            if (pair.first.equals(from) && pair.second.equals(to)) hostileFirstToSecond = hostile;
            else if (pair.second.equals(from) && pair.first.equals(to)) hostileSecondToFirst = hostile;
        }

        private boolean anyHostile() {
            return hostileFirstToSecond || hostileSecondToFirst;
        }

        private boolean isEmpty() {
            return !allied && requestFrom == null && !anyHostile();
        }
    }
}
