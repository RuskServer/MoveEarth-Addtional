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
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative job selection, experience and shared points. */
public final class JobProgressSavedData extends SavedData {
    public static final int MAX_ACTIVE_JOBS = 2;
    private static final int DATA_VERSION = 4;
    private static final double XP_EPSILON = 1.0E-9D;
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

    public AwardResult award(UUID playerId, JobDefinition definition, double amount, long gameTime) {
        PlayerJobs jobs = player(playerId);
        if (!jobs.activeJobs.contains(definition.id()) || !Double.isFinite(amount) || amount <= 0) {
            return AwardResult.NONE;
        }
        return awardInternal(jobs, definition, amount, gameTime);
    }

    public AwardResult awardAdmin(UUID playerId, JobDefinition definition, double amount, long gameTime) {
        if (!Double.isFinite(amount) || amount <= 0) {
            return AwardResult.NONE;
        }
        return awardInternal(player(playerId), definition, amount, gameTime);
    }

    private AwardResult awardInternal(PlayerJobs jobs, JobDefinition definition, double amount, long gameTime) {
        Progress progress = jobs.progress.computeIfAbsent(definition.id(), ignored -> new Progress());
        int oldLevel = progress.level;
        double remaining = amount;
        while (progress.level < definition.maxLevel()) {
            double threshold = definition.xpNeededForNextLevel(progress.level);
            if (progress.xpInLevel + XP_EPSILON >= threshold) {
                progress.xpInLevel = Math.max(0.0D, progress.xpInLevel - threshold);
                progress.level++;
                continue;
            }
            if (remaining <= XP_EPSILON) {
                break;
            }
            double needed = threshold - progress.xpInLevel;
            double applied = Math.min(remaining, needed);
            progress.xpInLevel += applied;
            remaining -= applied;
        }
        if (progress.level >= definition.maxLevel()) {
            progress.xpInLevel = 0.0D;
        }
        double updatedTotalXp = progress.totalXp + amount;
        progress.totalXp = Double.isFinite(updatedTotalXp) ? updatedTotalXp : Double.MAX_VALUE;
        int gainedLevels = progress.level - oldLevel;
        int levelPoints = safeMultiply(gainedLevels, definition.pointsPerLevel());
        JobPointIncome.Result recurring = JobPointIncome.apply(jobs.recurringWindowStartedAt,
                jobs.recurringPointXp, jobs.recurringPointsInWindow, amount, gameTime);
        jobs.recurringWindowStartedAt = recurring.startedAt();
        jobs.recurringPointXp = recurring.xpTowardsNextPoint();
        jobs.recurringPointsInWindow = recurring.pointsInWindow();
        int pointsEarned = safeAdd(levelPoints, recurring.pointsEarned());
        jobs.points = Math.max(0, safeAdd(jobs.points, pointsEarned));
        setDirty();
        return new AwardResult(amount, oldLevel, progress.level, pointsEarned,
                recurring.pointsEarned());
    }

    public void addPoints(UUID playerId, int amount) {
        PlayerJobs jobs = player(playerId);
        jobs.points = Math.max(0, safeAdd(jobs.points, amount));
        setDirty();
    }

