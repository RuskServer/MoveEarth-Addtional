package com.ruskserver.moveearth_addtional.analytics;

import com.ruskserver.moveearth_addtional.analytics.group.GroupRelation;
import com.ruskserver.moveearth_addtional.analytics.model.*;
import com.ruskserver.moveearth_addtional.analytics.queue.AnalyticsEventQueue;
import com.ruskserver.moveearth_addtional.analytics.storage.SqliteAnalyticsStorageEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SqliteAnalyticsStorageEngineTest {

    @TempDir
    Path tempDir;

    private Path dbPath;
    private SqliteAnalyticsStorageEngine engine;

    @BeforeEach
    public void setUp() throws Exception {
        dbPath = tempDir.resolve("test_analytics.db");
        engine = new SqliteAnalyticsStorageEngine();
        engine.initialize(dbPath);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (engine != null && engine.isOpen()) {
            engine.close();
        }
    }

    @Test
    public void testSchemaInitialization() throws Exception {
        assertTrue(engine.isOpen());
        assertTrue(engine.getDatabaseSizeBytes() > 0);

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version FROM schema_version")) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt("version"));
        }
    }

    @Test
    public void testWriteSessionEvents() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        long joinedAt = 1000000L;
        long leftAt = 1000600L;

        AnalyticsEventQueue.SessionStartEvent startEvent = new AnalyticsEventQueue.SessionStartEvent(
                sessionId, playerUuid, "TestPlayer", joinedAt);

        SessionRecord sessionRecord = new SessionRecord(
                sessionId, playerUuid, "TestPlayer", joinedAt, leftAt, 600, 450, 150);
        AnalyticsEventQueue.SessionEndEvent endEvent = new AnalyticsEventQueue.SessionEndEvent(sessionRecord);

        engine.writeBatch(List.of(startEvent, endEvent));

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT * FROM player_identity WHERE player_uuid = '" + playerUuid + "'")) {
                assertTrue(rs.next());
                assertEquals("TestPlayer", rs.getString("last_known_name"));
                assertEquals(joinedAt, rs.getLong("first_seen_at"));
                assertEquals(leftAt, rs.getLong("last_seen_at"));
            }

            try (ResultSet rs = stmt.executeQuery("SELECT * FROM player_session WHERE session_id = '" + sessionId + "'")) {
                assertTrue(rs.next());
                assertEquals("TestPlayer", rs.getString("last_known_name"));
                assertEquals(600, rs.getInt("online_seconds"));
                assertEquals(450, rs.getInt("active_seconds"));
                assertEquals(150, rs.getInt("afk_seconds"));
            }
        }
    }

    @Test
    public void testWritePlayerActivityAndDailyAggregation() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        UUID groupOwner = UUID.randomUUID();
        String dim = "minecraft:overworld";

        // Day 1: 2つの5分バケット
        long bucket1 = 86400L * 10 + 300L;
        long bucket2 = 86400L * 10 + 600L;

        PlayerActivityBucket p1 = new PlayerActivityBucket(
                bucket1, playerUuid, dim, groupOwner, 180, 50.0, 10, 5, 2, 3, 1, 0, 150.0, 1);
        PlayerActivityBucket p2 = new PlayerActivityBucket(
                bucket2, playerUuid, dim, groupOwner, 200, 70.0, 15, 8, 3, 2, 0, 1, 200.0, 0);

        engine.writeBatch(List.of(new AnalyticsEventQueue.PlayerActivityFlushEvent(List.of(p1, p2))));

        // 5分データの検証
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM player_activity_5m")) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1));
        }

        // 日次集約の実行
        engine.aggregateDaily(86400L * 11);

        // 日次データの検証 (Day 10)
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM player_activity_daily WHERE date_epoch_day = 10")) {
            assertTrue(rs.next());
            assertEquals(playerUuid.toString(), rs.getString("player_uuid"));
            assertEquals(380, rs.getInt("active_seconds")); // 180 + 200
            assertEquals(120.0, rs.getDouble("distance_blocks"), 0.001); // 50.0 + 70.0
            assertEquals(25, rs.getInt("breaks")); // 10 + 15
            assertEquals(13, rs.getInt("places")); // 5 + 8
            assertEquals(5, rs.getInt("crafts")); // 2 + 3
            assertEquals(5, rs.getInt("pve_kills")); // 3 + 2
            assertEquals(1, rs.getInt("pvp_kills")); // 1 + 0
            assertEquals(1, rs.getInt("deaths")); // 0 + 1
            assertEquals(350.0, rs.getDouble("jobs_xp"), 0.001); // 150.0 + 200.0
            assertEquals(1, rs.getInt("tpa_successes")); // 1 + 0
        }
    }

    @Test
    public void testPurgeOldRecords() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        String dim = "minecraft:overworld";

        long oldBucket = 1000L;
        long newBucket = 1000000L;

        PlayerActivityBucket oldP = new PlayerActivityBucket(
                oldBucket, playerUuid, dim, null, 100, 10.0, 1, 1, 0, 0, 0, 0, 0, 0);
        PlayerActivityBucket newP = new PlayerActivityBucket(
                newBucket, playerUuid, dim, null, 100, 10.0, 1, 1, 0, 0, 0, 0, 0, 0);

        engine.writeBatch(List.of(new AnalyticsEventQueue.PlayerActivityFlushEvent(List.of(oldP, newP))));

        // 5分データのパージ (cutoff = 500000)
        engine.purgeOldRecords(500000L, 500000L, 500000L);

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT bucket_at FROM player_activity_5m")) {
            assertTrue(rs.next());
            assertEquals(newBucket, rs.getLong("bucket_at"));
            assertFalse(rs.next()); // oldBucketは削除されている
        }
    }

    @Test
    public void testReopenPersistence() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        AnalyticsEventQueue.SessionStartEvent startEvent = new AnalyticsEventQueue.SessionStartEvent(
                UUID.randomUUID(), playerUuid, "ReopenPlayer", 12345L);

        engine.writeBatch(List.of(startEvent));
        engine.close();

        // 再オープン
        SqliteAnalyticsStorageEngine reopenedEngine = new SqliteAnalyticsStorageEngine();
        reopenedEngine.initialize(dbPath);
        assertTrue(reopenedEngine.isOpen());

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT last_known_name FROM player_identity WHERE player_uuid = '" + playerUuid + "'")) {
            assertTrue(rs.next());
            assertEquals("ReopenPlayer", rs.getString("last_known_name"));
        }

        reopenedEngine.close();
    }

    @Test
    public void testOverviewSummaryAndHybridQuery() throws Exception {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        long now = 86400L * 100 + 3600L; // Day 100, 1:00

        // Day 10 (過去90日前の日次集約相当)
        long day10 = 86400L * 10 + 300L;
        PlayerActivityBucket pastP1 = new PlayerActivityBucket(
                day10, p1, "minecraft:overworld", null, 1000, 500.0, 100, 50, 20, 5, 1, 0, 200.0, 3);
        engine.writeBatch(List.of(
                new AnalyticsEventQueue.SessionStartEvent(UUID.randomUUID(), p1, "Player1", day10),
                new AnalyticsEventQueue.PlayerActivityFlushEvent(List.of(pastP1))
        ));
        engine.aggregateDaily(86400L * 20); // 日次テーブルへ集約

        // Day 100 (本日リアルタイム)
        PlayerActivityBucket todayP2 = new PlayerActivityBucket(
                now, p2, "minecraft:overworld", null, 2000, 1000.0, 200, 100, 40, 10, 2, 1, 400.0, 5);
        engine.writeBatch(List.of(
                new AnalyticsEventQueue.SessionStartEvent(UUID.randomUUID(), p2, "Player2", now),
                new AnalyticsEventQueue.PlayerActivityFlushEvent(List.of(todayP2))
        ));

        // 過去90日前の5分データをパージ (cutoff = Day 20)
        engine.purgeOldRecords(86400L * 20, 0L, 0L);

        // 1. Player1 のサマリー（ALL_TIMEで過去のdailyから合算されるか）
        var p1SummaryOpt = engine.queryPlayerSummary(p1, com.ruskserver.moveearth_addtional.analytics.query.dto.TimeWindow.ALL_TIME, now);
        assertTrue(p1SummaryOpt.isPresent());
        var p1Summary = p1SummaryOpt.get();
        assertEquals("Player1", p1Summary.lastKnownName());
        assertEquals(100, p1Summary.totalBreaks()); // dailyから復元
        assertEquals(200.0, p1Summary.totalJobsXp(), 0.001);

        // 2. OverviewSummary の全期間集約
        var overview = engine.queryOverviewSummary(com.ruskserver.moveearth_addtional.analytics.query.dto.TimeWindow.ALL_TIME, now);
        assertEquals(2, overview.activeUniquePlayers()); // p1 + p2 (両方10分以上)
        assertEquals(300, overview.totalBreaks()); // 100 + 200
        assertEquals(150, overview.totalPlaces()); // 50 + 100
        assertEquals(600.0, overview.totalJobsXp(), 0.001); // 200 + 400
        assertEquals(1500.0, overview.totalDistanceBlocks(), 0.001); // 500 + 1000
    }

    @Test
    public void testMigrationFromV1() throws Exception {
        Path v1DbPath = tempDir.resolve("v1_legacy.db");
        // v1形式（3列主キーテーブル）を手動作成
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + v1DbPath.toAbsolutePath());
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE schema_version (version INTEGER PRIMARY KEY, applied_at INTEGER);");
            stmt.execute("INSERT INTO schema_version VALUES (1, 1000);");
            stmt.execute("""
                CREATE TABLE player_activity_5m (
                    bucket_at INTEGER NOT NULL,
                    player_uuid TEXT NOT NULL,
                    dimension TEXT NOT NULL,
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
            stmt.execute("""
                CREATE TABLE spatial_activity_5m (
                    bucket_at INTEGER NOT NULL,
                    dimension TEXT NOT NULL,
                    cell_x INTEGER NOT NULL,
                    cell_z INTEGER NOT NULL,
                    y_band TEXT NOT NULL,
                    relation TEXT NOT NULL,
                    active_samples INTEGER NOT NULL,
                    unique_players INTEGER NOT NULL,
                    PRIMARY KEY (bucket_at, dimension, cell_x, cell_z, y_band, relation)
                );
            """);
            stmt.execute("""
                CREATE TABLE player_activity_daily (
                    date_epoch_day INTEGER NOT NULL,
                    player_uuid TEXT NOT NULL,
                    dimension TEXT NOT NULL,
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

            UUID pOld = UUID.randomUUID();
            stmt.execute("INSERT INTO player_activity_5m VALUES (1000, '" + pOld + "', 'minecraft:overworld', 120, 10.0, 5, 2, 0, 0, 0, 0, 10.0, 0);");
        }

        // v2エンジンで初期化してマイグレーション実行
        SqliteAnalyticsStorageEngine v2Engine = new SqliteAnalyticsStorageEngine();
        v2Engine.initialize(v1DbPath);
        assertTrue(v2Engine.isOpen());

        // マイグレーション後に新4列主キー形式でデータ追加できるか検証
        UUID pNew = UUID.randomUUID();
        UUID gOwner = UUID.randomUUID();
        PlayerActivityBucket newBucket = new PlayerActivityBucket(
                2000L, pNew, "minecraft:overworld", gOwner, 300, 50.0, 10, 5, 1, 0, 0, 0, 50.0, 0);
        v2Engine.writeBatch(List.of(new AnalyticsEventQueue.PlayerActivityFlushEvent(List.of(newBucket))));

        var pNewSummary = v2Engine.queryPlayerSummary(pNew, com.ruskserver.moveearth_addtional.analytics.query.dto.TimeWindow.ALL_TIME, 3000L);
        assertTrue(pNewSummary.isPresent());
        assertEquals(10, pNewSummary.get().totalBreaks());

        v2Engine.close();
    }

    @Test
    public void testActiveDaysAndOnlinePlayerPreserved() throws Exception {
        UUID onlinePlayer = UUID.randomUUID();
        UUID shortStayPlayer = UUID.randomUUID();
        long now = 86400L * 5 + 3600L; // Day 5, 1:00

        // 1. shortStayPlayer: 1分（60秒）だけ活動 (10分未満)
        PlayerActivityBucket shortBucket = new PlayerActivityBucket(
                now, shortStayPlayer, "minecraft:overworld", null, 60, 10.0, 1, 1, 0, 0, 0, 0, 5.0, 0);

        // 2. onlinePlayer: 15分（900秒）活動 (10分以上) - ログアウト前（セッション未記録）
        PlayerActivityBucket onlineBucket = new PlayerActivityBucket(
                now, onlinePlayer, "minecraft:overworld", null, 900, 200.0, 50, 30, 5, 2, 0, 0, 150.0, 1);

        engine.writeBatch(List.of(
                new AnalyticsEventQueue.SessionStartEvent(UUID.randomUUID(), onlinePlayer, "OnlinePlayer", now - 900L),
                new AnalyticsEventQueue.PlayerActivityFlushEvent(List.of(shortBucket, onlineBucket))
        ));

        // ログアウト前でも onlinePlayer の活動時間が正しく取得できる
        var pSummary = engine.queryPlayerSummary(onlinePlayer, com.ruskserver.moveearth_addtional.analytics.query.dto.TimeWindow.DAYS_7, now);
        assertTrue(pSummary.isPresent());
        assertEquals(900, pSummary.get().totalActiveSeconds());
        assertEquals(1, pSummary.get().activeDays()); // 10分以上なので1日

        // shortStayPlayer は 10分未満なので activeDays = 0
        var shortSummary = engine.queryPlayerSummary(shortStayPlayer, com.ruskserver.moveearth_addtional.analytics.query.dto.TimeWindow.DAYS_7, now);
        assertTrue(shortSummary.isPresent());
        assertEquals(60, shortSummary.get().totalActiveSeconds());
        assertEquals(0, shortSummary.get().activeDays()); // 10分未満なので0日

        // 概要KPI: activeUniquePlayers は 10分以上の 1 人（onlinePlayer のみ）
        var overview = engine.queryOverviewSummary(com.ruskserver.moveearth_addtional.analytics.query.dto.TimeWindow.DAYS_7, now);
        assertEquals(1, overview.activeUniquePlayers());
        assertEquals(960L, overview.totalActiveSeconds()); // 900 + 60 は合計活動時間として正しく加算
    }
}
