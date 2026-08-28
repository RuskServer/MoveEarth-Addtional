package com.ruskserver.moveearth_addtional.analytics.storage;

import com.ruskserver.moveearth_addtional.analytics.model.*;
import com.ruskserver.moveearth_addtional.analytics.queue.AnalyticsEventQueue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.List;

/**
 * SQLite WALモードを用いたプレイヤー分析データの永続化エンジン実装
 */
public class SqliteAnalyticsStorageEngine implements AnalyticsStorageEngine {

    public static final int SCHEMA_VERSION = 1;

    private Connection connection;
    private Path dbFilePath;

    public SqliteAnalyticsStorageEngine() {
    }

    @Override
    public synchronized void initialize(Path dbPath) throws Exception {
        this.dbFilePath = dbPath;
        if (dbPath.getParent() != null) {
            Files.createDirectories(dbPath.getParent());
        }

        String jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        this.connection = DriverManager.getConnection(jdbcUrl);

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA synchronous = NORMAL;");
            stmt.execute("PRAGMA busy_timeout = 5000;");
            stmt.execute("PRAGMA foreign_keys = ON;");
        }

        applySchemaMigrations();
    }

    private void applySchemaMigrations() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // スキーマバージョン管理テーブル
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INTEGER PRIMARY KEY,
                    applied_at INTEGER NOT NULL
                );
            """);

            int currentVersion = 0;
            try (ResultSet rs = stmt.executeQuery("SELECT MAX(version) FROM schema_version")) {
                if (rs.next()) {
                    currentVersion = rs.getInt(1);
                }
            }

            if (currentVersion < 1) {
                // Version 1 スキーマの作成
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS player_identity (
                        player_uuid TEXT PRIMARY KEY,
                        last_known_name TEXT NOT NULL,
                        first_seen_at INTEGER NOT NULL,
                        last_seen_at INTEGER NOT NULL
                    );
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS player_session (
                        session_id TEXT PRIMARY KEY,
                        player_uuid TEXT NOT NULL,
                        last_known_name TEXT NOT NULL,
                        joined_at INTEGER NOT NULL,
                        left_at INTEGER NOT NULL,
                        online_seconds INTEGER NOT NULL,
                        active_seconds INTEGER NOT NULL,
                        afk_seconds INTEGER NOT NULL
                    );
                """);
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_session_player ON player_session(player_uuid);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_session_joined ON player_session(joined_at);");

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS player_activity_5m (
                        bucket_at INTEGER NOT NULL,
                        player_uuid TEXT NOT NULL,
                        dimension TEXT NOT NULL,
                        group_owner_uuid TEXT,
                        active_seconds INTEGER NOT NULL,
                        distance_blocks REAL NOT NULL,
                        breaks INTEGER NOT NULL,
                        places INTEGER NOT NULL,
                        crafts INTEGER NOT NULL,
                        pve_kills INTEGER NOT NULL,
                        pvp_kills INTEGER NOT NULL,
                        deaths INTEGER NOT NULL,
                        jobs_xp REAL NOT NULL,
                        tpa_successes INTEGER NOT NULL,
                        PRIMARY KEY (bucket_at, player_uuid, dimension)
                    );
                """);
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_activity_player ON player_activity_5m(player_uuid, bucket_at);");

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS spatial_activity_5m (
                        bucket_at INTEGER NOT NULL,
                        dimension TEXT NOT NULL,
                        cell_x INTEGER NOT NULL,
                        cell_z INTEGER NOT NULL,
                        y_band TEXT NOT NULL,
                        group_owner_uuid TEXT,
                        relation TEXT NOT NULL,
                        active_samples INTEGER NOT NULL,
                        unique_players INTEGER NOT NULL,
                        PRIMARY KEY (bucket_at, dimension, cell_x, cell_z, y_band, relation)
                    );
                """);
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_spatial_bucket ON spatial_activity_5m(bucket_at, dimension);");

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS detector_activity_5m (
                        bucket_at INTEGER NOT NULL,
                        dimension TEXT NOT NULL,
                        detector_pos_hash TEXT NOT NULL,
                        group_owner_uuid TEXT,
                        member_minutes REAL NOT NULL,
                        visitor_minutes REAL NOT NULL,
                        intrusion_sessions INTEGER NOT NULL,
                        distinct_members INTEGER NOT NULL,
                        distinct_visitors INTEGER NOT NULL,
                        PRIMARY KEY (bucket_at, dimension, detector_pos_hash)
                    );
                """);
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_detector_bucket ON detector_activity_5m(bucket_at, dimension);");

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS player_activity_daily (
                        date_epoch_day INTEGER NOT NULL,
                        player_uuid TEXT NOT NULL,
                        dimension TEXT NOT NULL,
                        group_owner_uuid TEXT,
                        active_seconds INTEGER NOT NULL,
                        distance_blocks REAL NOT NULL,
                        breaks INTEGER NOT NULL,
                        places INTEGER NOT NULL,
                        crafts INTEGER NOT NULL,
                        pve_kills INTEGER NOT NULL,
                        pvp_kills INTEGER NOT NULL,
                        deaths INTEGER NOT NULL,
                        jobs_xp REAL NOT NULL,
                        tpa_successes INTEGER NOT NULL,
                        PRIMARY KEY (date_epoch_day, player_uuid, dimension)
                    );
                """);
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_activity_daily_player ON player_activity_daily(player_uuid, date_epoch_day);");

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS collector_health (
                        recorded_at INTEGER NOT NULL,
                        queue_depth INTEGER NOT NULL,
                        dropped_events INTEGER NOT NULL,
                        flush_ms INTEGER NOT NULL,
                        db_bytes INTEGER NOT NULL
                    );
                """);
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_health_recorded ON collector_health(recorded_at);");

                stmt.execute("INSERT OR REPLACE INTO schema_version (version, applied_at) VALUES (1, " + (System.currentTimeMillis() / 1000L) + ");");
            }
        }
    }

    @Override
    public synchronized void writeBatch(List<AnalyticsEventQueue.AnalyticsEvent> events) throws Exception {
        if (events == null || events.isEmpty() || connection == null || connection.isClosed()) {
            return;
        }

        boolean autoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);

            for (AnalyticsEventQueue.AnalyticsEvent event : events) {
                if (event instanceof AnalyticsEventQueue.SessionStartEvent e) {
                    writeSessionStart(e);
                } else if (event instanceof AnalyticsEventQueue.SessionEndEvent e) {
                    writeSessionEnd(e);
                } else if (event instanceof AnalyticsEventQueue.PlayerActivityFlushEvent e) {
                    writePlayerActivity(e.records());
                } else if (event instanceof AnalyticsEventQueue.SpatialActivityFlushEvent e) {
                    writeSpatialActivity(e.records());
                } else if (event instanceof AnalyticsEventQueue.DetectorActivityFlushEvent e) {
                    writeDetectorActivity(e.records());
                } else if (event instanceof AnalyticsEventQueue.HealthMetricEvent e) {
                    writeHealthMetric(e);
                }
            }

            connection.commit();
        } catch (Exception ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private void writeSessionStart(AnalyticsEventQueue.SessionStartEvent event) throws SQLException {
        String sql = """
            INSERT INTO player_identity (player_uuid, last_known_name, first_seen_at, last_seen_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET
                last_known_name = excluded.last_known_name,
                last_seen_at = excluded.last_seen_at;
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, event.playerUuid().toString());
            ps.setString(2, event.playerName());
            ps.setLong(3, event.joinedAtEpochSec());
            ps.setLong(4, event.joinedAtEpochSec());
            ps.executeUpdate();
        }
    }

    private void writeSessionEnd(AnalyticsEventQueue.SessionEndEvent event) throws SQLException {
        SessionRecord r = event.sessionRecord();
        String sql = """
            INSERT OR REPLACE INTO player_session (
                session_id, player_uuid, last_known_name, joined_at, left_at,
                online_seconds, active_seconds, afk_seconds
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?);
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, r.sessionId().toString());
            ps.setString(2, r.playerUuid().toString());
            ps.setString(3, r.lastKnownName());
            ps.setLong(4, r.joinedAtEpochSec());
            ps.setLong(5, r.leftAtEpochSec());
            ps.setInt(6, r.onlineSeconds());
            ps.setInt(7, r.activeSeconds());
            ps.setInt(8, r.afkSeconds());
            ps.executeUpdate();
        }

        // identityの最終確認時刻を更新
        String updateIdentity = """
            UPDATE player_identity SET last_seen_at = ? WHERE player_uuid = ?;
        """;
        try (PreparedStatement ps = connection.prepareStatement(updateIdentity)) {
            ps.setLong(1, r.leftAtEpochSec());
            ps.setString(2, r.playerUuid().toString());
            ps.executeUpdate();
        }
    }

    private void writePlayerActivity(List<PlayerActivityBucket> records) throws SQLException {
        if (records == null || records.isEmpty()) return;

        String sql = """
            INSERT INTO player_activity_5m (
                bucket_at, player_uuid, dimension, group_owner_uuid,
                active_seconds, distance_blocks, breaks, places, crafts,
                pve_kills, pvp_kills, deaths, jobs_xp, tpa_successes
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(bucket_at, player_uuid, dimension) DO UPDATE SET
                active_seconds = active_seconds + excluded.active_seconds,
                distance_blocks = distance_blocks + excluded.distance_blocks,
                breaks = breaks + excluded.breaks,
                places = places + excluded.places,
                crafts = crafts + excluded.crafts,
                pve_kills = pve_kills + excluded.pve_kills,
                pvp_kills = pvp_kills + excluded.pvp_kills,
                deaths = deaths + excluded.deaths,
                jobs_xp = jobs_xp + excluded.jobs_xp,
                tpa_successes = tpa_successes + excluded.tpa_successes;
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (PlayerActivityBucket r : records) {
                ps.setLong(1, r.bucketAtEpochSec());
                ps.setString(2, r.playerUuid().toString());
                ps.setString(3, r.dimension());
                ps.setString(4, r.groupOwnerUuid() != null ? r.groupOwnerUuid().toString() : null);
                ps.setInt(5, r.activeSeconds());
                ps.setDouble(6, r.distanceBlocks());
                ps.setInt(7, r.breaks());
                ps.setInt(8, r.places());
                ps.setInt(9, r.crafts());
                ps.setInt(10, r.pveKills());
                ps.setInt(11, r.pvpKills());
                ps.setInt(12, r.deaths());
                ps.setDouble(13, r.jobsXp());
                ps.setInt(14, r.tpaSuccesses());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void writeSpatialActivity(List<SpatialActivityBucket> records) throws SQLException {
        if (records == null || records.isEmpty()) return;

        String sql = """
            INSERT INTO spatial_activity_5m (
                bucket_at, dimension, cell_x, cell_z, y_band,
                group_owner_uuid, relation, active_samples, unique_players
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(bucket_at, dimension, cell_x, cell_z, y_band, relation) DO UPDATE SET
                active_samples = active_samples + excluded.active_samples,
                unique_players = MAX(unique_players, excluded.unique_players);
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (SpatialActivityBucket r : records) {
                ps.setLong(1, r.bucketAtEpochSec());
                ps.setString(2, r.dimension());
                ps.setInt(3, r.cellX());
                ps.setInt(4, r.cellZ());
                ps.setString(5, r.yBand().getId());
                ps.setString(6, r.groupOwnerUuid() != null ? r.groupOwnerUuid().toString() : null);
                ps.setString(7, r.relation().getId());
                ps.setInt(8, r.activeSamples());
                ps.setInt(9, r.uniquePlayers());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void writeDetectorActivity(List<DetectorActivityBucket> records) throws SQLException {
        if (records == null || records.isEmpty()) return;

        String sql = """
            INSERT INTO detector_activity_5m (
                bucket_at, dimension, detector_pos_hash, group_owner_uuid,
                member_minutes, visitor_minutes, intrusion_sessions,
                distinct_members, distinct_visitors
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(bucket_at, dimension, detector_pos_hash) DO UPDATE SET
                member_minutes = member_minutes + excluded.member_minutes,
                visitor_minutes = visitor_minutes + excluded.visitor_minutes,
                intrusion_sessions = intrusion_sessions + excluded.intrusion_sessions,
                distinct_members = MAX(distinct_members, excluded.distinct_members),
                distinct_visitors = MAX(distinct_visitors, excluded.distinct_visitors);
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (DetectorActivityBucket r : records) {
                ps.setLong(1, r.bucketAtEpochSec());
                ps.setString(2, r.dimension());
                ps.setString(3, r.detectorPosHash());
                ps.setString(4, r.groupOwnerUuid() != null ? r.groupOwnerUuid().toString() : null);
                ps.setDouble(5, r.memberMinutes());
                ps.setDouble(6, r.visitorMinutes());
                ps.setInt(7, r.intrusionSessions());
                ps.setInt(8, r.distinctMembers());
                ps.setInt(9, r.distinctVisitors());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void writeHealthMetric(AnalyticsEventQueue.HealthMetricEvent event) throws SQLException {
        String sql = """
            INSERT INTO collector_health (recorded_at, queue_depth, dropped_events, flush_ms, db_bytes)
            VALUES (?, ?, ?, ?, ?);
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, event.recordedAtEpochSec());
            ps.setInt(2, event.queueDepth());
            ps.setLong(3, event.droppedEvents());
            ps.setLong(4, event.flushMs());
            ps.setLong(5, event.dbBytes());
            ps.executeUpdate();
        }
    }

    @Override
    public synchronized void aggregateDaily(long beforeEpochSec) throws SQLException {
        if (connection == null || connection.isClosed()) return;

        String sql = """
            INSERT INTO player_activity_daily (
                date_epoch_day, player_uuid, dimension, group_owner_uuid,
                active_seconds, distance_blocks, breaks, places, crafts,
                pve_kills, pvp_kills, deaths, jobs_xp, tpa_successes
            )
            SELECT
                (bucket_at / 86400) AS date_epoch_day,
                player_uuid,
                dimension,
                group_owner_uuid,
                SUM(active_seconds),
                SUM(distance_blocks),
                SUM(breaks),
                SUM(places),
                SUM(crafts),
                SUM(pve_kills),
                SUM(pvp_kills),
                SUM(deaths),
                SUM(jobs_xp),
                SUM(tpa_successes)
            FROM player_activity_5m
            WHERE bucket_at < ?
            GROUP BY date_epoch_day, player_uuid, dimension
            ON CONFLICT(date_epoch_day, player_uuid, dimension) DO UPDATE SET
                active_seconds = excluded.active_seconds,
                distance_blocks = excluded.distance_blocks,
                breaks = excluded.breaks,
                places = excluded.places,
                crafts = excluded.crafts,
                pve_kills = excluded.pve_kills,
                pvp_kills = excluded.pvp_kills,
                deaths = excluded.deaths,
                jobs_xp = excluded.jobs_xp,
                tpa_successes = excluded.tpa_successes;
        """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, beforeEpochSec);
            ps.executeUpdate();
        }
    }

    @Override
    public synchronized void purgeOldRecords(long cutoff5mEpochSec, long cutoffDailyEpochSec, long cutoffSessionEpochSec) throws SQLException {
        if (connection == null || connection.isClosed()) return;

        boolean autoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);

            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM player_activity_5m WHERE bucket_at < ?")) {
                ps.setLong(1, cutoff5mEpochSec);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM spatial_activity_5m WHERE bucket_at < ?")) {
                ps.setLong(1, cutoff5mEpochSec);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM detector_activity_5m WHERE bucket_at < ?")) {
                ps.setLong(1, cutoff5mEpochSec);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM player_activity_daily WHERE date_epoch_day < ?")) {
                ps.setLong(1, cutoffDailyEpochSec / 86400);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM player_session WHERE left_at < ?")) {
                ps.setLong(1, cutoffSessionEpochSec);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM collector_health WHERE recorded_at < ?")) {
                ps.setLong(1, cutoff5mEpochSec);
                ps.executeUpdate();
            }

            connection.commit();
        } catch (Exception e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    @Override
    public long getDatabaseSizeBytes() {
        if (dbFilePath != null) {
            File f = dbFilePath.toFile();
            if (f.exists()) {
                return f.length();
            }
        }
        return 0L;
    }

    @Override
    public boolean isOpen() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public synchronized void close() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
