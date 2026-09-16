package com.ruskserver.moveearth_addtional.s2.notification;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/** Persistent nation notification preferences and the delivery outbox consumed by an external bridge. */
public final class NationNotificationSavedData extends SavedData {
    public static final int MAX_OUTBOX = NotificationDeliveryPolicy.MAX_OUTBOX;
    public static final int MAX_DELIVERY_ATTEMPTS = NotificationDeliveryPolicy.MAX_ATTEMPTS;
    private static final Settings DEFAULT_SETTINGS = new Settings(true, false, true, false);

    private final Map<UUID, Settings> settings = new LinkedHashMap<>();
    private final Map<UUID, Link> links = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, Delivery> outbox = new LinkedHashMap<>();
    private final Map<UUID, Long> minecraftToDiscord = new LinkedHashMap<>();
    private final Map<Long, UUID> discordToMinecraft = new LinkedHashMap<>();
    private final Map<String, Long> recentDeliveries = new LinkedHashMap<>();
    private final ArrayDeque<AuditEntry> audit = new ArrayDeque<>();
    private long deliveredCount;
    private long failedAttemptCount;
    private long droppedCount;
    private long expiredCount;
    private long deduplicatedCount;
    private long revision;

    public Settings settings(UUID nationId) {
        return settings.getOrDefault(nationId, DEFAULT_SETTINGS);
    }

    public Link link(UUID nationId) {
        return links.getOrDefault(nationId, Link.unlinked());
    }

