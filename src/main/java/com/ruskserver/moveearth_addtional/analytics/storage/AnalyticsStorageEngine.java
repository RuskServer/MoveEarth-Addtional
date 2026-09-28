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

    /**
     * プレイヤーのサマリーKPIを集計
     */
    java.util.Optional<com.ruskserver.moveearth_addtional.analytics.query.dto.PlayerSummaryDto> queryPlayerSummary(
            java.util.UUID playerUuid,
            com.ruskserver.moveearth_addtional.analytics.query.dto.TimeWindow window,
            long currentEpochSec) throws Exception;

    /**
     * アクティブ時間上位のプレイヤーサマリー一覧を取得
     */
    List<com.ruskserver.moveearth_addtional.analytics.query.dto.PlayerSummaryDto> queryTopActivePlayers(
            com.ruskserver.moveearth_addtional.analytics.query.dto.TimeWindow window,
            int limit,
            long currentEpochSec) throws Exception;

    /**
     * 検知グループ（拠点）のサマリーKPIを集計
     */
    java.util.Optional<com.ruskserver.moveearth_addtional.analytics.query.dto.GroupSummaryDto> queryGroupSummary(
            java.util.UUID groupOwnerUuid,
            com.ruskserver.moveearth_addtional.analytics.query.dto.TimeWindow window,
            long currentEpochSec) throws Exception;

    /**
     * 全検知グループ（拠点）の一覧サマリーを取得
     */
    List<com.ruskserver.moveearth_addtional.analytics.query.dto.GroupSummaryDto> queryAllGroupSummaries(
            com.ruskserver.moveearth_addtional.analytics.query.dto.TimeWindow window,
            long currentEpochSec) throws Exception;

    /**
     * 指定した拠点所有者の検知器別サマリーを取得
     */
    List<com.ruskserver.moveearth_addtional.analytics.query.dto.DetectorSummaryDto> queryDetectorSummaries(
            java.util.UUID groupOwnerUuid,
            com.ruskserver.moveearth_addtional.analytics.query.dto.TimeWindow window,
            long currentEpochSec) throws Exception;

    /**
     * 指定ディメンションの空間ヒートマップ集計を取得
     */
    List<com.ruskserver.moveearth_addtional.analytics.query.dto.SpatialHeatmapCellDto> querySpatialHeatmap(
            String dimension,
            com.ruskserver.moveearth_addtional.analytics.query.dto.TimeWindow window,
            int limit,
            long currentEpochSec) throws Exception;

    /**
     * サーバー全体の総合概況KPIを集計
     */
    com.ruskserver.moveearth_addtional.analytics.query.dto.OverviewSummaryDto queryOverviewSummary(
            com.ruskserver.moveearth_addtional.analytics.query.dto.TimeWindow window,
            long currentEpochSec) throws Exception;

    /**
     * コレクターおよびストレージの最新ヘルス情報を取得
     */
    com.ruskserver.moveearth_addtional.analytics.query.dto.CollectorHealthDto queryCollectorHealth() throws Exception;

    /** TPS/MSPT time series for graphing and export. */
    List<com.ruskserver.moveearth_addtional.analytics.model.ServerPerformanceSample> queryServerPerformance(
            com.ruskserver.moveearth_addtional.analytics.query.dto.TimeWindow window,
            int limit,
            long currentEpochSec) throws Exception;

    /** Raw top-chunk samples retained for load history graphs and export. */
    List<com.ruskserver.moveearth_addtional.analytics.model.ChunkLoadSample> queryChunkLoadHistory(
            String dimension,
            com.ruskserver.moveearth_addtional.analytics.query.dto.TimeWindow window,
            int limit,
            long currentEpochSec) throws Exception;

    /** Aggregated ranking of the most consistently expensive chunks. */
    List<com.ruskserver.moveearth_addtional.analytics.query.dto.ChunkLoadSummaryDto> queryTopLoadedChunks(
            String dimension,
            com.ruskserver.moveearth_addtional.analytics.query.dto.TimeWindow window,
            int limit,
            long currentEpochSec) throws Exception;

    /** Completed sampled CPU profiles, newest sessions first. */
    List<com.ruskserver.moveearth_addtional.analytics.model.ChunkProfileRecord> queryChunkProfiles(
            com.ruskserver.moveearth_addtional.analytics.query.dto.TimeWindow window,
            int limit,
            long currentEpochSec) throws Exception;

    /** Game events, newest first, filtered by type prefix and optionally a nation or player. */
    List<com.ruskserver.moveearth_addtional.analytics.query.dto.GameEventDto> queryGameEvents(
            String typePrefix, java.util.UUID nationUuid, java.util.UUID playerUuid,
            long fromEpochSec, long toEpochSec, int limit) throws Exception;

    /** Game events grouped by one key, with counts, value sums and distinct players. */
    List<com.ruskserver.moveearth_addtional.analytics.query.dto.GameEventAggregateDto> aggregateGameEvents(
            String typePrefix, java.util.UUID nationUuid,
            com.ruskserver.moveearth_addtional.analytics.query.dto.GameEventGroupBy groupBy,
            long fromEpochSec, long toEpochSec, int limit) throws Exception;

    /** Stored nation snapshots in time order, for one nation or all of them. */
    List<com.ruskserver.moveearth_addtional.analytics.query.dto.NationHistoryPointDto> queryNationHistory(
            java.util.UUID nationUuid, long fromEpochSec, long toEpochSec) throws Exception;

    /** Players first seen inside the range. */
    long countNewPlayers(long fromEpochSec, long toEpochSec) throws Exception;

    /** Stored money supply snapshots in time order. */
    List<com.ruskserver.moveearth_addtional.analytics.query.dto.EconomyHistoryPointDto> queryEconomyHistory(
            long fromEpochSec, long toEpochSec) throws Exception;

    @Override
    void close() throws Exception;
}
