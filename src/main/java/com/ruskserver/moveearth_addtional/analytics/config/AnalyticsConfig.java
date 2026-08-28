package com.ruskserver.moveearth_addtional.analytics.config;

/**
 * プレイヤー分析システムの設定および定数定義
 */
public final class AnalyticsConfig {

    private AnalyticsConfig() {
    }

    /** 位置サンプリング間隔 (30秒 = 600 ticks) */
    public static final int POSITION_SAMPLE_INTERVAL_TICKS = 20 * 30;

    /** 空間座標セルサイズ (32x32ブロック) */
    public static final int CELL_SIZE_BLOCKS = 32;

    /** メモリ集計バケット間隔 (5分 = 300秒) */
    public static final int AGGREGATION_BUCKET_SECONDS = 300;

    /** AFK判定の無活動閾値 (5分 = 300,000ミリ秒) */
    public static final long AFK_THRESHOLD_MS = 5 * 60 * 1000L;

    /** 有効移動とみなす最小移動距離 (2.0ブロック) */
    public static final double MOVEMENT_THRESHOLD_BLOCKS = 2.0D;

    /** 2乗移動閾値 (4.0) */
    public static final double MOVEMENT_THRESHOLD_SQR = MOVEMENT_THRESHOLD_BLOCKS * MOVEMENT_THRESHOLD_BLOCKS;

    /** 検知グループの拠点範囲 (半径100ブロック) */
    public static final double DETECTOR_GROUP_RADIUS_BLOCKS = 100.0D;

    /** 5分粒度データの保持期間 (90日) */
    public static final int RETENTION_5M_DAYS = 90;

    /** 日次集約データの保持期間 (365日 = 1年) */
    public static final int RETENTION_DAILY_DAYS = 365;

    /** セッション概要データの保持期間 (365日 = 1年) */
    public static final int RETENTION_SESSION_DAYS = 365;

    /** 収集処理の非同期キュー上限 */
    public static final int MAX_QUEUE_CAPACITY = 10_000;

    /** Webダッシュボードサーバーの有効化フラグ */
    public static final boolean WEB_SERVER_ENABLED = true;

    /** Webダッシュボードサーバーのポート番号 */
    public static final int WEB_SERVER_PORT = 8080;
}
