package com.ruskserver.moveearth_addtional.analytics.config;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
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

    /** 再生成したトークンを即時保存するためのロード済み設定ディレクトリ */
    private static volatile Path loadedConfigDir;

    private static volatile boolean profilerAutoEnabled = true;
    private static volatile double profilerTpsThreshold = 18.0D;
    private static volatile double profilerMsptThreshold = 55.0D;
    private static volatile int profilerConsecutiveSamples = 3;
    private static volatile int profilerDurationSeconds = 10;
    private static volatile int profilerSampleIntervalTicks = 20;
    private static volatile int profilerCandidateChunks = 20;
    private static volatile int profilerCooldownSeconds = 300;

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

    public static boolean isProfilerAutoEnabled() { return profilerAutoEnabled; }
    public static double getProfilerTpsThreshold() { return profilerTpsThreshold; }
    public static double getProfilerMsptThreshold() { return profilerMsptThreshold; }
    public static int getProfilerConsecutiveSamples() { return profilerConsecutiveSamples; }
    public static int getProfilerDurationSeconds() { return profilerDurationSeconds; }
    public static int getProfilerSampleIntervalTicks() { return profilerSampleIntervalTicks; }
    public static int getProfilerCandidateChunks() { return profilerCandidateChunks; }
    public static int getProfilerCooldownSeconds() { return profilerCooldownSeconds; }

    public static synchronized String regenerateAuthToken() {
        currentAuthToken = UUID.randomUUID().toString().replace("-", "");
        if (loadedConfigDir != null) saveConfig(loadedConfigDir);
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
            loadedConfigDir = configDir;
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
            profilerAutoEnabled = Boolean.parseBoolean(props.getProperty("profiler_auto_enabled", "true"));
            profilerTpsThreshold = parseDouble(props, "profiler_tps_threshold", 18.0D, 1.0D, 20.0D);
            profilerMsptThreshold = parseDouble(props, "profiler_mspt_threshold", 55.0D, 1.0D, 1000.0D);
            profilerConsecutiveSamples = parseInt(props, "profiler_consecutive_samples", 3, 1, 60);
            profilerDurationSeconds = parseInt(props, "profiler_duration_seconds", 10, 1, 120);
            profilerSampleIntervalTicks = parseInt(props, "profiler_sample_interval_ticks", 20, 1, 200);
            profilerCandidateChunks = parseInt(props, "profiler_candidate_chunks", 20, 1, 100);
            profilerCooldownSeconds = parseInt(props, "profiler_cooldown_seconds", 300, 0, 86400);
            String configuredToken = props.getProperty("auth_token", "").trim();
            if (isValidToken(configuredToken)) {
                currentAuthToken = configuredToken;
            } else {
                currentAuthToken = UUID.randomUUID().toString().replace("-", "");
                saveConfig(configDir);
            }
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
            props.setProperty("auth_token", currentAuthToken);
            props.setProperty("profiler_auto_enabled", String.valueOf(profilerAutoEnabled));
            props.setProperty("profiler_tps_threshold", String.valueOf(profilerTpsThreshold));
            props.setProperty("profiler_mspt_threshold", String.valueOf(profilerMsptThreshold));
            props.setProperty("profiler_consecutive_samples", String.valueOf(profilerConsecutiveSamples));
            props.setProperty("profiler_duration_seconds", String.valueOf(profilerDurationSeconds));
            props.setProperty("profiler_sample_interval_ticks", String.valueOf(profilerSampleIntervalTicks));
            props.setProperty("profiler_candidate_chunks", String.valueOf(profilerCandidateChunks));
            props.setProperty("profiler_cooldown_seconds", String.valueOf(profilerCooldownSeconds));

            try (OutputStream out = Files.newOutputStream(configFile)) {
                props.store(out, "MoveEarth Analytics Configuration");
            }
            restrictOwnerAccess(configFile);
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
        currentAuthToken = UUID.randomUUID().toString().replace("-", "");
        loadedConfigDir = null;
        profilerAutoEnabled = true;
        profilerTpsThreshold = 18.0D;
        profilerMsptThreshold = 55.0D;
        profilerConsecutiveSamples = 3;
        profilerDurationSeconds = 10;
        profilerSampleIntervalTicks = 20;
        profilerCandidateChunks = 20;
        profilerCooldownSeconds = 300;
    }

    private static boolean isValidToken(String token) {
        return token != null && token.length() >= 16 && token.length() <= 256
                && token.chars().noneMatch(Character::isWhitespace);
    }

    private static void restrictOwnerAccess(Path configFile) {
        try {
            Files.setPosixFilePermissions(configFile, EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Windows and some mounted filesystems do not expose POSIX permissions.
        } catch (Exception exception) {
            System.err.println("[MoveEarth-Analytics] Failed to restrict config permissions: "
                    + exception.getMessage());
        }
    }

    private static int parseInt(Properties props, String key, int fallback, int minimum, int maximum) {
        try {
            return Math.max(minimum, Math.min(maximum, Integer.parseInt(props.getProperty(key,
                    Integer.toString(fallback)).trim())));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double parseDouble(Properties props, String key, double fallback,
                                      double minimum, double maximum) {
        try {
            return Math.max(minimum, Math.min(maximum, Double.parseDouble(props.getProperty(key,
                    Double.toString(fallback)).trim())));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
