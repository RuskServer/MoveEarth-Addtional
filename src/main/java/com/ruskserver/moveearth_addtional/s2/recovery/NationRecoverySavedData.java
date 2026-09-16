package com.ruskserver.moveearth_addtional.s2.recovery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class NationRecoverySavedData extends SavedData {
    private final Map<UUID, Episode> episodes = new LinkedHashMap<>();
    private final Map<UUID, UUID> bySourceSiege = new LinkedHashMap<>();
    private final java.util.Set<UUID> protectionWaived = new java.util.LinkedHashSet<>();

    public OpenResult open(UUID sourceSiegeId, UUID nationId, UUID attackerId, boolean individualAttacker,
                           UUID coreId, ResourceLocation dimension, BlockPos pos, int originalRadius,
                           int wallTarget, long now, long expiresAt) {
        UUID existingId = bySourceSiege.get(sourceSiegeId);
        if (existingId != null) return new OpenResult(false, episodes.get(existingId));
        Episode created = new Episode(UUID.randomUUID(), sourceSiegeId, nationId, attackerId,
                individualAttacker, coreId, dimension, pos.immutable(), Math.max(0, originalRadius),
                now, Math.max(now, expiresAt), State.ACTIVE, 0L, Math.max(0, wallTarget), 0,
                false, false, false, 30, 0L, 0L, null, 1L);
        episodes.put(created.id(), created);
        bySourceSiege.put(sourceSiegeId, created.id());
        setDirty();
        return new OpenResult(true, created);
    }

    public Optional<Episode> byId(UUID id) { return Optional.ofNullable(episodes.get(id)); }

    public Optional<Episode> activeForNation(UUID nationId) {
        return episodes.values().stream().filter(value -> value.nationId().equals(nationId)
                && value.state() == State.ACTIVE).max(Comparator.comparingLong(Episode::startedAt));
    }

    public Optional<Episode> eligibleForNation(UUID nationId, long now) {
        return episodes.values().stream().filter(value -> value.nationId().equals(nationId)
                && (value.state() == State.ACTIVE || value.state() == State.COMPLETED)
                && now < value.expiresAt()).max(Comparator.comparingLong(Episode::startedAt));
    }

    public List<Episode> forNation(UUID nationId) {
        return episodes.values().stream().filter(value -> value.nationId().equals(nationId))
                .sorted(Comparator.comparingLong(Episode::startedAt).reversed()).toList();
    }

    public List<Episode> active() {
        return episodes.values().stream().filter(value -> value.state() == State.ACTIVE).toList();
    }

    public boolean hasPriorAidSince(UUID nationId, UUID currentEpisode, long sinceOpenTick) {
        return episodes.values().stream().anyMatch(value -> value.nationId().equals(nationId)
                && !value.id().equals(currentEpisode) && value.startedAt() >= sinceOpenTick
                && (value.aidUsed() > 0L || value.aidReserved() > 0L));
    }

    public Episode updateProgress(UUID id, long resealStartedAt, int healthyWalls,
                                  RecoveryObjectivePolicy.Progress progress) {
        Episode before = episodes.get(id);
        if (before == null || before.state() != State.ACTIVE) return before;
        State nextState = progress.supportPercent() >= 100 ? State.COMPLETED : State.ACTIVE;
        Episode after = before.withProgress(resealStartedAt, healthyWalls, progress, nextState);
        if (!after.equals(before)) {
            episodes.put(id, after);
            setDirty();
        }
        return after;
    }

    public Episode markUpkeepPaid(UUID nationId) {
        Episode before = activeForNation(nationId).orElse(null);
        if (before == null || before.upkeepPaid()) return before;
        RecoveryObjectivePolicy.Progress progress = RecoveryObjectivePolicy.evaluate(
                before.resealed(), before.wallTarget(), before.healthyWalls(), true);
        Episode after = before.withProgress(before.resealStartedAt(), before.healthyWalls(), progress,
                progress.supportPercent() >= 100 ? State.COMPLETED : State.ACTIVE);
        episodes.put(after.id(), after);
        setDirty();
        return after;
    }

    public boolean expire(UUID id, long now) {
        Episode before = episodes.get(id);
        if (before == null || before.state() != State.ACTIVE || now < before.expiresAt()) return false;
        episodes.put(id, before.withState(State.EXPIRED));
        setDirty();
        return true;
    }

    public void closeNation(UUID nationId) {
        boolean changed = false;
        for (Episode value : List.copyOf(episodes.values())) {
            if (value.nationId().equals(nationId) && value.state() == State.ACTIVE) {
                episodes.put(value.id(), value.withState(State.CLOSED));
                changed = true;
            } else if (nationId.equals(value.rivalNation())) {
                episodes.put(value.id(), value.withRival(null));
                changed = true;
            }
        }
        if (changed) setDirty();
    }

    public boolean setRival(UUID episodeId, UUID rivalNation) {
        Episode before = episodes.get(episodeId);
        if (before == null || before.individualAttacker()) return false;
        Episode after = before.withRival(rivalNation);
        if (after.equals(before)) return true;
        episodes.put(episodeId, after);
        setDirty();
        return true;
    }

    public boolean waiveProtection(UUID episodeId) {
        Episode episode = episodes.get(episodeId);
        if (episode == null || episode.state() != State.ACTIVE && episode.state() != State.COMPLETED) return false;
        boolean changed = protectionWaived.add(episodeId);
        if (changed) setDirty();
        return true;
    }

    public boolean protectionWaived(UUID episodeId) { return protectionWaived.contains(episodeId); }

    public boolean reserveAid(UUID episodeId, long amount) {
        Episode before = episodes.get(episodeId);
        if (before == null || before.state() != State.ACTIVE && before.state() != State.COMPLETED
                || amount < 0L) return false;
        episodes.put(episodeId, before.withAid(before.aidUsed(), safeAdd(before.aidReserved(), amount)));
        setDirty();
        return true;
    }

    public boolean settleAid(UUID episodeId, long reservedAmount, long usedAmount) {
        Episode before = episodes.get(episodeId);
        if (before == null || reservedAmount < 0L || usedAmount < 0L
                || before.aidReserved() < reservedAmount || usedAmount > reservedAmount) return false;
        episodes.put(episodeId, before.withAid(safeAdd(before.aidUsed(), usedAmount),
                before.aidReserved() - reservedAmount));
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("SchemaVersion", 1);
        ListTag list = new ListTag();
        for (Episode value : episodes.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", value.id());
            entry.putUUID("SourceSiege", value.sourceSiegeId());
            entry.putUUID("Nation", value.nationId());
            if (value.attackerId() != null) entry.putUUID("Attacker", value.attackerId());
            entry.putBoolean("IndividualAttacker", value.individualAttacker());
            entry.putUUID("Core", value.coreId());
            entry.putString("Dimension", value.dimension().toString());
            entry.putLong("Pos", value.pos().asLong());
            entry.putInt("OriginalRadius", value.originalRadius());
            entry.putLong("StartedAt", value.startedAt());
            entry.putLong("ExpiresAt", value.expiresAt());
            entry.putString("State", value.state().name());
            entry.putLong("ResealStartedAt", value.resealStartedAt());
            entry.putInt("WallTarget", value.wallTarget());
            entry.putInt("HealthyWalls", value.healthyWalls());
            entry.putBoolean("Resealed", value.resealed());
            entry.putBoolean("WallsRestored", value.wallsRestored());
            entry.putBoolean("UpkeepPaid", value.upkeepPaid());
            entry.putInt("SupportPercent", value.supportPercent());
            entry.putLong("AidUsed", value.aidUsed());
            entry.putLong("AidReserved", value.aidReserved());
            if (value.rivalNation() != null) entry.putUUID("Rival", value.rivalNation());
            entry.putLong("Revision", value.revision());
            list.add(entry);
        }
        tag.put("Episodes", list);
        ListTag waived = new ListTag();
        for (UUID id : protectionWaived) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            waived.add(entry);
        }
        tag.put("ProtectionWaived", waived);
        return tag;
    }

    public static NationRecoverySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        NationRecoverySavedData data = new NationRecoverySavedData();
        ListTag list = tag.getList("Episodes", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            if (!entry.hasUUID("Id") || !entry.hasUUID("SourceSiege") || !entry.hasUUID("Nation")
                    || !entry.hasUUID("Core") || dimension == null) continue;
            State state;
            try { state = State.valueOf(entry.getString("State")); }
            catch (IllegalArgumentException ignored) { state = State.ACTIVE; }
            Episode value = new Episode(entry.getUUID("Id"), entry.getUUID("SourceSiege"),
                    entry.getUUID("Nation"), entry.hasUUID("Attacker") ? entry.getUUID("Attacker") : null,
                    entry.getBoolean("IndividualAttacker"), entry.getUUID("Core"), dimension,
                    BlockPos.of(entry.getLong("Pos")), Math.max(0, entry.getInt("OriginalRadius")),
                    Math.max(0L, entry.getLong("StartedAt")), Math.max(0L, entry.getLong("ExpiresAt")), state,
                    Math.max(0L, entry.getLong("ResealStartedAt")), Math.max(0, entry.getInt("WallTarget")),
                    Math.max(0, entry.getInt("HealthyWalls")), entry.getBoolean("Resealed"),
                    entry.getBoolean("WallsRestored"), entry.getBoolean("UpkeepPaid"),
                    Math.max(0, Math.min(100, entry.getInt("SupportPercent"))),
                    Math.max(0L, entry.getLong("AidUsed")), Math.max(0L, entry.getLong("AidReserved")),
                    entry.hasUUID("Rival") ? entry.getUUID("Rival") : null,
                    Math.max(1L, entry.getLong("Revision")));
            data.episodes.put(value.id(), value);
            data.bySourceSiege.put(value.sourceSiegeId(), value.id());
        }
        ListTag waived = tag.getList("ProtectionWaived", Tag.TAG_COMPOUND);
        for (int index = 0; index < waived.size(); index++) {
            CompoundTag entry = waived.getCompound(index);
            if (entry.hasUUID("Id") && data.episodes.containsKey(entry.getUUID("Id"))) {
                data.protectionWaived.add(entry.getUUID("Id"));
            }
        }
        return data;
    }

    public static NationRecoverySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(NationRecoverySavedData::new, NationRecoverySavedData::load, null),
                "moveearth_nation_recovery");
    }

    private static long safeAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    public enum State { ACTIVE, COMPLETED, EXPIRED, CLOSED }
    public record OpenResult(boolean created, Episode episode) { }
    public record Episode(UUID id, UUID sourceSiegeId, UUID nationId, UUID attackerId,
                          boolean individualAttacker, UUID coreId, ResourceLocation dimension,
                          BlockPos pos, int originalRadius, long startedAt, long expiresAt, State state,
                          long resealStartedAt, int wallTarget, int healthyWalls, boolean resealed,
                          boolean wallsRestored, boolean upkeepPaid, int supportPercent,
                          long aidUsed, long aidReserved, UUID rivalNation, long revision) {
        Episode withProgress(long resealStart, int walls, RecoveryObjectivePolicy.Progress progress, State nextState) {
            return new Episode(id, sourceSiegeId, nationId, attackerId, individualAttacker, coreId, dimension,
                    pos, originalRadius, startedAt, expiresAt, nextState, resealStart, wallTarget,
                    Math.max(0, walls), progress.resealed(), progress.wallsRestored(), progress.upkeepPaid(),
                    progress.supportPercent(), aidUsed, aidReserved, rivalNation, revision + 1L);
        }
        Episode withState(State next) {
            return new Episode(id, sourceSiegeId, nationId, attackerId, individualAttacker, coreId, dimension,
                    pos, originalRadius, startedAt, expiresAt, next, resealStartedAt, wallTarget,
                    healthyWalls, resealed, wallsRestored, upkeepPaid, supportPercent,
                    aidUsed, aidReserved, rivalNation, revision + 1L);
        }
        Episode withRival(UUID rival) {
            return new Episode(id, sourceSiegeId, nationId, attackerId, individualAttacker, coreId, dimension,
                    pos, originalRadius, startedAt, expiresAt, state, resealStartedAt, wallTarget,
                    healthyWalls, resealed, wallsRestored, upkeepPaid, supportPercent,
                    aidUsed, aidReserved, rival, revision + 1L);
        }
        Episode withAid(long used, long reserved) {
            return new Episode(id, sourceSiegeId, nationId, attackerId, individualAttacker, coreId, dimension,
                    pos, originalRadius, startedAt, expiresAt, state, resealStartedAt, wallTarget,
                    healthyWalls, resealed, wallsRestored, upkeepPaid, supportPercent,
                    Math.max(0L, used), Math.max(0L, reserved), rivalNation, revision + 1L);
        }
    }
}
