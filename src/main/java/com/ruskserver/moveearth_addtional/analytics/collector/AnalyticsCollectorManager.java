package com.ruskserver.moveearth_addtional.analytics.collector;

import com.ruskserver.moveearth_addtional.analytics.activity.ActivityCategory;
import com.ruskserver.moveearth_addtional.analytics.activity.PlayerActivityTracker;
import com.ruskserver.moveearth_addtional.analytics.config.AnalyticsConfig;
import com.ruskserver.moveearth_addtional.analytics.group.DetectorGroupService;
import com.ruskserver.moveearth_addtional.analytics.group.GroupRelation;
import com.ruskserver.moveearth_addtional.analytics.model.*;
import com.ruskserver.moveearth_addtional.analytics.queue.AnalyticsEventQueue;
import com.ruskserver.moveearth_addtional.analytics.profiler.ChunkProfilerService;
import com.ruskserver.moveearth_addtional.analytics.tracker.IntrusionTracker;
import com.ruskserver.moveearth_addtional.analytics.tracker.SessionTracker;
import com.ruskserver.moveearth_addtional.pvp.PvpMatchManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プレイヤー分析におけるイベント集計・定期サンプリング・5分バケットフラッシュを統括するマネージャー
 */
public class AnalyticsCollectorManager {

    public static final AnalyticsCollectorManager INSTANCE = new AnalyticsCollectorManager();
    private static final long PERFORMANCE_SAMPLE_INTERVAL_TICKS = 20L * 60L;
    private static final int MAX_RECORDED_CHUNKS_PER_SAMPLE = 100;
    private static final double BLOCK_ENTITY_WEIGHT = 2.5D;

    /** プレイヤー・ディメンション・グループごとの集計キー */
    public record PlayerBucketKey(UUID playerUuid, String dimension, @Nullable UUID groupOwnerUuid) {
    }

    /** プレイヤー・ディメンション・グループごとの5分バケット集計状態 */
    private static class PlayerBucketAccumulator {
        final UUID playerUuid;
        final String dimension;
        @Nullable final UUID groupOwnerUuid;

        int activeSeconds = 0;
        double distanceBlocks = 0.0;
        int breaks = 0;
        int places = 0;
        int crafts = 0;
        int pveKills = 0;
        int pvpKills = 0;
        int deaths = 0;
        double jobsXp = 0.0;
        int tpaSuccesses = 0;

        PlayerBucketAccumulator(UUID playerUuid, String dimension, @Nullable UUID groupOwnerUuid) {
            this.playerUuid = playerUuid;
            this.dimension = dimension;
            this.groupOwnerUuid = groupOwnerUuid;
        }
    }

    /** 空間セルごとの5分バケット集計状態 */
    private static class SpatialBucketAccumulator {
        int activeSamples = 0;
        final Set<UUID> uniquePlayers = new HashSet<>();
    }

    private final Map<PlayerBucketKey, PlayerBucketAccumulator> playerBuckets = new ConcurrentHashMap<>();
    private final Map<SpatialCellKey, SpatialBucketAccumulator> spatialBuckets = new ConcurrentHashMap<>();

    private long currentBucketEpochSec = 0L;

    public AnalyticsCollectorManager() {
        this.currentBucketEpochSec = alignToBucket(System.currentTimeMillis() / 1000L);
    }

    public static long alignToBucket(long epochSec) {
        return (epochSec / AnalyticsConfig.AGGREGATION_BUCKET_SECONDS) * AnalyticsConfig.AGGREGATION_BUCKET_SECONDS;
    }

    /**
     * サーバーTick毎の処理（30秒分散サンプリング、定期積算、5分バケットローテーション）
     */
    public void onServerTick(MinecraftServer server, long gameTime) {
        long currentTimeMs = System.currentTimeMillis();
        long currentEpochSec = currentTimeMs / 1000L;

        // 1. セッション時間のアクティブ/AFK積算更新（毎秒または数秒ごと）
        if (gameTime % 20 == 0) {
            SessionTracker.INSTANCE.updateAllAccounting(PlayerActivityTracker.INSTANCE, currentTimeMs);
        }

        // 2. 分散位置サンプリング
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) {
                continue; // スペクテイターは除外
            }

