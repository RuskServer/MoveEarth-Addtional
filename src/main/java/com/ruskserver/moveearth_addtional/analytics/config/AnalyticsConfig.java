package com.ruskserver.moveearth_addtional.analytics.config;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

/**
 * プレイヤー分析システムの設定および定数定義
 */
public final class AnalyticsConfig {

    private AnalyticsConfig() {
    }

    /** 設定ファイル名 */
    public static final String CONFIG_FILE_NAME = "moveearth_analytics.properties";

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

    // --- 外部設定項目 (moveearth_analytics.properties で変更可能) ---

    /** 専用サーバー限定動作フラグ (falseにするとシングルプレイでも動作) */
    private static volatile boolean dedicatedServerOnly = true;

    /** Webダッシュボードサーバーの有効化フラグ */
    private static volatile boolean webServerEnabled = true;

    /** Webダッシュボードサーバーのバインドホスト */
    private static volatile String webServerHost = "127.0.0.1";

    /** Webダッシュボードサーバーのポート番号 */
    private static volatile int webServerPort = 8080;

    /** WebダッシュボードAPIの認証必須フラグ */
    private static volatile boolean webServerRequireAuth = true;

    /** 現在有効なWebダッシュボードAPIトークン (volatile) */
    private static volatile String currentAuthToken = UUID.randomUUID().toString().replace("-", "");

    public static boolean isDedicatedServerOnly() {
        return dedicatedServerOnly;
    }

    public static void setDedicatedServerOnly(boolean val) {
        dedicatedServerOnly = val;
    }

    public static boolean isWebServerEnabled() {
        return webServerEnabled;
    }

    public static void setWebServerEnabled(boolean val) {
        webServerEnabled = val;
    }

    public static String getWebServerHost() {
        return webServerHost;
    }

    public static void setWebServerHost(String host) {
        if (host != null && !host.isBlank()) {
            webServerHost = host.trim();
        }
    }

    public static int getWebServerPort() {
        return webServerPort;
    }

    public static void setWebServerPort(int port) {
        if (port >= 1 && port <= 65535) {
            webServerPort = port;
        }
    }

    public static boolean isWebServerRequireAuth() {
        return webServerRequireAuth;
    }

    public static void setWebServerRequireAuth(boolean val) {
        webServerRequireAuth = val;
    }

    public static String getAuthToken() {
        return currentAuthToken;
    }

    public static String regenerateAuthToken() {
        currentAuthToken = UUID.randomUUID().toString().replace("-", "");
        return currentAuthToken;
    }

    /**
     * 設定ファイル（config/moveearth_analytics.properties）をロード。存在しない場合はデフォルト生成。
     */
    public static synchronized void loadConfig(Path configDir) {
        if (configDir == null) {
            return;
        }

        try {
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }

            Path configFile = configDir.resolve(CONFIG_FILE_NAME);
            if (!Files.exists(configFile)) {
                saveConfig(configDir);
                return;
            }

            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(configFile)) {
                props.load(in);
            }

            dedicatedServerOnly = Boolean.parseBoolean(props.getProperty("dedicated_server_only", "true"));
            webServerEnabled = Boolean.parseBoolean(props.getProperty("web_server_enabled", "true"));
            webServerHost = props.getProperty("web_server_host", "127.0.0.1").trim();

            try {
                webServerPort = Integer.parseInt(props.getProperty("web_server_port", "8080").trim());
            } catch (NumberFormatException e) {
                webServerPort = 8080;
            }

            webServerRequireAuth = Boolean.parseBoolean(props.getProperty("web_server_require_auth", "true"));
        } catch (Exception e) {
            System.err.println("[MoveEarth-Analytics] Failed to load config: " + e.getMessage());
        }
    }

    /**
     * 現在の設定値を設定ファイル（config/moveearth_analytics.properties）へ保存
     */
    public static synchronized void saveConfig(Path configDir) {
        if (configDir == null) {
            return;
        }

        try {
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }

            Path configFile = configDir.resolve(CONFIG_FILE_NAME);
            Properties props = new Properties();
            props.setProperty("dedicated_server_only", String.valueOf(dedicatedServerOnly));
            props.setProperty("web_server_enabled", String.valueOf(webServerEnabled));
            props.setProperty("web_server_host", webServerHost);
            props.setProperty("web_server_port", String.valueOf(webServerPort));
            props.setProperty("web_server_require_auth", String.valueOf(webServerRequireAuth));

            try (OutputStream out = Files.newOutputStream(configFile)) {
                props.store(out, "MoveEarth Analytics Configuration");
            }
        } catch (Exception e) {
            System.err.println("[MoveEarth-Analytics] Failed to save config: " + e.getMessage());
        }
    }

    /**
     * テスト環境等での設定リセット
     */
    public static synchronized void resetToDefaults() {
        dedicatedServerOnly = true;
        webServerEnabled = true;
        webServerHost = "127.0.0.1";
        webServerPort = 8080;
        webServerRequireAuth = true;
    }
}
