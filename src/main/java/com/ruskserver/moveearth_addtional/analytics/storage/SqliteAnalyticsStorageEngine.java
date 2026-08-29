package com.ruskserver.moveearth_addtional.analytics.storage;

import com.ruskserver.moveearth_addtional.analytics.model.*;
import com.ruskserver.moveearth_addtional.analytics.query.dto.*;
import com.ruskserver.moveearth_addtional.analytics.queue.AnalyticsEventQueue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/**
 * SQLite WALモードによるプレイヤー分析データストレージエンジン
 */
public class SqliteAnalyticsStorageEngine implements AnalyticsStorageEngine {

    private Path dbPath;
    private Connection connection;

    public SqliteAnalyticsStorageEngine() {
    }

    @Override
    public synchronized void initialize(Path dbPath) throws SQLException {
        this.dbPath = dbPath;
        try {
            if (dbPath.getParent() != null) {
                Files.createDirectories(dbPath.getParent());
            }
        } catch (Exception e) {
            throw new SQLException("Failed to create parent directories for analytics database", e);
        }

        String jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        this.connection = DriverManager.getConnection(jdbcUrl);

        try (Statement stmt = connection.createStatement()) {
            // WALモードおよび高スループット設定
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA synchronous = NORMAL;");
            stmt.execute("PRAGMA busy_timeout = 5000;");

            // スキーマ初期化および動的カラム検知・安全修復マイグレーション
            checkAndMigrateSchema(stmt);
        }
    }