    public Optional<UUID> nationForGuild(long guildId) {
        if (guildId <= 0L) return Optional.empty();
        return links.entrySet().stream()
                .filter(entry -> entry.getValue().linked && entry.getValue().guildId == guildId)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public int linkedCount() {
        return (int) links.values().stream().filter(Link::linked).count();
    }

    public long revision() {
        return revision;
    }

    public int pendingCount(UUID nationId) {
        int count = 0;
        for (Delivery delivery : outbox.values()) if (delivery.nationId.equals(nationId)) count++;
        return count;
    }

    public Optional<UUID> minecraftForDiscord(long discordId) {
        return Optional.ofNullable(discordToMinecraft.get(discordId));
    }

    public OptionalLong discordForMinecraft(UUID minecraftId) {
        Long value = minecraftToDiscord.get(minecraftId);
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }

    public AccountLinkResult linkAccount(UUID minecraftId, long discordId) {
        if (minecraftId == null || discordId <= 0L) return AccountLinkResult.INVALID;
        Long currentDiscord = minecraftToDiscord.get(minecraftId);
        UUID currentMinecraft = discordToMinecraft.get(discordId);
        if (Long.valueOf(discordId).equals(currentDiscord) && minecraftId.equals(currentMinecraft)) {
            return AccountLinkResult.ALREADY_LINKED;
        }
        if (currentDiscord != null) return AccountLinkResult.MINECRAFT_IN_USE;
        if (currentMinecraft != null) return AccountLinkResult.DISCORD_IN_USE;
        minecraftToDiscord.put(minecraftId, discordId);
        discordToMinecraft.put(discordId, minecraftId);
        changed();
        return AccountLinkResult.LINKED;
    }

    public boolean unlinkAccount(UUID minecraftId) {
        Long discordId = minecraftToDiscord.remove(minecraftId);
        if (discordId == null) return false;
        discordToMinecraft.remove(discordId);
        changed();
        return true;
    }

    public void updateSettings(UUID nationId, Settings value) {
        Settings safe = value == null ? DEFAULT_SETTINGS : value;
        if (!link(nationId).linked()) safe = new Settings(
                safe.inGame(), false, safe.includeCoordinates(), safe.mentionOnSiege());
        if (safe.equals(settings(nationId))) return;
        settings.put(nationId, safe);
        changed();
    }

    /** Called only by a trusted bridge/administrative integration after it verifies the pairing externally. */
    public void markLinked(UUID nationId, long guildId, long channelId) {
        if (guildId <= 0L || channelId <= 0L) return;
        List<UUID> displaced = links.entrySet().stream()
                .filter(entry -> entry.getValue().guildId == guildId && !entry.getKey().equals(nationId))
                .map(Map.Entry::getKey).toList();
        displaced.forEach(this::unlink);
        links.put(nationId, new Link(true, guildId, channelId, 0L));
        changed();
    }

    public void updateDiscordTarget(UUID nationId, long channelId, long mentionRoleId) {
        Link current = link(nationId);
        if (!current.linked || channelId <= 0L) return;
        links.put(nationId, new Link(true, current.guildId, channelId, Math.max(0L, mentionRoleId)));
        changed();
    }

    public void unlink(UUID nationId) {
        boolean removed = links.remove(nationId) != null;
        int pendingBefore = outbox.size();
        outbox.values().removeIf(delivery -> delivery.nationId.equals(nationId));
        int cancelled = pendingBefore - outbox.size();
        if (cancelled > 0) {
            droppedCount += cancelled;
            removed = true;
        }
        Settings current = settings(nationId);
        if (current.discord()) {
            settings.put(nationId, new Settings(current.inGame(), false,
                    current.includeCoordinates(), current.mentionOnSiege()));
            removed = true;
        }
        if (removed) changed();
    }

    public Optional<UUID> unlinkGuild(long guildId) {
        Optional<UUID> nation = nationForGuild(guildId);
        nation.ifPresent(this::unlink);
        return nation;
    }

    public Optional<UUID> clearMentionRole(long guildId, long roleId) {
        Optional<UUID> nation = nationForGuild(guildId);
        if (nation.isEmpty()) return Optional.empty();
        Link current = link(nation.get());
        if (current.mentionRoleId != roleId) return Optional.empty();
        links.put(nation.get(), new Link(true, current.guildId, current.channelId, 0L));
        changed();
        return nation;
    }

    public Optional<UUID> enqueue(UUID nationId, EventType type, ResourceLocation dimension,
                                  BlockPos pos, List<String> arguments, long nowMillis) {
        Settings preference = settings(nationId);
        Link link = link(nationId);
        if (!NotificationDeliveryPolicy.canQueue(link.linked, preference.discord, outbox.size())) {
            return Optional.empty();
        }
        EventType safeType = type == null ? EventType.SYSTEM : type;
        String key = deliveryKey(nationId, safeType, dimension, pos);
        long window = com.ruskserver.moveearth_addtional.config.DiscordBotConfig
                .deduplicationWindowSeconds() * 1_000L;
        Long recent = recentDeliveries.get(key);
        boolean queued = outbox.values().stream().anyMatch(delivery -> delivery.key.equals(key));
        if (queued || (recent != null && NotificationDeliveryPolicy.duplicate(recent, nowMillis, window))) {
            deduplicatedCount++;
            setDirty();
            return Optional.empty();
        }
        UUID id = UUID.randomUUID();
        List<String> safeArguments = arguments == null ? List.of() : arguments.stream()
                .map(value -> value == null ? "" : value)
                .map(value -> value.substring(0, Math.min(256, value.length())))
                .limit(16)
                .toList();
        BlockPos safePos = preference.includeCoordinates ? pos : null;
        outbox.put(id, new Delivery(id, nationId, safeType,
                dimension, safePos, safeArguments, Math.max(0L, nowMillis), Math.max(0L, nowMillis), 0, key));
        setDirty();
        return Optional.of(id);
    }

    /** Read-only delivery lease. Transport implementations must call acknowledge/fail afterwards. */
    public List<Delivery> ready(long nowMillis, int limit) {
        if (limit <= 0) return List.of();
        return outbox.values().stream()
                .filter(delivery -> delivery.availableAtMillis <= nowMillis)
                .limit(Math.min(256, limit))
                .toList();
    }

    public boolean acknowledge(UUID deliveryId) {
        Delivery delivery = outbox.remove(deliveryId);
        if (delivery == null) return false;
        recentDeliveries.put(delivery.key, System.currentTimeMillis());
        deliveredCount++;
        audit("delivery", delivery.nationId, null, 0L, true, delivery.type.name());
        return true;
    }

    public boolean fail(UUID deliveryId, long nowMillis) {
        Delivery delivery = outbox.get(deliveryId);
        if (delivery == null) return false;
        int attempts = delivery.attempts + 1;
        failedAttemptCount++;
        boolean dropped = NotificationDeliveryPolicy.shouldDrop(attempts);
        if (dropped) {
            outbox.remove(deliveryId);
            droppedCount++;
        }
        else outbox.put(deliveryId, delivery.retryAt(
                nowMillis + NotificationDeliveryPolicy.retryDelayMillis(attempts), attempts));
        audit("delivery", delivery.nationId, null, 0L, false,
                dropped ? "retry_limit:" + delivery.type.name() : "retry:" + attempts);
        return true;
    }

    public int pruneExpired(long nowMillis, long retentionMillis, long deduplicationWindowMillis) {
        int before = outbox.size();
        outbox.values().removeIf(delivery -> NotificationDeliveryPolicy.expired(
                delivery.createdAtMillis, nowMillis, retentionMillis));
        int removed = before - outbox.size();
        expiredCount += removed;
        boolean recentRemoved = recentDeliveries.entrySet().removeIf(entry -> !NotificationDeliveryPolicy.duplicate(
                entry.getValue(), nowMillis, deduplicationWindowMillis));
        if (removed > 0) audit("delivery_expired", null, null, 0L, false,
                Integer.toString(removed));
        else if (recentRemoved) setDirty();
        return removed;
    }

    public DeliveryStats deliveryStats() {
        return new DeliveryStats(outbox.size(), deliveredCount, failedAttemptCount,
                droppedCount, expiredCount, deduplicatedCount);
    }

    public void audit(String action, UUID nationId, UUID minecraftId, long discordId,
                      boolean success, String detail) {
        String safeAction = trim(action, 64);
        String safeDetail = trim(detail, 256);
        audit.addLast(new AuditEntry(System.currentTimeMillis(), safeAction, nationId,
                minecraftId, Math.max(0L, discordId), success, safeDetail));
        int limit = com.ruskserver.moveearth_addtional.config.DiscordBotConfig.auditLogEntries();
        while (audit.size() > limit) audit.removeFirst();
        setDirty();
    }

    public List<AuditEntry> recentAudit(int limit) {
        return recentAudit(null, 0L, limit);
    }

    public List<AuditEntry> recentAudit(UUID nationId, long discordId, int limit) {
        List<AuditEntry> result = new ArrayList<>();
        var iterator = audit.descendingIterator();
        while (iterator.hasNext() && result.size() < Math.max(0, Math.min(25, limit))) {
            AuditEntry entry = iterator.next();
            if (nationId == null && discordId <= 0L
                    || nationId != null && nationId.equals(entry.nationId)
                    || discordId > 0L && discordId == entry.discordId) result.add(entry);
        }
        return List.copyOf(result);
    }

    private static String trim(String value, int maximum) {
        String safe = value == null ? "" : value;
        return safe.substring(0, Math.min(maximum, safe.length()));
    }

    private static String deliveryKey(UUID nationId, EventType type, ResourceLocation dimension,
                                      BlockPos pos) {
        String canonical = nationId + "|" + type + "|" + (dimension == null ? "" : dimension)
                + "|" + (pos == null ? "" : pos.asLong());
        return UUID.nameUUIDFromBytes(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private void changed() {
        revision++;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("Revision", revision);
        ListTag preferenceList = new ListTag();
        for (Map.Entry<UUID, Settings> entry : settings.entrySet()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("Nation", entry.getKey());
            value.putBoolean("InGame", entry.getValue().inGame);
            value.putBoolean("Discord", entry.getValue().discord);
            value.putBoolean("Coordinates", entry.getValue().includeCoordinates);
            value.putBoolean("MentionSiege", entry.getValue().mentionOnSiege);
            preferenceList.add(value);
        }
        tag.put("Settings", preferenceList);
        ListTag linkList = new ListTag();
        for (Map.Entry<UUID, Link> entry : links.entrySet()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("Nation", entry.getKey());
            value.putLong("Guild", entry.getValue().guildId);
            value.putLong("Channel", entry.getValue().channelId);
            value.putLong("MentionRole", entry.getValue().mentionRoleId);
            linkList.add(value);
        }
        tag.put("Links", linkList);
        ListTag accountList = new ListTag();
        minecraftToDiscord.forEach((minecraft, discord) -> {
            CompoundTag value = new CompoundTag();
            value.putUUID("Minecraft", minecraft);
            value.putLong("Discord", discord);
            accountList.add(value);
        });
        tag.put("Accounts", accountList);
        ListTag deliveryList = new ListTag();
        for (Delivery delivery : outbox.values()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("Id", delivery.id);
            value.putUUID("Nation", delivery.nationId);
            value.putString("Type", delivery.type.name());
            if (delivery.dimension != null) value.putString("Dimension", delivery.dimension.toString());
            if (delivery.pos != null) value.putLong("Pos", delivery.pos.asLong());
            ListTag argumentList = new ListTag();
            delivery.arguments.forEach(argument -> argumentList.add(StringTag.valueOf(argument)));
            value.put("Arguments", argumentList);
            value.putLong("CreatedAt", delivery.createdAtMillis);
            value.putLong("AvailableAt", delivery.availableAtMillis);
            value.putInt("Attempts", delivery.attempts);
            value.putString("Key", delivery.key);
            deliveryList.add(value);
        }
        tag.put("Outbox", deliveryList);
        ListTag recentList = new ListTag();
        recentDeliveries.forEach((key, timestamp) -> {
            CompoundTag value = new CompoundTag();
            value.putString("Key", key);
            value.putLong("At", timestamp);
            recentList.add(value);
        });
        tag.put("RecentDeliveries", recentList);
        ListTag auditList = new ListTag();
        for (AuditEntry entry : audit) {
            CompoundTag value = new CompoundTag();
            value.putLong("At", entry.atMillis);
            value.putString("Action", entry.action);
            if (entry.nationId != null) value.putUUID("Nation", entry.nationId);
            if (entry.minecraftId != null) value.putUUID("Minecraft", entry.minecraftId);
            value.putLong("Discord", entry.discordId);
            value.putBoolean("Success", entry.success);
            value.putString("Detail", entry.detail);
            auditList.add(value);
        }
        tag.put("Audit", auditList);
        tag.putLong("Delivered", deliveredCount);
        tag.putLong("FailedAttempts", failedAttemptCount);
        tag.putLong("Dropped", droppedCount);
        tag.putLong("Expired", expiredCount);
        tag.putLong("Deduplicated", deduplicatedCount);
        return tag;
    }

    public static NationNotificationSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        NationNotificationSavedData data = new NationNotificationSavedData();
        data.revision = Math.max(0L, tag.getLong("Revision"));
        ListTag preferenceList = tag.getList("Settings", Tag.TAG_COMPOUND);
        for (int index = 0; index < preferenceList.size(); index++) {
            CompoundTag value = preferenceList.getCompound(index);
            if (!value.hasUUID("Nation")) continue;
            data.settings.put(value.getUUID("Nation"), new Settings(value.getBoolean("InGame"),
                    value.getBoolean("Discord"), value.getBoolean("Coordinates"),
                    value.getBoolean("MentionSiege")));
        }
        ListTag linkList = tag.getList("Links", Tag.TAG_COMPOUND);
        for (int index = 0; index < linkList.size(); index++) {
            CompoundTag value = linkList.getCompound(index);
            if (value.hasUUID("Nation") && value.getLong("Guild") > 0L && value.getLong("Channel") > 0L) {
                data.links.put(value.getUUID("Nation"), new Link(true, value.getLong("Guild"),
                        value.getLong("Channel"), Math.max(0L, value.getLong("MentionRole"))));
            }
        }
        ListTag deliveryList = tag.getList("Outbox", Tag.TAG_COMPOUND);
        ListTag accountList = tag.getList("Accounts", Tag.TAG_COMPOUND);
        for (int index = 0; index < accountList.size(); index++) {
            CompoundTag value = accountList.getCompound(index);
            if (!value.hasUUID("Minecraft") || value.getLong("Discord") <= 0L) continue;
            UUID minecraft = value.getUUID("Minecraft");
            long discord = value.getLong("Discord");
            if (!data.minecraftToDiscord.containsKey(minecraft) && !data.discordToMinecraft.containsKey(discord)) {
                data.minecraftToDiscord.put(minecraft, discord);
                data.discordToMinecraft.put(discord, minecraft);
            }
        }
        for (int index = 0; index < deliveryList.size() && data.outbox.size() < MAX_OUTBOX; index++) {
            CompoundTag value = deliveryList.getCompound(index);
            if (!value.hasUUID("Id") || !value.hasUUID("Nation")) continue;
            try {
                EventType type = EventType.valueOf(value.getString("Type"));
                ResourceLocation dimension = ResourceLocation.tryParse(value.getString("Dimension"));
                BlockPos pos = value.contains("Pos", Tag.TAG_LONG) ? BlockPos.of(value.getLong("Pos")) : null;
                ListTag argumentList = value.getList("Arguments", Tag.TAG_STRING);
                List<String> arguments = new ArrayList<>(argumentList.size());
                for (int argument = 0; argument < argumentList.size(); argument++) {
                    arguments.add(argumentList.getString(argument));
                }
                UUID id = value.getUUID("Id");
                data.outbox.put(id, new Delivery(id, value.getUUID("Nation"), type, dimension, pos,
                        List.copyOf(arguments), Math.max(0L, value.getLong("CreatedAt")),
                        Math.max(0L, value.getLong("AvailableAt")),
                        Math.max(0, value.getInt("Attempts")), value.contains("Key", Tag.TAG_STRING)
                        ? value.getString("Key") : id.toString()));
            } catch (IllegalArgumentException ignored) {
                // Skip malformed or obsolete delivery types without preventing the world from loading.
            }
        }
        ListTag recentList = tag.getList("RecentDeliveries", Tag.TAG_COMPOUND);
        for (int index = 0; index < recentList.size(); index++) {
            CompoundTag value = recentList.getCompound(index);
            if (!value.getString("Key").isBlank()) data.recentDeliveries.put(
                    value.getString("Key"), Math.max(0L, value.getLong("At")));
        }
        ListTag auditList = tag.getList("Audit", Tag.TAG_COMPOUND);
        int auditStart = Math.max(0, auditList.size() - 2048);
        for (int index = auditStart; index < auditList.size(); index++) {
            CompoundTag value = auditList.getCompound(index);
            data.audit.addLast(new AuditEntry(Math.max(0L, value.getLong("At")),
                    value.getString("Action"), value.hasUUID("Nation") ? value.getUUID("Nation") : null,
                    value.hasUUID("Minecraft") ? value.getUUID("Minecraft") : null,
                    Math.max(0L, value.getLong("Discord")), value.getBoolean("Success"),
                    value.getString("Detail")));
        }
        data.deliveredCount = Math.max(0L, tag.getLong("Delivered"));
        data.failedAttemptCount = Math.max(0L, tag.getLong("FailedAttempts"));
        data.droppedCount = Math.max(0L, tag.getLong("Dropped"));
        data.expiredCount = Math.max(0L, tag.getLong("Expired"));
        data.deduplicatedCount = Math.max(0L, tag.getLong("Deduplicated"));
        return data;
    }

    public static NationNotificationSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(NationNotificationSavedData::new,
                        NationNotificationSavedData::load, null), "moveearth_nation_notifications");
    }

