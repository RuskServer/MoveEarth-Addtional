package com.ruskserver.moveearth_addtional.analytics.storage;

import com.ruskserver.moveearth_addtional.analytics.queue.AnalyticsEventQueue;

import java.nio.file.Path;
import java.util.List;

/**
 * プレイヤー分析データの永続化および集約操作を定義するストレージエンジンインターフェース
 */
public interface AnalyticsStorageEngine extends AutoCloseable {

    /**
     * データベースの初期化とマイグレーションを実行
     */
    void initialize(Path dbPath) throws Exception;

    /**
     * イベントバッチをトランザクションで永続化
     */
    void writeBatch(List<AnalyticsEventQueue.AnalyticsEvent> events) throws Exception;

    /**
     * 5分粒度のプレイヤー活動データを日次集約テーブル（player_activity_daily）へ集約
     */
    void aggregateDaily(long beforeEpochSec) throws Exception;

    /**
     * 保持期間（90日/365日）を超過した古いレコードを削除
     */
    void purgeOldRecords(long cutoff5mEpochSec, long cutoffDailyEpochSec, long cutoffSessionEpochSec) throws Exception;

    /**
     * データベースファイルのサイズ（バイト）を取得
     */
    long getDatabaseSizeBytes();

    /**
     * データベースが開いているか
     */
    boolean isOpen();

    @Override
    void close() throws Exception;
}
