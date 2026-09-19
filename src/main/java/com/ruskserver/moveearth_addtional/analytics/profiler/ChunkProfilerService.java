package com.ruskserver.moveearth_addtional.analytics.profiler;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.analytics.config.AnalyticsConfig;
import com.ruskserver.moveearth_addtional.analytics.model.ChunkLoadSample;
import com.ruskserver.moveearth_addtional.analytics.model.ChunkProfileRecord;
import com.ruskserver.moveearth_addtional.analytics.queue.AnalyticsEventQueue;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Bounded, sampled profiler activated only for suspicious chunks. */
public final class ChunkProfilerService {
    public static final ChunkProfilerService INSTANCE = new ChunkProfilerService();

    private volatile ProfileSession active;
    private volatile Set<ChunkKey> candidates = Set.of();
    private int consecutiveSlowSamples;
    private long nextAutomaticStartEpochSec;

    private ChunkProfilerService() {
    }

    public void onPerformanceSample(MinecraftServer server, double tps, double mspt,
                                    List<ChunkLoadSample> estimatedLoads) {
        updateCandidates(estimatedLoads);

        if (!AnalyticsConfig.isProfilerAutoEnabled() || active != null) return;
        boolean slow = tps < AnalyticsConfig.getProfilerTpsThreshold()
                || mspt > AnalyticsConfig.getProfilerMsptThreshold();
        consecutiveSlowSamples = slow ? consecutiveSlowSamples + 1 : 0;
        long now = System.currentTimeMillis() / 1000L;
        if (consecutiveSlowSamples >= AnalyticsConfig.getProfilerConsecutiveSamples()
                && now >= nextAutomaticStartEpochSec && !candidates.isEmpty()) {
            start(server, AnalyticsConfig.getProfilerDurationSeconds(), "automatic", false);
            consecutiveSlowSamples = 0;
        }
    }

    public void updateCandidates(List<ChunkLoadSample> estimatedLoads) {
        int limit = AnalyticsConfig.getProfilerCandidateChunks();
        Set<ChunkKey> updated = new HashSet<>();
        if (estimatedLoads == null) estimatedLoads = List.of();
        estimatedLoads.stream()
                .sorted(Comparator.comparingDouble(ChunkLoadSample::loadScore).reversed())
                .limit(limit)
                .forEach(sample -> updated.add(new ChunkKey(
                        sample.dimension(), sample.chunkX(), sample.chunkZ())));
        candidates = Set.copyOf(updated);
    }

    public StartResult start(MinecraftServer server, int seconds, String trigger, boolean manual) {
        if (server == null) return new StartResult(false, "server_unavailable", null);
        if (active != null) return new StartResult(false, "already_running", snapshot());
        if (candidates.isEmpty()) return new StartResult(false, "no_candidates", null);
        int boundedSeconds = Math.max(1, Math.min(120, seconds));
        long now = System.currentTimeMillis() / 1000L;
        long currentServerTick = server.getTickCount();
        ProfileSession session = new ProfileSession(UUID.randomUUID(), now,
                currentServerTick, currentServerTick + boundedSeconds * 20L,
                trigger == null || trigger.isBlank() ? (manual ? "manual" : "automatic") : trigger,
                candidates);
        active = session;
        Moveearth_addtional.LOGGER.info(
                "Chunk profiler started: session={} trigger={} duration={}s candidates={} sampleEvery={} ticks",
                session.id, session.trigger, boundedSeconds, session.candidates.size(),
                AnalyticsConfig.getProfilerSampleIntervalTicks());
        return new StartResult(true, "started", snapshot());
    }

    public void tick(MinecraftServer server) {
        ProfileSession session = active;
        if (session != null) {
            session.observedServerTick = server.getTickCount();
            if (server.getTickCount() >= session.endServerTick) finish(false);
        }
    }

    public boolean stop() {
        return finish(true);
    }

    public boolean isRunning() {
        return active != null;
    }

    /** Fast guard used by injected hot paths before resolving chunk coordinates. */
    public boolean shouldSample(ServerLevel level) {
        ProfileSession session = active;
        return session != null && level != null
                && level.getServer().getTickCount() % AnalyticsConfig.getProfilerSampleIntervalTicks() == 0;
    }

    public long begin(ServerLevel level, ChunkPos pos) {
        ProfileSession session = active;
        if (session == null || level == null || pos == null) return 0L;
        if (!shouldSample(level)) return 0L;
        ChunkKey key = new ChunkKey(level.dimension().location().toString(), pos.x, pos.z);
        return session.candidates.contains(key) ? System.nanoTime() : 0L;
    }

    public void end(ServerLevel level, ChunkPos pos, Category category, long startedNanos) {
        if (startedNanos == 0L || level == null || pos == null || category == null) return;
        ProfileSession session = active;
        if (session == null) return;
        long elapsed = Math.max(0L, System.nanoTime() - startedNanos);
        ChunkKey key = new ChunkKey(level.dimension().location().toString(), pos.x, pos.z);
        if (!session.candidates.contains(key)) return;
        session.accumulators.computeIfAbsent(key, ignored -> new Accumulator())
                .record(level.getServer().getTickCount(), category, elapsed);
    }