    public record Settings(boolean inGame, boolean discord, boolean includeCoordinates,
                           boolean mentionOnSiege) { }

    public record Link(boolean linked, long guildId, long channelId, long mentionRoleId) {
        public static Link unlinked() { return new Link(false, 0L, 0L, 0L); }
    }

    public enum EventType {
        SYSTEM,
        SIEGE_INITIAL_STARTED,
        SIEGE_STARTED,
        CORE_DAMAGED,
        CORE_FALLEN,
        TERRITORY_EXPOSED,
        TERRITORY_RESEALED,
        UPKEEP_WARNING,
        JOIN_APPLICATION,
        COUNTEROFFENSIVE_STARTED,
        COUNTEROFFENSIVE_SUCCEEDED,
        COUNTEROFFENSIVE_FAILED,
        SIEGE_ENDED,
        TERRITORY_LOST,
        TERRITORY_OCCUPIED,
        RECOVERY_STARTED,
        RECOVERY_OBJECTIVE,
        RECOVERY_COMPLETED,
        RECOVERY_EXPIRED,
        DISPATCH_CREATED,
        DISPATCH_ACTIVATED,
        DISPATCH_COMPLETED,
        DISPATCH_CANCELLED,
        RIVAL_UPDATED
    }

    public record Delivery(UUID id, UUID nationId, EventType type, ResourceLocation dimension,
                           BlockPos pos, List<String> arguments, long createdAtMillis,
                           long availableAtMillis, int attempts, String key) {
        private Delivery retryAt(long availableAt, int attemptCount) {
            return new Delivery(id, nationId, type, dimension, pos, arguments,
                    createdAtMillis, availableAt, attemptCount, key);
        }
    }

    public enum AccountLinkResult { LINKED, ALREADY_LINKED, MINECRAFT_IN_USE, DISCORD_IN_USE, INVALID }

    public record DeliveryStats(int pending, long delivered, long failedAttempts,
                                long dropped, long expired, long deduplicated) { }

    public record AuditEntry(long atMillis, String action, UUID nationId, UUID minecraftId,
                             long discordId, boolean success, String detail) { }
}
