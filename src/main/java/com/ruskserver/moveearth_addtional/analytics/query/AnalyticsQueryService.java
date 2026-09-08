package com.ruskserver.moveearth_addtional.analytics.query;

import com.ruskserver.moveearth_addtional.analytics.query.cache.AnalyticsQueryCache;
import com.ruskserver.moveearth_addtional.analytics.query.dto.*;
import com.ruskserver.moveearth_addtional.analytics.storage.AnalyticsStorageEngine;
import com.ruskserver.moveearth_addtional.analytics.storage.AnalyticsStorageService;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * プレイヤー分析KPIの非同期集計クエリおよびインメモリキャッシュを提供するサービス
 */
public class AnalyticsQueryService {

    public static final AnalyticsQueryService INSTANCE = new AnalyticsQueryService();

    private static final long CACHE_TTL_MS = 60_000L; // 60秒キャッシュ

    private final AnalyticsQueryCache<String, PlayerSummaryDto> playerCache = new AnalyticsQueryCache<>(CACHE_TTL_MS);
    private final AnalyticsQueryCache<String, List<PlayerSummaryDto>> topPlayersCache = new AnalyticsQueryCache<>(CACHE_TTL_MS);
    private final AnalyticsQueryCache<String, GroupSummaryDto> groupCache = new AnalyticsQueryCache<>(CACHE_TTL_MS);
    private final AnalyticsQueryCache<String, List<GroupSummaryDto>> allGroupsCache = new AnalyticsQueryCache<>(CACHE_TTL_MS);
    private final AnalyticsQueryCache<String, List<DetectorSummaryDto>> detectorCache = new AnalyticsQueryCache<>(CACHE_TTL_MS);
    private final AnalyticsQueryCache<String, List<SpatialHeatmapCellDto>> heatmapCache = new AnalyticsQueryCache<>(CACHE_TTL_MS);
    private final AnalyticsQueryCache<String, CollectorHealthDto> healthCache = new AnalyticsQueryCache<>(CACHE_TTL_MS);
    private final AnalyticsQueryCache<String, OverviewSummaryDto> overviewCache = new AnalyticsQueryCache<>(CACHE_TTL_MS);