    public Status snapshot() {
        ProfileSession session = active;
        if (session == null) return new Status(false, null, "idle", 0L, candidates.size(), 0);
        long remainingTicks = Math.max(0L, session.endServerTick - session.observedServerTick);
        return new Status(true, session.id, session.trigger, remainingTicks,
                session.candidates.size(), session.accumulators.size());
    }

    public void reset() {
        active = null;
        candidates = Set.of();
        consecutiveSlowSamples = 0;
        nextAutomaticStartEpochSec = 0L;
    }

    private boolean finish(boolean manualStop) {
        ProfileSession session = active;
        if (session == null) return false;
        active = null;
        long finishedAt = System.currentTimeMillis() / 1000L;
        List<ChunkProfileRecord> records = new ArrayList<>();
        for (Map.Entry<ChunkKey, Accumulator> entry : session.accumulators.entrySet()) {
            Accumulator accumulator = entry.getValue();
            accumulator.finishCurrentTick();
            if (accumulator.sampledTicks == 0) continue;
            ChunkKey key = entry.getKey();
            double totalMs = nanosToMillis(accumulator.totalNanos);
            records.add(new ChunkProfileRecord(session.id, session.startedAtEpochSec, finishedAt,
                    manualStop ? session.trigger + "_stopped" : session.trigger,
                    key.dimension, key.chunkX, key.chunkZ, accumulator.sampledTicks,
                    totalMs / accumulator.sampledTicks, nanosToMillis(accumulator.maximumTickNanos), totalMs,
                    nanosToMillis(accumulator.entityNanos), nanosToMillis(accumulator.blockEntityNanos),
                    nanosToMillis(accumulator.scheduledTickNanos), accumulator.entityCalls,
                    accumulator.blockEntityCalls, accumulator.scheduledTickCalls));
        }
        records.sort(Comparator.comparingDouble(ChunkProfileRecord::totalMs).reversed());
        AnalyticsEventQueue.INSTANCE.enqueue(new AnalyticsEventQueue.ChunkProfileEvent(records));
        nextAutomaticStartEpochSec = finishedAt + AnalyticsConfig.getProfilerCooldownSeconds();
        Moveearth_addtional.LOGGER.info(
                "Chunk profiler finished: session={} trigger={} recordedChunks={}",
                session.id, session.trigger, records.size());
        return true;
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0D;
    }

    public enum Category { ENTITY, BLOCK_ENTITY, SCHEDULED_TICK }

    public record StartResult(boolean started, String reason, Status status) { }

    public record Status(boolean running, UUID sessionId, String trigger, long remainingTicks,
                         int candidateChunks, int measuredChunks) { }

    private record ChunkKey(String dimension, int chunkX, int chunkZ) { }

    private static final class ProfileSession {
        final UUID id;
        final long startedAtEpochSec;
        final long endServerTick;
        final String trigger;
        final Set<ChunkKey> candidates;
        final Map<ChunkKey, Accumulator> accumulators = new HashMap<>();
        volatile long observedServerTick;

        ProfileSession(UUID id, long startedAtEpochSec, long initialServerTick, long endServerTick,
                       String trigger, Set<ChunkKey> candidates) {
            this.id = id;
            this.startedAtEpochSec = startedAtEpochSec;
            this.endServerTick = endServerTick;
            this.trigger = trigger;
            this.candidates = Set.copyOf(candidates);
            this.observedServerTick = initialServerTick;
        }
    }

    private static final class Accumulator {
        long currentTick = Long.MIN_VALUE;
        long currentTickNanos;
        long maximumTickNanos;
        long totalNanos;
        long entityNanos;
        long blockEntityNanos;
        long scheduledTickNanos;
        long entityCalls;
        long blockEntityCalls;
        long scheduledTickCalls;
        int sampledTicks;

        void record(long tick, Category category, long nanos) {
            if (currentTick != tick) {
                finishCurrentTick();
                currentTick = tick;
            }
            currentTickNanos += nanos;
            totalNanos += nanos;
            switch (category) {
                case ENTITY -> { entityNanos += nanos; entityCalls++; }
                case BLOCK_ENTITY -> { blockEntityNanos += nanos; blockEntityCalls++; }
                case SCHEDULED_TICK -> { scheduledTickNanos += nanos; scheduledTickCalls++; }
            }
        }

        void finishCurrentTick() {
            if (currentTick == Long.MIN_VALUE) return;
            maximumTickNanos = Math.max(maximumTickNanos, currentTickNanos);
            sampledTicks++;
            currentTickNanos = 0L;
            currentTick = Long.MIN_VALUE;
        }
    }
}