            // UUIDハッシュを用いてサンプリングtickを均等に分散
            int offset = Math.abs(player.getUUID().hashCode()) % AnalyticsConfig.POSITION_SAMPLE_INTERVAL_TICKS;
            if ((gameTime + offset) % AnalyticsConfig.POSITION_SAMPLE_INTERVAL_TICKS == 0) {
                samplePlayerPosition(player, currentTimeMs);
            }
        }

        // 3. 5分バケットのローテーションとキューフラッシュ判定
        long expectedBucket = alignToBucket(currentEpochSec);
        if (currentBucketEpochSec == 0L) {
            currentBucketEpochSec = expectedBucket;
        } else if (expectedBucket > currentBucketEpochSec) {
            flushCurrentBucket(currentBucketEpochSec);
            currentBucketEpochSec = expectedBucket;
        }

        // 4. TPS/MSPTと負荷上位チャンクの低頻度サンプリング（60秒ごと）
        if (gameTime % PERFORMANCE_SAMPLE_INTERVAL_TICKS == 0L) {
            sampleServerPerformance(server, currentEpochSec);
        }
    }

    private void sampleServerPerformance(MinecraftServer server, long recordedAtEpochSec) {
        ChunkCollection collection = collectChunkLoads(server, recordedAtEpochSec);
        List<ChunkLoadSample> chunkSamples = collection.samples();
        double mspt = Math.max(0.0D, server.getAverageTickTimeNanos() / 1_000_000.0D);
        double tps = Math.min(20.0D, 1_000.0D / Math.max(0.001D, mspt));
        ServerPerformanceSample serverSample = new ServerPerformanceSample(
                recordedAtEpochSec, tps, mspt, collection.loadedChunks(), server.getPlayerCount());
        AnalyticsEventQueue.INSTANCE.enqueue(
                new AnalyticsEventQueue.PerformanceSampleEvent(serverSample, chunkSamples));
        ChunkProfilerService.INSTANCE.onPerformanceSample(server, tps, mspt, chunkSamples);
    }

    public void prepareProfilerCandidates(MinecraftServer server) {
        ChunkProfilerService.INSTANCE.updateCandidates(
                collectChunkLoads(server, System.currentTimeMillis() / 1000L).samples());
    }

    private ChunkCollection collectChunkLoads(MinecraftServer server, long recordedAtEpochSec) {
        Map<ChunkDimensionKey, Integer> entitiesByChunk = new HashMap<>();
        int loadedChunks = 0;

        for (ServerLevel level : server.getAllLevels()) {
            String dimension = level.dimension().location().toString();
            loadedChunks += level.getChunkSource().getLoadedChunksCount();
            for (Entity entity : level.getAllEntities()) {
                ChunkPos pos = entity.chunkPosition();
                entitiesByChunk.merge(new ChunkDimensionKey(dimension, pos.x, pos.z), 1, Integer::sum);
            }
        }

        List<ChunkLoadSample> chunkSamples = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            if (!(level.getChunkSource().chunkMap instanceof LoadedChunkView loadedChunkView)) continue;
            String dimension = level.dimension().location().toString();
            for (LevelChunk chunk : loadedChunkView.moveearth$getTickingChunks()) {
                ChunkPos pos = chunk.getPos();
                int entityCount = entitiesByChunk.getOrDefault(
                        new ChunkDimensionKey(dimension, pos.x, pos.z), 0);
                int blockEntityCount = chunk.getBlockEntities().size();
                double loadScore = entityCount + blockEntityCount * BLOCK_ENTITY_WEIGHT;
                if (loadScore <= 0.0D) continue;
                chunkSamples.add(new ChunkLoadSample(recordedAtEpochSec, dimension, pos.x, pos.z,
                        entityCount, blockEntityCount, loadScore));
            }
        }
        chunkSamples.sort(Comparator.comparingDouble(ChunkLoadSample::loadScore).reversed());
        if (chunkSamples.size() > MAX_RECORDED_CHUNKS_PER_SAMPLE) {
            chunkSamples = new ArrayList<>(chunkSamples.subList(0, MAX_RECORDED_CHUNKS_PER_SAMPLE));
        }

        return new ChunkCollection(List.copyOf(chunkSamples), loadedChunks);
    }

    /**
     * 単一プレイヤーの位置サンプリング処理
     */
    private void samplePlayerPosition(ServerPlayer player, long currentTimeMs) {
        UUID uuid = player.getUUID();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        String dimension = player.serverLevel().dimension().location().toString();

        // PvPアリーナ判定
        boolean inPvpArena = PvpMatchManager.INSTANCE.isActive(player)
                || player.serverLevel().dimension().equals(PvpMatchManager.ARENA);
        final String effectiveDimension = inPvpArena ? "pvp_arena" : dimension;

        // 2ブロック以上の有効移動判定および移動距離計測 (異世界移動・テレポート除外)
        double distance = PlayerActivityTracker.INSTANCE.updatePositionAndGetDistance(uuid, x, y, z, effectiveDimension, currentTimeMs);

        // AFK判定
        boolean isAfk = PlayerActivityTracker.INSTANCE.isAfk(uuid, currentTimeMs);

        // 検知グループおよび立場の解決
        BlockPos blockPos = player.blockPosition();
        UUID groupOwner = DetectorGroupService.INSTANCE.findCoveringGroupOwner(player.serverLevel(), blockPos);
        GroupRelation relation = DetectorGroupService.INSTANCE.getGroupRelation(player.serverLevel(), blockPos, uuid);

        // プレイヤーバケットの更新
        PlayerBucketKey pKey = new PlayerBucketKey(uuid, effectiveDimension, groupOwner);
        PlayerBucketAccumulator pAcc = playerBuckets.computeIfAbsent(pKey,
                k -> new PlayerBucketAccumulator(uuid, effectiveDimension, groupOwner));

        pAcc.distanceBlocks += distance;
        if (!isAfk) {
            pAcc.activeSeconds += 30; // 30秒間アクティブ
        }

        // 空間セルバケットの更新（アクティブ時のみサンプリング加算）
        if (!isAfk) {
            SpatialCellKey cellKey = SpatialCellKey.of(effectiveDimension, x, y, z, groupOwner, relation);
            SpatialBucketAccumulator sAcc = spatialBuckets.computeIfAbsent(cellKey, k -> new SpatialBucketAccumulator());
            sAcc.activeSamples++;
            sAcc.uniquePlayers.add(uuid);
        }
    }

    // --- 各種ゲームイベントの記録メソッド ---

    public void onPlayerLogin(ServerPlayer player, long currentTimeMs) {
        UUID uuid = player.getUUID();
        String name = player.getScoreboardName();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        String dimension = player.serverLevel().dimension().location().toString();

        PlayerActivityTracker.INSTANCE.onPlayerLogin(uuid, x, y, z, dimension, currentTimeMs);
        SessionTracker.ActiveSession session = SessionTracker.INSTANCE.onLogin(uuid, name, currentTimeMs);

        AnalyticsEventQueue.INSTANCE.enqueue(new AnalyticsEventQueue.SessionStartEvent(
                session.getSessionId(),
                uuid,
                name,
                currentTimeMs / 1000L
        ));
    }

    public void onPlayerLogout(ServerPlayer player, long currentTimeMs) {
        UUID uuid = player.getUUID();
        SessionRecord sessionRecord = SessionTracker.INSTANCE.onLogout(uuid, PlayerActivityTracker.INSTANCE, currentTimeMs);
        PlayerActivityTracker.INSTANCE.onPlayerLogout(uuid);

        if (sessionRecord != null) {
            AnalyticsEventQueue.INSTANCE.enqueue(new AnalyticsEventQueue.SessionEndEvent(sessionRecord));
        }
    }

    public void recordBlockBreak(ServerPlayer player) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUUID();
        PlayerActivityTracker.INSTANCE.recordActivity(uuid, ActivityCategory.MINING, now);

        PlayerBucketAccumulator pAcc = getOrCreatePlayerBucket(player);
        pAcc.breaks++;
    }

    public void recordBlockPlace(ServerPlayer player) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUUID();
        PlayerActivityTracker.INSTANCE.recordActivity(uuid, ActivityCategory.BUILDING, now);

        PlayerBucketAccumulator pAcc = getOrCreatePlayerBucket(player);
        pAcc.places++;
    }

    public void recordCraft(ServerPlayer player) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUUID();
        PlayerActivityTracker.INSTANCE.recordActivity(uuid, ActivityCategory.CRAFTING, now);

        PlayerBucketAccumulator pAcc = getOrCreatePlayerBucket(player);
        pAcc.crafts++;
    }

    public void recordKill(ServerPlayer killer, boolean isPvpVictim) {
        long now = System.currentTimeMillis();
        UUID uuid = killer.getUUID();
        PlayerActivityTracker.INSTANCE.recordActivity(uuid,
                isPvpVictim ? ActivityCategory.COMBAT_PVP : ActivityCategory.COMBAT_PVE, now);

        PlayerBucketAccumulator pAcc = getOrCreatePlayerBucket(killer);
        if (isPvpVictim) {
            pAcc.pvpKills++;
        } else {
            pAcc.pveKills++;
        }
    }

    public void recordDeath(ServerPlayer victim) {
        long now = System.currentTimeMillis();
        UUID uuid = victim.getUUID();
        PlayerActivityTracker.INSTANCE.recordActivity(uuid, ActivityCategory.OTHER, now);

        PlayerBucketAccumulator pAcc = getOrCreatePlayerBucket(victim);
        pAcc.deaths++;
    }

    public void recordJobsXp(ServerPlayer player, double xp) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUUID();
        PlayerActivityTracker.INSTANCE.recordActivity(uuid, ActivityCategory.JOBS, now);

        PlayerBucketAccumulator pAcc = getOrCreatePlayerBucket(player);
        pAcc.jobsXp += xp;
    }

    public void recordTpaSuccess(ServerPlayer player) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUUID();
        PlayerActivityTracker.INSTANCE.recordActivity(uuid, ActivityCategory.TPA, now);

        PlayerBucketAccumulator pAcc = getOrCreatePlayerBucket(player);
        pAcc.tpaSuccesses++;
    }

    private PlayerBucketAccumulator getOrCreatePlayerBucket(ServerPlayer player) {
        UUID uuid = player.getUUID();
        boolean inPvpArena = PvpMatchManager.INSTANCE.isActive(player)
                || player.serverLevel().dimension().equals(PvpMatchManager.ARENA);
        String rawDim = player.serverLevel().dimension().location().toString();
        String effectiveDim = inPvpArena ? "pvp_arena" : rawDim;

        UUID groupOwner = DetectorGroupService.INSTANCE.findCoveringGroupOwner(player.serverLevel(), player.blockPosition());
        PlayerBucketKey key = new PlayerBucketKey(uuid, effectiveDim, groupOwner);

        return playerBuckets.computeIfAbsent(key, k -> new PlayerBucketAccumulator(uuid, effectiveDim, groupOwner));
    }

    /**
     * 5分バケットのフラッシュ処理（メモリ上集計を不変レコード化し非同期キューへ投入）
     */
    public synchronized void flushCurrentBucket(long bucketAtEpochSec) {
        // 1. プレイヤー活動バケットのフラッシュ
        List<PlayerActivityBucket> pList = new ArrayList<>();
        for (PlayerBucketAccumulator acc : playerBuckets.values()) {
            pList.add(new PlayerActivityBucket(
                    bucketAtEpochSec,
                    acc.playerUuid,
                    acc.dimension != null ? acc.dimension : "minecraft:overworld",
                    acc.groupOwnerUuid,
                    acc.activeSeconds,
                    acc.distanceBlocks,
                    acc.breaks,
                    acc.places,
                    acc.crafts,
                    acc.pveKills,
                    acc.pvpKills,
                    acc.deaths,
                    acc.jobsXp,
                    acc.tpaSuccesses
            ));
        }
        playerBuckets.clear();
        if (!pList.isEmpty()) {
            AnalyticsEventQueue.INSTANCE.enqueue(new AnalyticsEventQueue.PlayerActivityFlushEvent(pList));
        }

        // 2. 空間セル活動バケットのフラッシュ
        List<SpatialActivityBucket> sList = new ArrayList<>();
        for (Map.Entry<SpatialCellKey, SpatialBucketAccumulator> entry : spatialBuckets.entrySet()) {
            SpatialCellKey key = entry.getKey();
            SpatialBucketAccumulator acc = entry.getValue();
            sList.add(new SpatialActivityBucket(
                    bucketAtEpochSec,
                    key.dimension(),
                    key.cellX(),
                    key.cellZ(),
                    key.yBand(),
                    key.groupOwnerUuid(),
                    key.relation(),
                    acc.activeSamples,
                    acc.uniquePlayers.size()
            ));
        }
        spatialBuckets.clear();
        if (!sList.isEmpty()) {
            AnalyticsEventQueue.INSTANCE.enqueue(new AnalyticsEventQueue.SpatialActivityFlushEvent(sList));
        }

        // 3. 検知ブロック活動バケットのフラッシュ
        List<DetectorActivityBucket> dList = IntrusionTracker.INSTANCE.flushBucket(bucketAtEpochSec);
        if (!dList.isEmpty()) {
            AnalyticsEventQueue.INSTANCE.enqueue(new AnalyticsEventQueue.DetectorActivityFlushEvent(dList));
        }
    }

    /**
     * サーバー停止時のクリーンアップと全フラッシュ
     */
    public synchronized void onServerStopping(MinecraftServer server) {
        long currentEpochSec = System.currentTimeMillis() / 1000L;
        long bucket = alignToBucket(currentEpochSec);
        flushCurrentBucket(bucket);
    }

    private record ChunkDimensionKey(String dimension, int chunkX, int chunkZ) {
    }

    private record ChunkCollection(List<ChunkLoadSample> samples, int loadedChunks) {
    }
}
