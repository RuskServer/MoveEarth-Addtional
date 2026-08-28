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
            assertEquals(1, rs.getInt("version"));
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
}