    private final ExecutorService queryExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "MoveEarth-Analytics-Query-Worker");
        t.setDaemon(true);
        return t;
    });

    private AnalyticsStorageEngine storageEngineOverride;

    public AnalyticsQueryService() {
    }

    /**
     * テスト環境等でストレージエンジンを直接指定
     */
    public void setStorageEngineOverride(AnalyticsStorageEngine engine) {
        this.storageEngineOverride = engine;
    }

    private AnalyticsStorageEngine getStorageEngine() {
        if (storageEngineOverride != null) {
            return storageEngineOverride;
        }
        return AnalyticsStorageService.INSTANCE.getStorageEngine();
    }

    /**
     * プレイヤーのサマリーKPIを非同期で取得
     */
    public CompletableFuture<Optional<PlayerSummaryDto>> getPlayerSummaryAsync(UUID playerUuid, TimeWindow window) {
        if (playerUuid == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        String cacheKey = playerUuid + ":" + window.getId();
        Optional<PlayerSummaryDto> cached = playerCache.get(cacheKey);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached);
        }

        return CompletableFuture.supplyAsync(() -> {
            AnalyticsStorageEngine engine = getStorageEngine();
            if (engine == null || !engine.isOpen()) {
                return Optional.empty();
            }

            try {
                long nowSec = System.currentTimeMillis() / 1000L;
                Optional<PlayerSummaryDto> result = engine.queryPlayerSummary(playerUuid, window, nowSec);
                result.ifPresent(dto -> playerCache.put(cacheKey, dto));
                return result;
            } catch (Exception e) {
                e.printStackTrace();
                return Optional.empty();
            }
        }, queryExecutor);
    }

    /**
     * アクティブ時間上位のプレイヤー一覧を非同期で取得
     */
    public CompletableFuture<List<PlayerSummaryDto>> getTopActivePlayersAsync(TimeWindow window, int limit) {
        String cacheKey = window.getId() + ":" + limit;
        Optional<List<PlayerSummaryDto>> cached = topPlayersCache.get(cacheKey);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached.get());
        }

        return CompletableFuture.supplyAsync(() -> {
            AnalyticsStorageEngine engine = getStorageEngine();
            if (engine == null || !engine.isOpen()) {
                return Collections.emptyList();
            }

            try {
                long nowSec = System.currentTimeMillis() / 1000L;
                List<PlayerSummaryDto> result = engine.queryTopActivePlayers(window, limit, nowSec);
                topPlayersCache.put(cacheKey, result);
                return result;
            } catch (Exception e) {
                e.printStackTrace();
                return Collections.emptyList();
            }
        }, queryExecutor);
    }

    /**
     * 検知グループ（拠点）のサマリーKPIを非同期で取得
     */
    public CompletableFuture<Optional<GroupSummaryDto>> getGroupSummaryAsync(UUID groupOwnerUuid, TimeWindow window) {
        if (groupOwnerUuid == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        String cacheKey = groupOwnerUuid + ":" + window.getId();
        Optional<GroupSummaryDto> cached = groupCache.get(cacheKey);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached);
        }

        return CompletableFuture.supplyAsync(() -> {
            AnalyticsStorageEngine engine = getStorageEngine();
            if (engine == null || !engine.isOpen()) {
                return Optional.empty();
            }

            try {
                long nowSec = System.currentTimeMillis() / 1000L;
                Optional<GroupSummaryDto> result = engine.queryGroupSummary(groupOwnerUuid, window, nowSec);
                result.ifPresent(dto -> groupCache.put(cacheKey, dto));
                return result;
            } catch (Exception e) {
                e.printStackTrace();
                return Optional.empty();
            }
        }, queryExecutor);
    }

    /**
     * 全検知グループ（拠点）のサマリー一覧を非同期で取得
     */
    public CompletableFuture<List<GroupSummaryDto>> getAllGroupSummariesAsync(TimeWindow window) {
        String cacheKey = "all_groups:" + window.getId();
        Optional<List<GroupSummaryDto>> cached = allGroupsCache.get(cacheKey);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached.get());
        }

        return CompletableFuture.supplyAsync(() -> {
            AnalyticsStorageEngine engine = getStorageEngine();
            if (engine == null || !engine.isOpen()) {
                return Collections.emptyList();
            }

            try {
                long nowSec = System.currentTimeMillis() / 1000L;
                List<GroupSummaryDto> result = engine.queryAllGroupSummaries(window, nowSec);
                allGroupsCache.put(cacheKey, result);
                return result;
            } catch (Exception e) {
                e.printStackTrace();
                return Collections.emptyList();
            }
        }, queryExecutor);
    }

    /**
     * 指定した拠点所有者の検知器別サマリーを非同期で取得
     */
    public CompletableFuture<List<DetectorSummaryDto>> getDetectorSummariesAsync(
            UUID groupOwnerUuid,
            TimeWindow window
    ) {
        if (groupOwnerUuid == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        String cacheKey = groupOwnerUuid + ":" + window.getId();
        Optional<List<DetectorSummaryDto>> cached = detectorCache.get(cacheKey);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached.get());
        }

        return CompletableFuture.supplyAsync(() -> {
            AnalyticsStorageEngine engine = getStorageEngine();
            if (engine == null || !engine.isOpen()) {
                return Collections.emptyList();
            }
            try {
                long nowSec = System.currentTimeMillis() / 1000L;
                List<DetectorSummaryDto> result = engine.queryDetectorSummaries(groupOwnerUuid, window, nowSec);
                detectorCache.put(cacheKey, result);
                return result;
            } catch (Exception e) {
                e.printStackTrace();
                return Collections.emptyList();
            }
        }, queryExecutor);
    }

    /**
     * 空間ヒートマップ集計を非同期で取得
     */
    public CompletableFuture<List<SpatialHeatmapCellDto>> getSpatialHeatmapAsync(String dimension, TimeWindow window, int limit) {
        String dim = dimension != null ? dimension : "minecraft:overworld";
        String cacheKey = dim + ":" + window.getId() + ":" + limit;
        Optional<List<SpatialHeatmapCellDto>> cached = heatmapCache.get(cacheKey);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached.get());
        }

        return CompletableFuture.supplyAsync(() -> {
            AnalyticsStorageEngine engine = getStorageEngine();
            if (engine == null || !engine.isOpen()) {
                return Collections.emptyList();
            }

            try {
                long nowSec = System.currentTimeMillis() / 1000L;
                List<SpatialHeatmapCellDto> result = engine.querySpatialHeatmap(dim, window, limit, nowSec);
                heatmapCache.put(cacheKey, result);
                return result;
            } catch (Exception e) {
                e.printStackTrace();
                return Collections.emptyList();
            }
        }, queryExecutor);
    }

    /**
     * コレクターおよびストレージの最新ヘルス情報を非同期で取得
     */
    public CompletableFuture<CollectorHealthDto> getCollectorHealthAsync() {
        String cacheKey = "health";
        Optional<CollectorHealthDto> cached = healthCache.get(cacheKey);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached.get());
        }

        return CompletableFuture.supplyAsync(() -> {
            AnalyticsStorageEngine engine = getStorageEngine();
            if (engine == null || !engine.isOpen()) {
                return CollectorHealthDto.empty();
            }

            try {
                CollectorHealthDto result = engine.queryCollectorHealth();
                healthCache.put(cacheKey, result);
                return result;
            } catch (Exception e) {
                e.printStackTrace();
                return CollectorHealthDto.empty();
            }
        }, queryExecutor);
    }

    /**
     * サーバー全体の総合概況KPIを非同期で取得
     */
    public CompletableFuture<OverviewSummaryDto> getOverviewSummaryAsync(TimeWindow window) {
        String cacheKey = "overview:" + window.getId();
        Optional<OverviewSummaryDto> cached = overviewCache.get(cacheKey);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached.get());
        }

        return CompletableFuture.supplyAsync(() -> {
            AnalyticsStorageEngine engine = getStorageEngine();
            if (engine == null || !engine.isOpen()) {
                return OverviewSummaryDto.empty();
            }

            try {
                long nowSec = System.currentTimeMillis() / 1000L;
                OverviewSummaryDto result = engine.queryOverviewSummary(window, nowSec);
                overviewCache.put(cacheKey, result);
                return result;
            } catch (Exception e) {
                e.printStackTrace();
                return OverviewSummaryDto.empty();
            }
        }, queryExecutor);
    }

    /**
     * キャッシュのクリア
     */
    public void clearCache() {
        playerCache.clear();
        topPlayersCache.clear();
        groupCache.clear();
        allGroupsCache.clear();
        detectorCache.clear();
        heatmapCache.clear();
        healthCache.clear();
        overviewCache.clear();
    }
}
