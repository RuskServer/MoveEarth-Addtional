package com.ruskserver.moveearth_addtional.analytics.storage;

import com.ruskserver.moveearth_addtional.analytics.config.AnalyticsConfig;
import com.ruskserver.moveearth_addtional.analytics.queue.AnalyticsEventQueue;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

/**
 * サーバーライフサイクルと分析ストレージエンジンを統合管理するサービス
 */
public class AnalyticsStorageService {

    public static final AnalyticsStorageService INSTANCE = new AnalyticsStorageService();

    private AnalyticsStorageEngine storageEngine;
    private AnalyticsStorageWorker storageWorker;

    public AnalyticsStorageService() {
    }

    /**
     * サーバー起動時の初期化処理
     */
    public synchronized void start(MinecraftServer server) {
        if (storageWorker != null && storageWorker.isRunning()) {
            return;
        }

        try {
            Path worldDir = server.getWorldPath(LevelResource.ROOT);
            Path dbPath = worldDir.resolve("moveearth/analytics/analytics.db");

            SqliteAnalyticsStorageEngine engine = new SqliteAnalyticsStorageEngine();
            engine.initialize(dbPath);
            this.storageEngine = engine;

            this.storageWorker = new AnalyticsStorageWorker(engine, AnalyticsEventQueue.INSTANCE);
            this.storageWorker.start();

            // Webダッシュボードサーバーの起動
            com.ruskserver.moveearth_addtional.analytics.web.AnalyticsWebServer.INSTANCE.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * サーバーTick時の定期メンテナンス（日次集約・保持期間パージ）
     */
    public synchronized void performDailyMaintenance() {
        if (storageEngine == null || !storageEngine.isOpen()) {
            return;
        }

        try {
            long nowSec = System.currentTimeMillis() / 1000L;
            long cutoffDailySec = nowSec - (AnalyticsConfig.RETENTION_DAILY_DAYS * 86400L);
            long cutoff5mSec = nowSec - (AnalyticsConfig.RETENTION_5M_DAYS * 86400L);
            long cutoffSessionSec = nowSec - (AnalyticsConfig.RETENTION_SESSION_DAYS * 86400L);

            // 1. 日次集約
            storageEngine.aggregateDaily(nowSec);

            // 2. パージ
            storageEngine.purgeOldRecords(cutoff5mSec, cutoffDailySec, cutoffSessionSec);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * サーバー停止時の終了処理
     */
    public synchronized void stop(long timeoutMs) {
        // Webダッシュボードサーバーの停止
        com.ruskserver.moveearth_addtional.analytics.web.AnalyticsWebServer.INSTANCE.stop();

        if (storageWorker != null) {
            storageWorker.stopAndFlush(timeoutMs);
            storageWorker = null;
        }
        this.storageEngine = null;
    }

    public AnalyticsStorageEngine getStorageEngine() {
        return storageEngine;
    }

    public AnalyticsStorageWorker getStorageWorker() {
        return storageWorker;
    }
}
