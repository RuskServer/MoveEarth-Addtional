package com.ruskserver.moveearth_addtional.s2.nation;

import com.ruskserver.moveearth_addtional.s2.S2Permission;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class NationSavedData extends SavedData {
    public static final String OWNER_ROLE = "owner";
    public static final String MEMBER_ROLE = "member";
    public static final int MAX_CUSTOM_ROLES = 16;

    private final Map<UUID, Nation> nations = new LinkedHashMap<>();
    private final Map<UUID, UUID> nationByMember = new LinkedHashMap<>();
    private final Map<UUID, Invitation> invitations = new LinkedHashMap<>();
    private final Map<NationPair, DiplomacyRecord> diplomacy = new LinkedHashMap<>();
    private long revision;

    public Optional<Nation> nationFor(UUID playerId) {
        UUID nationId = nationByMember.get(playerId);
        return Optional.ofNullable(nationId == null ? null : nations.get(nationId));
    }

    public Optional<Nation> nation(UUID nationId) {
        return Optional.ofNullable(nations.get(nationId));
    }

    public Map<UUID, Nation> nations() {
        return Map.copyOf(nations);
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
        DiplomacyRecord record = diplomacy.get(NationPair.of(viewerNation, otherNation));
        if (record == null) return DiplomacyRelation.NEUTRAL;
        if (record.allied) return DiplomacyRelation.ALLIED;
        if (record.isHostile(viewerNation, otherNation)) return DiplomacyRelation.HOSTILE;
        if (viewerNation.equals(record.requestFrom)) return DiplomacyRelation.OUTGOING_REQUEST;
        if (otherNation.equals(record.requestFrom)) return DiplomacyRelation.INCOMING_REQUEST;
        return DiplomacyRelation.NEUTRAL;
    }

    public boolean isHostileFrom(UUID viewerNation, UUID otherNation) {
        DiplomacyRecord record = diplomacy.get(NationPair.of(viewerNation, otherNation));
        return record != null && record.isHostileFrom(viewerNation);
    }

    public DiplomacyResult changeDiplomacy(UUID actorId, UUID targetNationId,
                                             DiplomacyAction action, long expectedRevision) {
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
                if (!record.allied) return diplomacyResult(DiplomacyStatus.NOT_ALLIED);
                record.allied = false;
                status = DiplomacyStatus.ALLIANCE_ENDED;
            }
            case DECLARE_HOSTILE -> {
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

    public long revision() {
        return revision;
    }

    public Optional<Invitation> invitationFor(UUID playerId) {
        return Optional.ofNullable(invitations.get(playerId));
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

    public MembershipResult accept(UUID playerId, UUID nationId, String playerName, long expectedRevision) {
        if (expectedRevision != revision) return membershipResult(MembershipStatus.STALE);
        if (nationByMember.containsKey(playerId)) return membershipResult(MembershipStatus.TARGET_ALREADY_MEMBER);
        Invitation invitation = invitations.get(playerId);
        Nation nation = nations.get(nationId);
        if (invitation == null || !invitation.nationId.equals(nationId) || nation == null) {
            return membershipResult(MembershipStatus.INVITE_NOT_FOUND);
        }
        nation.members.put(playerId, new Member(
                playerId, playerName, MEMBER_ROLE, System.currentTimeMillis()));
        nationByMember.put(playerId, nationId);
        invitations.remove(playerId);
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

    public MembershipResult leave(UUID playerId, long expectedRevision) {
        if (expectedRevision != revision) return membershipResult(MembershipStatus.STALE);
        Nation nation = nationFor(playerId).orElse(null);
        if (nation == null) return membershipResult(MembershipStatus.NOT_MEMBER);
        if (nation.ownerId.equals(playerId)) return membershipResult(MembershipStatus.OWNER_CANNOT_LEAVE);
        nation.members.remove(playerId);
        nationByMember.remove(playerId);
        changed();
        return membershipResult(MembershipStatus.LEFT);
    }

    public MembershipResult kick(UUID actorId, UUID targetId, long expectedRevision) {
        if (expectedRevision != revision) return membershipResult(MembershipStatus.STALE);
        Nation nation = nationFor(actorId).orElse(null);
        if (nation == null || !hasPermission(nation, actorId, S2Permission.MANAGE_MEMBERS)) {
            return membershipResult(MembershipStatus.NO_PERMISSION);
        }
        if (nation.ownerId.equals(targetId)) return membershipResult(MembershipStatus.OWNER_CANNOT_LEAVE);
        if (!nation.members.containsKey(targetId)) return membershipResult(MembershipStatus.NOT_MEMBER);
        nation.members.remove(targetId);
        nationByMember.remove(targetId);
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
        nation.members.values().stream()
                .filter(member -> normalizedId.equals(member.roleId))
                .forEach(member -> member.roleId = MEMBER_ROLE);
        nation.roles.remove(normalizedId);
        changed();
        return new RoleResult(RoleStatus.DELETED, revision, normalizedId);
    }

    private RoleResult roleResult(RoleStatus status) {
        return new RoleResult(status, revision, "");
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
                               long expectedRevision) {
        if (nationByMember.containsKey(ownerId)) return new CreateResult(Status.ALREADY_MEMBER, null, revision);
        if (expectedRevision != revision) return new CreateResult(Status.STALE, null, revision);
        NationNamePolicy.Validation validation = NationNamePolicy.validate(rawName, rawTag);
        if (!validation.valid()) return new CreateResult(Status.INVALID, null, revision);
        boolean duplicate = nations.values().stream().anyMatch(nation ->
                NationNamePolicy.normalizedName(nation.name).equals(NationNamePolicy.normalizedName(validation.name()))
                        || nation.tag.equalsIgnoreCase(validation.tag()));
        if (duplicate) return new CreateResult(Status.DUPLICATE, null, revision);

        UUID nationId = UUID.randomUUID();
        Nation nation = new Nation(nationId, validation.name(), validation.tag(), ownerId);
        nation.members.put(ownerId, new Member(
                ownerId, ownerName, OWNER_ROLE, System.currentTimeMillis()));
        nations.put(nationId, nation);
        nationByMember.put(ownerId, nationId);
        changed();
        return new CreateResult(Status.CREATED, nation, revision);
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
        ListTag diplomacyList = new ListTag();
        for (DiplomacyRecord record : diplomacy.values()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("First", record.pair.first);
            value.putUUID("Second", record.pair.second);
            value.putBoolean("Allied", record.allied);
            if (record.requestFrom != null) value.putUUID("RequestFrom", record.requestFrom);
            value.putBoolean("HostileFirstToSecond", record.hostileFirstToSecond);
            value.putBoolean("HostileSecondToFirst", record.hostileSecondToFirst);
            diplomacyList.add(value);
        }
        tag.put("Diplomacy", diplomacyList);
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
            if (!record.isEmpty()) data.diplomacy.put(pair, record);
        }
        return data;
    }

    public static NationSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(NationSavedData::new, NationSavedData::load, null),
                "moveearth_nations");
    }

    public enum Status { CREATED, INVALID, DUPLICATE, ALREADY_MEMBER, STALE }

    public enum MembershipStatus {
        INVITED, JOINED, DECLINED, LEFT, KICKED,
        STALE, NO_PERMISSION, TARGET_ALREADY_MEMBER, ALREADY_INVITED,
        INVITE_NOT_FOUND, NOT_MEMBER, OWNER_CANNOT_LEAVE, TARGET_OFFLINE
    }

    public enum RoleStatus {
        CREATED, UPDATED, ASSIGNED, DELETED, INVALID, DUPLICATE, LIMIT_REACHED,
        BUILT_IN, NOT_FOUND, OWNER_IMMUTABLE, STALE, NO_PERMISSION
    }

    public enum DiplomacyAction {
        REQUEST_ALLIANCE, ACCEPT_ALLIANCE, DECLINE_ALLIANCE, END_ALLIANCE,
        DECLARE_HOSTILE, SET_NEUTRAL, UNKNOWN
    }

    public enum DiplomacyRelation {
        NEUTRAL, OUTGOING_REQUEST, INCOMING_REQUEST, ALLIED, HOSTILE
    }

    public enum DiplomacyStatus {
        REQUESTED, ALLIED, DECLINED, ALLIANCE_ENDED, HOSTILE_DECLARED, NEUTRAL,
        STALE, NO_PERMISSION, NOT_FOUND, ALREADY_ALLIED, HOSTILE_CONFLICT,
        REQUEST_EXISTS, REQUEST_NOT_FOUND, NOT_ALLIED, NOT_HOSTILE, INVALID
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
                    || status == DiplomacyStatus.HOSTILE_DECLARED || status == DiplomacyStatus.NEUTRAL;
        }
    }

    public static final class Nation {
        private final UUID id;
        private final String name;
        private final String tag;
        private final UUID ownerId;
        private final Map<UUID, Member> members = new LinkedHashMap<>();
        private final Map<String, Role> roles = new LinkedHashMap<>();

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
        public Map<UUID, Member> members() { return Map.copyOf(members); }
        public Map<String, Role> roles() { return Map.copyOf(roles); }
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
    }

    private static final class DiplomacyRecord {
        private final NationPair pair;
        private UUID requestFrom;
        private boolean allied;
        private boolean hostileFirstToSecond;
        private boolean hostileSecondToFirst;

        private DiplomacyRecord(NationPair pair) {
            this.pair = pair;
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
