package com.ruskserver.moveearth_addtional.jobs;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative job selection, experience and shared points. */
public final class JobProgressSavedData extends SavedData {
    public static final int MAX_ACTIVE_JOBS = 2;
    private static final int DATA_VERSION = 1;
    private final Map<UUID, PlayerJobs> players = new HashMap<>();

    public JoinResult join(UUID playerId, ResourceLocation jobId) {
        PlayerJobs jobs = player(playerId);
        if (jobs.activeJobs.contains(jobId)) {
            return JoinResult.ALREADY_ACTIVE;
        }
        if (jobs.activeJobs.size() >= MAX_ACTIVE_JOBS) {
            return JoinResult.LIMIT_REACHED;
        }
        jobs.activeJobs.add(jobId);
        jobs.progress.computeIfAbsent(jobId, ignored -> new Progress());
        setDirty();
        return JoinResult.JOINED;
    }

    public boolean leave(UUID playerId, ResourceLocation jobId) {
        boolean removed = player(playerId).activeJobs.remove(jobId);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public AwardResult award(UUID playerId, JobDefinition definition, int amount) {
        PlayerJobs jobs = player(playerId);
        if (!jobs.activeJobs.contains(definition.id()) || amount <= 0) {
            return AwardResult.NONE;
        }
        return awardInternal(jobs, definition, amount);
    }

    public AwardResult awardAdmin(UUID playerId, JobDefinition definition, int amount) {
        if (amount <= 0) {
            return AwardResult.NONE;
        }
        return awardInternal(player(playerId), definition, amount);
    }

    private AwardResult awardInternal(PlayerJobs jobs, JobDefinition definition, int amount) {
        Progress progress = jobs.progress.computeIfAbsent(definition.id(), ignored -> new Progress());
        if (progress.level >= definition.maxLevel()) {
            return AwardResult.NONE;
        }

        int oldLevel = progress.level;
        long remaining = amount;
        while (remaining > 0 && progress.level < definition.maxLevel()) {
            long needed = definition.xpNeededForNextLevel(progress.level) - progress.xpInLevel;
            long applied = Math.min(remaining, needed);
            progress.xpInLevel += applied;
            progress.totalXp += applied;
            remaining -= applied;
            if (progress.xpInLevel >= definition.xpNeededForNextLevel(progress.level)) {
                progress.level++;
                progress.xpInLevel = 0;
                jobs.points = safeAdd(jobs.points, definition.pointsPerLevel());
            }
        }
        setDirty();
        int gainedLevels = progress.level - oldLevel;
        return new AwardResult(amount - (int) remaining, oldLevel, progress.level,
                gainedLevels * definition.pointsPerLevel());
    }

    public void addPoints(UUID playerId, int amount) {
        PlayerJobs jobs = player(playerId);
        jobs.points = Math.max(0, safeAdd(jobs.points, amount));
        setDirty();
    }

    public void reset(UUID playerId) {
        players.remove(playerId);
        setDirty();
    }

    public PlayerSnapshot snapshot(UUID playerId) {
        PlayerJobs jobs = player(playerId);
        Map<ResourceLocation, ProgressSnapshot> progress = new HashMap<>();
        jobs.progress.forEach((id, value) -> progress.put(id,
                new ProgressSnapshot(value.level, value.xpInLevel, value.totalXp)));
        return new PlayerSnapshot(jobs.points, Set.copyOf(jobs.activeJobs), Map.copyOf(progress));
    }

    private PlayerJobs player(UUID playerId) {
        return players.computeIfAbsent(playerId, ignored -> new PlayerJobs());
    }

    private static int safeAdd(int left, int right) {
        long sum = (long) left + right;
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, sum));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Version", DATA_VERSION);
        CompoundTag playerTags = new CompoundTag();
        players.forEach((playerId, jobs) -> playerTags.put(playerId.toString(), jobs.save()));
        tag.put("Players", playerTags);
        return tag;
    }

    public static JobProgressSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        JobProgressSavedData data = new JobProgressSavedData();
        CompoundTag playerTags = tag.getCompound("Players");
        for (String key : playerTags.getAllKeys()) {
            try {
                data.players.put(UUID.fromString(key), PlayerJobs.load(playerTags.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    public static JobProgressSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(JobProgressSavedData::new, JobProgressSavedData::load, null),
                "moveearth_jobs");
    }

    public enum JoinResult {
        JOINED,
        ALREADY_ACTIVE,
        LIMIT_REACHED
    }

    public record AwardResult(int awardedXp, int oldLevel, int newLevel, int pointsEarned) {
        public static final AwardResult NONE = new AwardResult(0, 0, 0, 0);

        public boolean leveledUp() {
            return newLevel > oldLevel;
        }
    }

    public record ProgressSnapshot(int level, long xpInLevel, long totalXp) {
    }

    public record PlayerSnapshot(int points, Set<ResourceLocation> activeJobs,
                                 Map<ResourceLocation, ProgressSnapshot> progress) {
        public ProgressSnapshot progress(ResourceLocation id) {
            return progress.getOrDefault(id, new ProgressSnapshot(1, 0, 0));
        }
    }

    private static final class PlayerJobs {
        private int points;
        private final Set<ResourceLocation> activeJobs = new LinkedHashSet<>();
        private final Map<ResourceLocation, Progress> progress = new HashMap<>();

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Points", points);
            ListTag active = new ListTag();
            activeJobs.forEach(id -> active.add(StringTag.valueOf(id.toString())));
            tag.put("Active", active);

            CompoundTag progressTag = new CompoundTag();
            progress.forEach((id, value) -> progressTag.put(id.toString(), value.save()));
            tag.put("Progress", progressTag);
            return tag;
        }

        private static PlayerJobs load(CompoundTag tag) {
            PlayerJobs jobs = new PlayerJobs();
            jobs.points = Math.max(0, tag.getInt("Points"));
            ListTag active = tag.getList("Active", Tag.TAG_STRING);
            for (int i = 0; i < active.size() && jobs.activeJobs.size() < MAX_ACTIVE_JOBS; i++) {
                ResourceLocation id = ResourceLocation.tryParse(active.getString(i));
                if (id != null) {
                    jobs.activeJobs.add(id);
                }
            }
            CompoundTag progressTag = tag.getCompound("Progress");
            for (String key : progressTag.getAllKeys()) {
                ResourceLocation id = ResourceLocation.tryParse(key);
                if (id != null) {
                    jobs.progress.put(id, Progress.load(progressTag.getCompound(key)));
                }
            }
            return jobs;
        }
    }

    private static final class Progress {
        private int level = 1;
        private long xpInLevel;
        private long totalXp;

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Level", level);
            tag.putLong("Xp", xpInLevel);
            tag.putLong("TotalXp", totalXp);
            return tag;
        }

        private static Progress load(CompoundTag tag) {
            Progress progress = new Progress();
            progress.level = Math.max(1, tag.getInt("Level"));
            progress.xpInLevel = Math.max(0, tag.getLong("Xp"));
            progress.totalXp = Math.max(progress.xpInLevel, tag.getLong("TotalXp"));
            return progress;
        }
    }
}