    public boolean trySpendPoints(UUID playerId, int amount) {
        if (amount <= 0) {
            return false;
        }
        PlayerJobs jobs = player(playerId);
        if (jobs.points < amount) {
            return false;
        }
        jobs.points -= amount;
        setDirty();
        return true;
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

    public RecurringPointSnapshot recurringSnapshot(UUID playerId, long gameTime) {
        PlayerJobs jobs = player(playerId);
        if (jobs.recurringWindowStartedAt >= 0
                && (gameTime < jobs.recurringWindowStartedAt
                || gameTime - jobs.recurringWindowStartedAt >= JobPointIncome.WINDOW_TICKS)) {
            jobs.recurringWindowStartedAt = -1;
            jobs.recurringPointXp = 0;
            jobs.recurringPointsInWindow = 0;
            setDirty();
        }
        long remaining = JobPointIncome.ticksRemaining(jobs.recurringWindowStartedAt, gameTime);
        return new RecurringPointSnapshot(jobs.recurringPointXp, jobs.recurringPointsInWindow, remaining);
    }

    public void rememberName(UUID playerId, String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        String boundedName = name.length() > 16 ? name.substring(0, 16) : name;
        PlayerJobs jobs = player(playerId);
        if (!boundedName.equals(jobs.lastKnownName)) {
            jobs.lastKnownName = boundedName;
            setDirty();
        }
    }

    public List<LeaderboardEntry> leaderboard(ResourceLocation jobId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return players.entrySet().stream()
                .filter(entry -> entry.getValue().progress.containsKey(jobId))
                .map(entry -> {
                    Progress progress = entry.getValue().progress.get(jobId);
                    String name = entry.getValue().lastKnownName;
                    if (name.isBlank()) {
                        name = entry.getKey().toString().substring(0, 8);
                    }
                    return new LeaderboardEntry(entry.getKey(), name, progress.level,
                            progress.xpInLevel, progress.totalXp);
                })
                .filter(entry -> entry.level() > 1 || entry.totalXp() > XP_EPSILON)
                .sorted((left, right) -> JobLeaderboardOrder.compare(
                        left.level(), left.xpInLevel(), left.totalXp(), left.playerName(), left.playerId(),
                        right.level(), right.xpInLevel(), right.totalXp(), right.playerName(), right.playerId()))
                .limit(Math.min(limit, 100))
                .toList();
    }

    private PlayerJobs player(UUID playerId) {
        return players.computeIfAbsent(playerId, ignored -> new PlayerJobs());
    }

    private static int safeAdd(int left, int right) {
        long sum = (long) left + right;
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, sum));
    }

    private static int safeMultiply(int left, int right) {
        long product = (long) left * right;
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, product));
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

    public record AwardResult(double awardedXp, int oldLevel, int newLevel, int pointsEarned,
                              int recurringPointsEarned) {
        public static final AwardResult NONE = new AwardResult(0, 0, 0, 0, 0);

        public boolean leveledUp() {
            return newLevel > oldLevel;
        }
    }

    public record ProgressSnapshot(int level, double xpInLevel, double totalXp) {
    }

    public record RecurringPointSnapshot(double xpTowardsNextPoint, int pointsInWindow,
                                         long ticksRemaining) {
    }

    public record PlayerSnapshot(int points, Set<ResourceLocation> activeJobs,
                                 Map<ResourceLocation, ProgressSnapshot> progress) {
        public ProgressSnapshot progress(ResourceLocation id) {
            return progress.getOrDefault(id, new ProgressSnapshot(1, 0, 0));
        }
    }

    public record LeaderboardEntry(UUID playerId, String playerName, int level,
                                   double xpInLevel, double totalXp) {
    }

    private static final class PlayerJobs {
        private int points;
        private String lastKnownName = "";
        private long recurringWindowStartedAt = -1;
        private double recurringPointXp;
        private int recurringPointsInWindow;
        private final Set<ResourceLocation> activeJobs = new LinkedHashSet<>();
        private final Map<ResourceLocation, Progress> progress = new HashMap<>();

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Points", points);
            tag.putString("LastKnownName", lastKnownName);
            if (recurringWindowStartedAt >= 0) tag.putLong("RecurringWindowStartedAt", recurringWindowStartedAt);
            tag.putDouble("RecurringPointXp", recurringPointXp);
            tag.putInt("RecurringPointsInWindow", recurringPointsInWindow);
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
            String loadedName = tag.getString("LastKnownName");
            jobs.lastKnownName = loadedName.length() > 16 ? loadedName.substring(0, 16) : loadedName;
            jobs.recurringWindowStartedAt = tag.contains("RecurringWindowStartedAt")
                    ? tag.getLong("RecurringWindowStartedAt") : -1;
            double loadedRecurringXp = tag.getDouble("RecurringPointXp");
            jobs.recurringPointXp = Double.isFinite(loadedRecurringXp)
                    ? Math.max(0.0D, Math.min(JobPointIncome.XP_PER_POINT, loadedRecurringXp)) : 0.0D;
            jobs.recurringPointsInWindow = Math.max(0,
                    Math.min(JobPointIncome.MAX_POINTS_PER_WINDOW, tag.getInt("RecurringPointsInWindow")));
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
        private double xpInLevel;
        private double totalXp;

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Level", level);
            tag.putDouble("Xp", xpInLevel);
            tag.putDouble("TotalXp", totalXp);
            return tag;
        }

        private static Progress load(CompoundTag tag) {
            Progress progress = new Progress();
            progress.level = Math.max(1, tag.getInt("Level"));
            progress.xpInLevel = Math.max(0.0D, tag.getDouble("Xp"));
            progress.totalXp = Math.max(progress.xpInLevel, tag.getDouble("TotalXp"));
            return progress;
        }
    }
}