    private boolean hasColumn(Statement stmt, String tableName, String columnName) {
        try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ");")) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        } catch (SQLException ignored) {
        }
        return false;
    }

    private int getPkColumnCount(Statement stmt, String tableName) {
        int count = 0;
        try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ");")) {
            while (rs.next()) {
                if (rs.getInt("pk") > 0) {
                    count++;
                }
            }
        } catch (SQLException ignored) {
        }
        return count;
    }

    private boolean tableExists(Statement stmt, String tableName) {
        try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + tableName + "';")) {
            return rs.next();
        } catch (SQLException ignored) {
            return false;
        }
    }

    private void applySchemaBase(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS player_identity (
                player_uuid TEXT PRIMARY KEY,
                last_known_name TEXT NOT NULL,
                first_seen_at INTEGER NOT NULL,
                last_seen_at INTEGER NOT NULL
            );
        """);
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_identity_name ON player_identity(last_known_name);");

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
                group_owner_uuid TEXT NOT NULL DEFAULT '',
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
                PRIMARY KEY (bucket_at, player_uuid, dimension, group_owner_uuid)
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
                group_owner_uuid TEXT NOT NULL DEFAULT '',
                relation TEXT NOT NULL,
                active_samples INTEGER NOT NULL,
                unique_players INTEGER NOT NULL,
                PRIMARY KEY (bucket_at, dimension, cell_x, cell_z, y_band, group_owner_uuid, relation)
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
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_detector_group ON detector_activity_5m(group_owner_uuid, bucket_at);");

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS player_activity_daily (
                date_epoch_day INTEGER NOT NULL,
                player_uuid TEXT NOT NULL,
                dimension TEXT NOT NULL,
                group_owner_uuid TEXT NOT NULL DEFAULT '',
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
                PRIMARY KEY (date_epoch_day, player_uuid, dimension, group_owner_uuid)
            );
        """);
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_daily_player ON player_activity_daily(player_uuid, date_epoch_day);");

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS collector_health (
                recorded_at INTEGER NOT NULL,
                queue_depth INTEGER NOT NULL,
                dropped_events INTEGER NOT NULL,
                last_flush_duration_ms INTEGER NOT NULL,
                database_size_bytes INTEGER NOT NULL,
                PRIMARY KEY (recorded_at)
            );
        """);
    }

    private void checkAndMigrateSchema(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS schema_version (
                version INTEGER PRIMARY KEY,
                applied_at INTEGER NOT NULL
            );
        """);

        applySchemaBase(stmt);

        // 1. player_activity_5m の点検・再構築
        if (tableExists(stmt, "player_activity_5m")) {
            if (getPkColumnCount(stmt, "player_activity_5m") < 4 || !hasColumn(stmt, "player_activity_5m", "group_owner_uuid")) {
                boolean hasGroup = hasColumn(stmt, "player_activity_5m", "group_owner_uuid");
                stmt.execute("ALTER TABLE player_activity_5m RENAME TO _old_player_activity_5m;");
                stmt.execute("""
                    CREATE TABLE player_activity_5m (
                        bucket_at INTEGER NOT NULL,
                        player_uuid TEXT NOT NULL,
                        dimension TEXT NOT NULL,
                        group_owner_uuid TEXT NOT NULL DEFAULT '',
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
                        PRIMARY KEY (bucket_at, player_uuid, dimension, group_owner_uuid)
                    );
                """);
                String groupExpr = hasGroup ? "COALESCE(group_owner_uuid, '')" : "''";
                stmt.execute("INSERT OR IGNORE INTO player_activity_5m (bucket_at, player_uuid, dimension, group_owner_uuid, active_seconds, distance_blocks, breaks, places, crafts, pve_kills, pvp_kills, deaths, jobs_xp, tpa_successes) SELECT bucket_at, player_uuid, dimension, " + groupExpr + ", active_seconds, distance_blocks, breaks, places, crafts, pve_kills, pvp_kills, deaths, jobs_xp, tpa_successes FROM _old_player_activity_5m;");
                stmt.execute("DROP TABLE _old_player_activity_5m;");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_activity_player ON player_activity_5m(player_uuid, bucket_at);");
            }
        }

        // 2. spatial_activity_5m の点検・再構築
        if (tableExists(stmt, "spatial_activity_5m")) {
            if (getPkColumnCount(stmt, "spatial_activity_5m") < 7 || !hasColumn(stmt, "spatial_activity_5m", "group_owner_uuid")) {
                boolean hasGroup = hasColumn(stmt, "spatial_activity_5m", "group_owner_uuid");
                stmt.execute("ALTER TABLE spatial_activity_5m RENAME TO _old_spatial_activity_5m;");
                stmt.execute("""
                    CREATE TABLE spatial_activity_5m (
                        bucket_at INTEGER NOT NULL,
                        dimension TEXT NOT NULL,
                        cell_x INTEGER NOT NULL,
                        cell_z INTEGER NOT NULL,
                        y_band TEXT NOT NULL,
                        group_owner_uuid TEXT NOT NULL DEFAULT '',
                        relation TEXT NOT NULL,
                        active_samples INTEGER NOT NULL,
                        unique_players INTEGER NOT NULL,
                        PRIMARY KEY (bucket_at, dimension, cell_x, cell_z, y_band, group_owner_uuid, relation)
                    );
                """);
                String groupExpr = hasGroup ? "COALESCE(group_owner_uuid, '')" : "''";
                stmt.execute("INSERT OR IGNORE INTO spatial_activity_5m (bucket_at, dimension, cell_x, cell_z, y_band, group_owner_uuid, relation, active_samples, unique_players) SELECT bucket_at, dimension, cell_x, cell_z, y_band, " + groupExpr + ", relation, active_samples, unique_players FROM _old_spatial_activity_5m;");
                stmt.execute("DROP TABLE _old_spatial_activity_5m;");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_spatial_bucket ON spatial_activity_5m(bucket_at, dimension);");
            }
        }

        // 3. player_activity_daily の点検・再構築
        if (tableExists(stmt, "player_activity_daily")) {
            if (getPkColumnCount(stmt, "player_activity_daily") < 4 || !hasColumn(stmt, "player_activity_daily", "group_owner_uuid")) {
                boolean hasGroup = hasColumn(stmt, "player_activity_daily", "group_owner_uuid");
                stmt.execute("ALTER TABLE player_activity_daily RENAME TO _old_player_activity_daily;");
                stmt.execute("""
                    CREATE TABLE player_activity_daily (
                        date_epoch_day INTEGER NOT NULL,
                        player_uuid TEXT NOT NULL,
                        dimension TEXT NOT NULL,
                        group_owner_uuid TEXT NOT NULL DEFAULT '',
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
                        PRIMARY KEY (date_epoch_day, player_uuid, dimension, group_owner_uuid)
                    );
                """);
                String groupExpr = hasGroup ? "COALESCE(group_owner_uuid, '')" : "''";
                stmt.execute("INSERT OR IGNORE INTO player_activity_daily (date_epoch_day, player_uuid, dimension, group_owner_uuid, active_seconds, distance_blocks, breaks, places, crafts, pve_kills, pvp_kills, deaths, jobs_xp, tpa_successes) SELECT date_epoch_day, player_uuid, dimension, " + groupExpr + ", active_seconds, distance_blocks, breaks, places, crafts, pve_kills, pvp_kills, deaths, jobs_xp, tpa_successes FROM _old_player_activity_daily;");
                stmt.execute("DROP TABLE _old_player_activity_daily;");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_daily_player ON player_activity_daily(player_uuid, date_epoch_day);");
            }
        }

        // 4. collector_health の点検・再構築
        if (tableExists(stmt, "collector_health")) {
            if (!hasColumn(stmt, "collector_health", "last_flush_duration_ms")) {
                boolean hasFlushMs = hasColumn(stmt, "collector_health", "flush_ms");
                stmt.execute("ALTER TABLE collector_health RENAME TO _old_collector_health;");
                stmt.execute("""
                    CREATE TABLE collector_health (
                        recorded_at INTEGER NOT NULL,
                        queue_depth INTEGER NOT NULL,
                        dropped_events INTEGER NOT NULL,
                        last_flush_duration_ms INTEGER NOT NULL,
                        database_size_bytes INTEGER NOT NULL,
                        PRIMARY KEY (recorded_at)
                    );
                """);
                if (hasFlushMs) {
                    stmt.execute("INSERT OR IGNORE INTO collector_health (recorded_at, queue_depth, dropped_events, last_flush_duration_ms, database_size_bytes) SELECT recorded_at, queue_depth, dropped_events, flush_ms, db_bytes FROM _old_collector_health;");
                }
                stmt.execute("DROP TABLE _old_collector_health;");
            }
        }

        stmt.execute("INSERT OR REPLACE INTO schema_version (version, applied_at) VALUES (3, " + (System.currentTimeMillis() / 1000L) + ");");
    }

    @Override
    public synchronized void writeBatch(List<AnalyticsEventQueue.AnalyticsEvent> events) throws SQLException {
        if (connection == null || connection.isClosed() || events.isEmpty()) {
            return;
        }

        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);

        String sqlIdentityUpsert = """
            INSERT INTO player_identity (player_uuid, last_known_name, first_seen_at, last_seen_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET
                last_known_name = excluded.last_known_name,
                last_seen_at = excluded.last_seen_at;
        """;

        String sqlSessionInsert = """
            INSERT OR REPLACE INTO player_session (
                session_id, player_uuid, last_known_name, joined_at, left_at,
                online_seconds, active_seconds, afk_seconds
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?);
        """;

        String sqlPlayerActivity = """
            INSERT INTO player_activity_5m (
                bucket_at, player_uuid, dimension, group_owner_uuid, active_seconds, distance_blocks,
                breaks, places, crafts, pve_kills, pvp_kills, deaths, jobs_xp, tpa_successes
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(bucket_at, player_uuid, dimension, group_owner_uuid) DO UPDATE SET
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

        String sqlSpatialActivity = """
            INSERT INTO spatial_activity_5m (
                bucket_at, dimension, cell_x, cell_z, y_band, group_owner_uuid, relation, active_samples, unique_players
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(bucket_at, dimension, cell_x, cell_z, y_band, group_owner_uuid, relation) DO UPDATE SET
                active_samples = active_samples + excluded.active_samples,
                unique_players = MAX(unique_players, excluded.unique_players);
        """;

        String sqlDetectorActivity = """
            INSERT INTO detector_activity_5m (
                bucket_at, dimension, detector_pos_hash, group_owner_uuid,
                member_minutes, visitor_minutes, intrusion_sessions, distinct_members, distinct_visitors
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(bucket_at, dimension, detector_pos_hash) DO UPDATE SET
                member_minutes = member_minutes + excluded.member_minutes,
                visitor_minutes = visitor_minutes + excluded.visitor_minutes,
                intrusion_sessions = intrusion_sessions + excluded.intrusion_sessions,
                distinct_members = MAX(distinct_members, excluded.distinct_members),
                distinct_visitors = MAX(distinct_visitors, excluded.distinct_visitors);
        """;

        String sqlHealthMetric = """
            INSERT OR REPLACE INTO collector_health (
                recorded_at, queue_depth, dropped_events, last_flush_duration_ms, database_size_bytes
            ) VALUES (?, ?, ?, ?, ?);
        """;

        try (PreparedStatement psIdentity = connection.prepareStatement(sqlIdentityUpsert);
             PreparedStatement psSession = connection.prepareStatement(sqlSessionInsert);
             PreparedStatement psPlayer = connection.prepareStatement(sqlPlayerActivity);
             PreparedStatement psSpatial = connection.prepareStatement(sqlSpatialActivity);
             PreparedStatement psDetector = connection.prepareStatement(sqlDetectorActivity);
             PreparedStatement psHealth = connection.prepareStatement(sqlHealthMetric)) {

            for (AnalyticsEventQueue.AnalyticsEvent event : events) {
                if (event instanceof AnalyticsEventQueue.SessionStartEvent s) {
                    psIdentity.setString(1, s.playerUuid().toString());
                    psIdentity.setString(2, s.playerName());
                    psIdentity.setLong(3, s.joinedAtEpochSec());
                    psIdentity.setLong(4, s.joinedAtEpochSec());
                    psIdentity.addBatch();
                } else if (event instanceof AnalyticsEventQueue.SessionEndEvent e) {
                    SessionRecord r = e.sessionRecord();
                    psIdentity.setString(1, r.playerUuid().toString());
                    psIdentity.setString(2, r.lastKnownName());
                    psIdentity.setLong(3, r.joinedAtEpochSec());
                    psIdentity.setLong(4, r.leftAtEpochSec());
                    psIdentity.addBatch();

                    psSession.setString(1, r.sessionId().toString());
                    psSession.setString(2, r.playerUuid().toString());
                    psSession.setString(3, r.lastKnownName());
                    psSession.setLong(4, r.joinedAtEpochSec());
                    psSession.setLong(5, r.leftAtEpochSec());
                    psSession.setInt(6, r.onlineSeconds());
                    psSession.setInt(7, r.activeSeconds());
                    psSession.setInt(8, r.afkSeconds());
                    psSession.addBatch();
                } else if (event instanceof AnalyticsEventQueue.PlayerActivityFlushEvent f) {
                    for (PlayerActivityBucket b : f.records()) {
                        psPlayer.setLong(1, b.bucketAtEpochSec());
                        psPlayer.setString(2, b.playerUuid().toString());
                        psPlayer.setString(3, b.dimension());
                        psPlayer.setString(4, b.groupOwnerUuid() != null ? b.groupOwnerUuid().toString() : "");
                        psPlayer.setInt(5, b.activeSeconds());
                        psPlayer.setDouble(6, b.distanceBlocks());
                        psPlayer.setInt(7, b.breaks());
                        psPlayer.setInt(8, b.places());
                        psPlayer.setInt(9, b.crafts());
                        psPlayer.setInt(10, b.pveKills());
                        psPlayer.setInt(11, b.pvpKills());
                        psPlayer.setInt(12, b.deaths());
                        psPlayer.setDouble(13, b.jobsXp());
                        psPlayer.setInt(14, b.tpaSuccesses());
                        psPlayer.addBatch();
                    }
                } else if (event instanceof AnalyticsEventQueue.SpatialActivityFlushEvent f) {
                    for (SpatialActivityBucket b : f.records()) {
                        psSpatial.setLong(1, b.bucketAtEpochSec());
                        psSpatial.setString(2, b.dimension());
                        psSpatial.setInt(3, b.cellX());
                        psSpatial.setInt(4, b.cellZ());
                        psSpatial.setString(5, b.yBand().name());
                        psSpatial.setString(6, b.groupOwnerUuid() != null ? b.groupOwnerUuid().toString() : "");
                        psSpatial.setString(7, b.relation().name());
                        psSpatial.setInt(8, b.activeSamples());
                        psSpatial.setInt(9, b.uniquePlayers());
                        psSpatial.addBatch();
                    }
                } else if (event instanceof AnalyticsEventQueue.DetectorActivityFlushEvent f) {
                    for (DetectorActivityBucket b : f.records()) {
                        psDetector.setLong(1, b.bucketAtEpochSec());
                        psDetector.setString(2, b.dimension());
                        psDetector.setString(3, b.detectorPosHash());
                        psDetector.setString(4, b.groupOwnerUuid() != null ? b.groupOwnerUuid().toString() : null);
                        psDetector.setDouble(5, b.memberMinutes());
                        psDetector.setDouble(6, b.visitorMinutes());
                        psDetector.setInt(7, b.intrusionSessions());
                        psDetector.setInt(8, b.distinctMembers());
                        psDetector.setInt(9, b.distinctVisitors());
                        psDetector.addBatch();
                    }
                } else if (event instanceof AnalyticsEventQueue.HealthMetricEvent h) {
                    psHealth.setLong(1, h.recordedAtEpochSec());
                    psHealth.setInt(2, h.queueDepth());
                    psHealth.setLong(3, h.droppedEvents());
                    psHealth.setLong(4, h.flushMs());
                    psHealth.setLong(5, h.dbBytes());
                    psHealth.addBatch();
                }
            }

            psIdentity.executeBatch();
            psSession.executeBatch();
            psPlayer.executeBatch();
            psSpatial.executeBatch();
            psDetector.executeBatch();
            psHealth.executeBatch();

            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    @Override
    public synchronized void aggregateDaily(long beforeEpochSec) throws SQLException {
        if (connection == null || connection.isClosed()) return;

        String sql = """
            INSERT INTO player_activity_daily (
                date_epoch_day, player_uuid, dimension, group_owner_uuid, active_seconds, distance_blocks,
                breaks, places, crafts, pve_kills, pvp_kills, deaths, jobs_xp, tpa_successes
            )
            SELECT
                ((bucket_at - 36000) / 86400) AS date_epoch_day,
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
            GROUP BY ((bucket_at - 36000) / 86400), player_uuid, dimension, group_owner_uuid
            ON CONFLICT(date_epoch_day, player_uuid, dimension, group_owner_uuid) DO UPDATE SET
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

        if (cutoff5mEpochSec > 0) {
            try (PreparedStatement ps1 = connection.prepareStatement("DELETE FROM player_activity_5m WHERE bucket_at < ?");
                 PreparedStatement ps2 = connection.prepareStatement("DELETE FROM spatial_activity_5m WHERE bucket_at < ?");
                 PreparedStatement ps3 = connection.prepareStatement("DELETE FROM detector_activity_5m WHERE bucket_at < ?")) {
                ps1.setLong(1, cutoff5mEpochSec);
                ps1.executeUpdate();
                ps2.setLong(1, cutoff5mEpochSec);
                ps2.executeUpdate();
                ps3.setLong(1, cutoff5mEpochSec);
                ps3.executeUpdate();
            }
        }

        if (cutoffDailyEpochSec > 0) {
            long cutoffDay = (cutoffDailyEpochSec - 36000L) / 86400L;
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM player_activity_daily WHERE date_epoch_day < ?")) {
                ps.setLong(1, cutoffDay);
                ps.executeUpdate();
            }
        }

        if (cutoffSessionEpochSec > 0) {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM player_session WHERE left_at < ?")) {
                ps.setLong(1, cutoffSessionEpochSec);
                ps.executeUpdate();
            }
        }
    }

    @Override
    public synchronized Optional<PlayerSummaryDto> queryPlayerSummary(UUID playerUuid, TimeWindow window, long currentEpochSec) throws SQLException {
        if (connection == null || connection.isClosed()) return Optional.empty();

        long startEpochSec = window.getStartEpochSec(currentEpochSec);
        long todayDay = currentEpochSec / 86400L;
        long todayStartEpochSec = todayDay * 86400L;
        long startDay = startEpochSec / 86400L;

        // 1. 名前、初回参加時刻、最終ログイン
        String lastKnownName = "Unknown";
        long firstSeenAt = 0L;
        long lastSeenAt = 0L;
        String sqlIdentity = "SELECT first_seen_at, last_known_name, last_seen_at FROM player_identity WHERE player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sqlIdentity)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    firstSeenAt = rs.getLong("first_seen_at");
                    lastKnownName = rs.getString("last_known_name");
                    lastSeenAt = rs.getLong("last_seen_at");
                }
            }
        }

        // 2. セッション統計 (player_session から取得 + 進行中アクティブセッション合算)
        int sessionCount = 0;
        int sessionOnlineSec = 0;
        int sessionAfkSec = 0;
        String sqlSession = """
            SELECT COUNT(*) as session_count,
                   COALESCE(SUM(online_seconds), 0) as total_online,
                   COALESCE(SUM(afk_seconds), 0) as total_afk
            FROM player_session
            WHERE player_uuid = ? AND joined_at >= ?
        """;
        try (PreparedStatement ps = connection.prepareStatement(sqlSession)) {
            ps.setString(1, playerUuid.toString());
            ps.setLong(2, startEpochSec);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    sessionCount = rs.getInt("session_count");
                    sessionOnlineSec = rs.getInt("total_online");
                    sessionAfkSec = rs.getInt("total_afk");
                }
            }
        }

        // 進行中のアクティブセッションがあればリアルタイム合算
        var activeSession = com.ruskserver.moveearth_addtional.analytics.tracker.SessionTracker.INSTANCE.getSession(playerUuid);
        if (activeSession != null) {
            sessionCount++;
            sessionOnlineSec += activeSession.getOnlineSeconds(currentEpochSec * 1000L);
            sessionAfkSec += activeSession.getAfkSeconds();
            if (lastSeenAt < currentEpochSec) {
                lastSeenAt = currentEpochSec;
            }
        }

        // 3. ハイブリッド活動統計 (player_activity_5m + player_activity_daily)
        long min5mEpoch = currentEpochSec;
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery("SELECT COALESCE(MIN(bucket_at), " + currentEpochSec + ") FROM player_activity_5m")) {
            if (rs.next()) {
                min5mEpoch = rs.getLong(1);
            }
        }
        long min5mDay = (min5mEpoch - 36000L) / 86400L;
        long startOpenDay = (startEpochSec - 36000L) / 86400L;

        String sqlActivityHybrid = """
            SELECT
                COALESCE(SUM(active_seconds), 0) as total_active,
                COALESCE(SUM(distance_blocks), 0.0) as total_distance,
                COALESCE(SUM(breaks), 0) as total_breaks,
                COALESCE(SUM(places), 0) as total_places,
                COALESCE(SUM(crafts), 0) as total_crafts,
                COALESCE(SUM(pve_kills), 0) as total_pve,
                COALESCE(SUM(pvp_kills), 0) as total_pvp,
                COALESCE(SUM(deaths), 0) as total_deaths,
                COALESCE(SUM(jobs_xp), 0.0) as total_jobs,
                COALESCE(SUM(tpa_successes), 0) as total_tpa
            FROM (
                SELECT active_seconds, distance_blocks, breaks, places, crafts, pve_kills, pvp_kills, deaths, jobs_xp, tpa_successes
                FROM player_activity_5m
                WHERE player_uuid = ? AND bucket_at >= ?
                UNION ALL
                SELECT active_seconds, distance_blocks, breaks, places, crafts, pve_kills, pvp_kills, deaths, jobs_xp, tpa_successes
                FROM player_activity_daily
                WHERE player_uuid = ? AND date_epoch_day >= ? AND date_epoch_day < ?
            )
        """;

        int totalActiveSec = 0;
        double totalDistance = 0.0;
        int totalBreaks = 0;
        int totalPlaces = 0;
        int totalCrafts = 0;
        int totalPveKills = 0;
        int totalPvpKills = 0;
        int totalDeaths = 0;
        double totalJobsXp = 0.0;
        int totalTpa = 0;

        try (PreparedStatement ps = connection.prepareStatement(sqlActivityHybrid)) {
            ps.setString(1, playerUuid.toString());
            ps.setLong(2, startEpochSec);
            ps.setString(3, playerUuid.toString());
            ps.setLong(4, startOpenDay);
            ps.setLong(5, min5mDay);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    totalActiveSec = rs.getInt("total_active");
                    totalDistance = rs.getDouble("total_distance");
                    totalBreaks = rs.getInt("total_breaks");
                    totalPlaces = rs.getInt("total_places");
                    totalCrafts = rs.getInt("total_crafts");
                    totalPveKills = rs.getInt("total_pve");
                    totalPvpKills = rs.getInt("total_pvp");
                    totalDeaths = rs.getInt("total_deaths");
                    totalJobsXp = rs.getDouble("total_jobs");
                    totalTpa = rs.getInt("total_tpa");
                }
            }
        }

        // 活動履歴もセッションもない場合は空
        if (sessionCount == 0 && totalActiveSec == 0 && totalBreaks == 0 && totalPlaces == 0 && "Unknown".equals(lastKnownName)) {
            return Optional.empty();
        }

        // オンライン秒数の補正（セッション履歴がない/プレイ中の場合は活動秒数以上を担保）
        int finalOnlineSec = Math.max(sessionOnlineSec, totalActiveSec + sessionAfkSec);
        int avgSessionDuration = sessionCount > 0 ? (sessionOnlineSec / sessionCount) : totalActiveSec;

        // 4. JST 19:00基準 開放日アクティブ日数（1開放日サイクルあたり 600秒/10分以上 活動した日数）
        String sqlActiveDays = """
            SELECT COUNT(*) FROM (
                SELECT day_idx, SUM(active_seconds) as day_active
                FROM (
                    SELECT ((bucket_at - 36000) / 86400) as day_idx, active_seconds
                    FROM player_activity_5m
                    WHERE player_uuid = ? AND bucket_at >= ?
                    UNION ALL
                    SELECT date_epoch_day as day_idx, active_seconds
                    FROM player_activity_daily
                    WHERE player_uuid = ? AND date_epoch_day >= ((? - 36000) / 86400)
                )
                GROUP BY day_idx
                HAVING day_active >= 600
            )
        """;
        int activeDays = 0;
        try (PreparedStatement ps = connection.prepareStatement(sqlActiveDays)) {
            ps.setString(1, playerUuid.toString());
            ps.setLong(2, startEpochSec);
            ps.setString(3, playerUuid.toString());
            ps.setLong(4, startEpochSec);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    activeDays = rs.getInt(1);
                }
            }
        }

        // 5. 主活動ディメンションおよび主拠点グループ
        String primaryDimension = "minecraft:overworld";
        String sqlDim = """
            SELECT dimension, SUM(active_seconds) as s
            FROM (
                SELECT dimension, active_seconds FROM player_activity_5m WHERE player_uuid = ? AND bucket_at >= ?
                UNION ALL
                SELECT dimension, active_seconds FROM player_activity_daily WHERE player_uuid = ? AND date_epoch_day >= ?
            )
            GROUP BY dimension ORDER BY s DESC LIMIT 1
        """;
        try (PreparedStatement ps = connection.prepareStatement(sqlDim)) {
            ps.setString(1, playerUuid.toString());
            ps.setLong(2, startEpochSec);
            ps.setString(3, playerUuid.toString());
            ps.setLong(4, startDay);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getString("dimension") != null) {
                    primaryDimension = rs.getString("dimension");
                }
            }
        }

        UUID primaryGroupOwner = null;
        String sqlGroup = """
            SELECT group_owner_uuid, SUM(active_seconds) as s
            FROM (
                SELECT group_owner_uuid, active_seconds FROM player_activity_5m WHERE player_uuid = ? AND bucket_at >= ? AND group_owner_uuid != ''
                UNION ALL
                SELECT group_owner_uuid, active_seconds FROM player_activity_daily WHERE player_uuid = ? AND date_epoch_day >= ? AND group_owner_uuid != ''
            )
            GROUP BY group_owner_uuid ORDER BY s DESC LIMIT 1
        """;
        try (PreparedStatement ps = connection.prepareStatement(sqlGroup)) {
            ps.setString(1, playerUuid.toString());
            ps.setLong(2, startEpochSec);
            ps.setString(3, playerUuid.toString());
            ps.setLong(4, startDay);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String gStr = rs.getString("group_owner_uuid");
                    if (gStr != null && !gStr.isEmpty()) {
                        primaryGroupOwner = UUID.fromString(gStr);
                    }
                }
            }
        }

        return Optional.of(new PlayerSummaryDto(
                playerUuid,
                lastKnownName,
                firstSeenAt,
                lastSeenAt,
                sessionCount,
                (long) finalOnlineSec,
                (long) totalActiveSec,
                (long) sessionAfkSec,
                (long) avgSessionDuration,
                activeDays,
                (long) totalBreaks,
                (long) totalPlaces,
                (long) totalCrafts,
                (long) totalPveKills,
                (long) totalPvpKills,
                (long) totalDeaths,
                totalJobsXp,
                totalTpa,
                totalDistance,
                primaryDimension,
                primaryGroupOwner
        ));
    }

    @Override
    public synchronized List<PlayerSummaryDto> queryTopActivePlayers(TimeWindow window, int limit, long currentEpochSec) throws SQLException {
        if (connection == null || connection.isClosed()) return Collections.emptyList();

        long startEpochSec = window.getStartEpochSec(currentEpochSec);
        long todayDay = currentEpochSec / 86400L;
        long todayStartEpochSec = todayDay * 86400L;
        long startDay = startEpochSec / 86400L;

        long min5mEpoch = currentEpochSec;
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery("SELECT COALESCE(MIN(bucket_at), " + currentEpochSec + ") FROM player_activity_5m")) {
            if (rs.next()) {
                min5mEpoch = rs.getLong(1);
            }
        }
        long min5mDay = (min5mEpoch - 36000L) / 86400L;
        long startOpenDay = (startEpochSec - 36000L) / 86400L;

        String sql = """
            SELECT player_uuid, SUM(active_seconds) as total_active
            FROM (
                SELECT player_uuid, active_seconds FROM player_activity_5m
                WHERE bucket_at >= ?
                UNION ALL
                SELECT player_uuid, active_seconds FROM player_activity_daily
                WHERE date_epoch_day >= ? AND date_epoch_day < ?
            )
            GROUP BY player_uuid
            ORDER BY total_active DESC
            LIMIT ?;
        """;

        List<UUID> topUuids = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, startEpochSec);
            ps.setLong(2, startOpenDay);
            ps.setLong(3, min5mDay);
            ps.setInt(4, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    topUuids.add(UUID.fromString(rs.getString("player_uuid")));
                }
            }
        }

        List<PlayerSummaryDto> result = new ArrayList<>();
        for (UUID uuid : topUuids) {
            queryPlayerSummary(uuid, window, currentEpochSec).ifPresent(result::add);
        }
        return result;
    }

    @Override
    public synchronized OverviewSummaryDto queryOverviewSummary(TimeWindow window, long currentEpochSec) throws SQLException {
        if (connection == null || connection.isClosed()) return OverviewSummaryDto.empty();

        long startEpochSec = window.getStartEpochSec(currentEpochSec);
        long min5mEpoch = currentEpochSec;
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery("SELECT COALESCE(MIN(bucket_at), " + currentEpochSec + ") FROM player_activity_5m")) {
            if (rs.next()) {
                min5mEpoch = rs.getLong(1);
            }
        }
        long min5mDay = (min5mEpoch - 36000L) / 86400L;
        long startOpenDay = (startEpochSec - 36000L) / 86400L;

        // 1. JST 19:00基準 開放日アクティブ人数（いずれかの開放日サイクルで1日あたり 600秒/10分以上 活動したユニーク人数）
        String sqlActivePlayers = """
            SELECT COUNT(DISTINCT player_uuid) FROM (
                SELECT player_uuid, day_idx, SUM(active_seconds) as day_active
                FROM (
                    SELECT player_uuid, ((bucket_at - 36000) / 86400) as day_idx, active_seconds
                    FROM player_activity_5m
                    WHERE bucket_at >= ?
                    UNION ALL
                    SELECT player_uuid, date_epoch_day as day_idx, active_seconds
                    FROM player_activity_daily
                    WHERE date_epoch_day >= ? AND date_epoch_day < ?
                )
                GROUP BY player_uuid, day_idx
                HAVING day_active >= 600
            )
        """;
        int activeUniquePlayers = 0;
        try (PreparedStatement ps = connection.prepareStatement(sqlActivePlayers)) {
            ps.setLong(1, startEpochSec);
            ps.setLong(2, startOpenDay);
            ps.setLong(3, min5mDay);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    activeUniquePlayers = rs.getInt(1);
                }
            }
        }

        // 2. セッション合計（完了セッション履歴 + 進行中アクティブセッション）
        int totalSessionCount = 0;
        long totalOnlineSeconds = 0L;
        long totalAfkSeconds = 0L;
        String sqlSession = """
            SELECT COUNT(*) as sc, COALESCE(SUM(online_seconds), 0) as so, COALESCE(SUM(afk_seconds), 0) as sa
            FROM player_session
            WHERE joined_at >= ?;
        """;
        try (PreparedStatement ps = connection.prepareStatement(sqlSession)) {
            ps.setLong(1, startEpochSec);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    totalSessionCount = rs.getInt("sc");
                    totalOnlineSeconds = rs.getLong("so");
                    totalAfkSeconds = rs.getLong("sa");
                }
            }
        }

        // 進行中の全アクティブセッションの時間を合算
        for (var s : com.ruskserver.moveearth_addtional.analytics.tracker.SessionTracker.INSTANCE.getAllActiveSessions().values()) {
            if (s.getLoginTimeMs() / 1000L >= startEpochSec || currentEpochSec >= startEpochSec) {
                totalSessionCount++;
                totalOnlineSeconds += s.getOnlineSeconds(currentEpochSec * 1000L);
                totalAfkSeconds += s.getAfkSeconds();
            }
        }

        // 3. ハイブリッド活動量集約（5m + daily）
        String sqlActivity = """
            SELECT
                COALESCE(SUM(active_seconds), 0) as total_active,
                COALESCE(SUM(breaks), 0) as total_breaks,
                COALESCE(SUM(places), 0) as total_places,
                COALESCE(SUM(crafts), 0) as total_crafts,
                COALESCE(SUM(pve_kills), 0) as total_pve,
                COALESCE(SUM(pvp_kills), 0) as total_pvp,
                COALESCE(SUM(deaths), 0) as total_deaths,
                COALESCE(SUM(jobs_xp), 0.0) as total_jobs,
                COALESCE(SUM(distance_blocks), 0.0) as total_distance
            FROM (
                SELECT active_seconds, breaks, places, crafts, pve_kills, pvp_kills, deaths, jobs_xp, distance_blocks
                FROM player_activity_5m
                WHERE bucket_at >= ?
                UNION ALL
                SELECT active_seconds, breaks, places, crafts, pve_kills, pvp_kills, deaths, jobs_xp, distance_blocks
                FROM player_activity_daily
                WHERE date_epoch_day >= ? AND date_epoch_day < ?
            )
        """;

        long totalActiveSeconds = 0;
        long totalBreaks = 0;
        long totalPlaces = 0;
        long totalCrafts = 0;
        long totalPveKills = 0;
        long totalPvpKills = 0;
        long totalDeaths = 0;
        double totalJobsXp = 0.0;
        double totalDistanceBlocks = 0.0;

        try (PreparedStatement ps = connection.prepareStatement(sqlActivity)) {
            ps.setLong(1, startEpochSec);
            ps.setLong(2, startOpenDay);
            ps.setLong(3, min5mDay);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    totalActiveSeconds = rs.getLong("total_active");
                    totalBreaks = rs.getLong("total_breaks");
                    totalPlaces = rs.getLong("total_places");
                    totalCrafts = rs.getLong("total_crafts");
                    totalPveKills = rs.getLong("total_pve");
                    totalPvpKills = rs.getLong("total_pvp");
                    totalDeaths = rs.getLong("total_deaths");
                    totalJobsXp = rs.getDouble("total_jobs");
                    totalDistanceBlocks = rs.getDouble("total_distance");
                }
            }
        }

        // オンライン秒数は実アクティブ秒数以上を担保
        totalOnlineSeconds = Math.max(totalOnlineSeconds, totalActiveSeconds + totalAfkSeconds);

        return new OverviewSummaryDto(
                activeUniquePlayers,
                totalActiveSeconds,
                totalOnlineSeconds,
                totalAfkSeconds,
                totalBreaks,
                totalPlaces,
                totalCrafts,
                totalPveKills,
                totalPvpKills,
                totalDeaths,
                totalJobsXp,
                totalDistanceBlocks
        );
    }

    @Override
    public synchronized Optional<GroupSummaryDto> queryGroupSummary(UUID groupOwnerUuid, TimeWindow window, long currentEpochSec) throws SQLException {
        if (connection == null || connection.isClosed()) return Optional.empty();

        long startEpochSec = window.getStartEpochSec(currentEpochSec);

        // オーナー名
        String ownerName = "Unknown";
        String sqlOwner = "SELECT last_known_name FROM player_identity WHERE player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sqlOwner)) {
            ps.setString(1, groupOwnerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ownerName = rs.getString("last_known_name");
                }
            }
        }

        String sql = """
            SELECT
                COUNT(DISTINCT detector_pos_hash) as detector_count,
                COALESCE(SUM(member_minutes), 0.0) as total_member_min,
                COALESCE(SUM(visitor_minutes), 0.0) as total_visitor_min,
                COALESCE(SUM(intrusion_sessions), 0) as total_intrusions,
                COALESCE(MAX(distinct_members), 0) as max_members,
                COALESCE(MAX(distinct_visitors), 0) as max_visitors
            FROM detector_activity_5m
            WHERE group_owner_uuid = ? AND bucket_at >= ?;
        """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, groupOwnerUuid.toString());
            ps.setLong(2, startEpochSec);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int detectorCount = rs.getInt("detector_count");
                    double totalMemberMin = rs.getDouble("total_member_min");
                    double totalVisitorMin = rs.getDouble("total_visitor_min");
                    int totalIntrusions = rs.getInt("total_intrusions");
                    int maxMembers = rs.getInt("max_members");
                    int maxVisitors = rs.getInt("max_visitors");

                    if (detectorCount == 0 && "Unknown".equals(ownerName)) {
                        return Optional.empty();
                    }

                    return Optional.of(new GroupSummaryDto(
                            groupOwnerUuid,
                            ownerName,
                            detectorCount,
                            totalMemberMin,
                            totalVisitorMin,
                            totalIntrusions,
                            maxMembers,
                            maxVisitors
                    ));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public synchronized List<GroupSummaryDto> queryAllGroupSummaries(TimeWindow window, long currentEpochSec) throws SQLException {
        if (connection == null || connection.isClosed()) return Collections.emptyList();

        long startEpochSec = window.getStartEpochSec(currentEpochSec);
        String sql = """
            SELECT DISTINCT group_owner_uuid
            FROM detector_activity_5m
            WHERE group_owner_uuid IS NOT NULL AND group_owner_uuid != '' AND bucket_at >= ?;
        """;

        List<UUID> owners = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, startEpochSec);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String gStr = rs.getString("group_owner_uuid");
                    try {
                        owners.add(UUID.fromString(gStr));
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        List<GroupSummaryDto> list = new ArrayList<>();
        for (UUID owner : owners) {
            queryGroupSummary(owner, window, currentEpochSec).ifPresent(list::add);
        }
        return list;
    }

    @Override
    public synchronized List<SpatialHeatmapCellDto> querySpatialHeatmap(String dimension, TimeWindow window, int limit, long currentEpochSec) throws SQLException {
        if (connection == null || connection.isClosed()) return Collections.emptyList();

        long startEpochSec = window.getStartEpochSec(currentEpochSec);
        String sql = """
            SELECT cell_x, cell_z, y_band, group_owner_uuid, relation, SUM(active_samples) as total_samples, MAX(unique_players) as max_players
            FROM spatial_activity_5m
            WHERE dimension = ? AND bucket_at >= ?
            GROUP BY cell_x, cell_z, y_band, group_owner_uuid, relation
            ORDER BY total_samples DESC
            LIMIT ?;
        """;

        List<SpatialHeatmapCellDto> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, dimension);
            ps.setLong(2, startEpochSec);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String gStr = rs.getString("group_owner_uuid");
                    UUID groupOwner = (gStr != null && !gStr.isEmpty()) ? UUID.fromString(gStr) : null;

                    result.add(new SpatialHeatmapCellDto(
                            dimension,
                            rs.getInt("cell_x"),
                            rs.getInt("cell_z"),
                            rs.getString("y_band"),
                            groupOwner,
                            rs.getString("relation"),
                            rs.getInt("total_samples"),
                            rs.getInt("max_players")
                    ));
                }
            }
        }
        return result;
    }

    @Override
    public synchronized CollectorHealthDto queryCollectorHealth() throws SQLException {
        if (connection == null || connection.isClosed()) {
            return CollectorHealthDto.empty();
        }

        String sql = "SELECT * FROM collector_health ORDER BY recorded_at DESC LIMIT 1;";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return new CollectorHealthDto(
                        rs.getLong("recorded_at"),
                        rs.getInt("queue_depth"),
                        rs.getLong("dropped_events"),
                        rs.getLong("last_flush_duration_ms"),
                        rs.getLong("database_size_bytes")
                );
            }
        }
        return new CollectorHealthDto(System.currentTimeMillis() / 1000L, 0, 0L, 0L, getDatabaseSizeBytes());
    }

    @Override
    public long getDatabaseSizeBytes() {
        if (dbPath != null) {
            File f = dbPath.toFile();
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
