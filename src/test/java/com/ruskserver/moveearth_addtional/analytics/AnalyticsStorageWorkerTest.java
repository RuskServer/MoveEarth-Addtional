package com.ruskserver.moveearth_addtional.analytics;

import com.ruskserver.moveearth_addtional.analytics.queue.AnalyticsEventQueue;
import com.ruskserver.moveearth_addtional.analytics.storage.AnalyticsStorageWorker;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class AnalyticsStorageWorkerTest {

    @TempDir
    Path tempDir;

    private Path dbPath;
    private SqliteAnalyticsStorageEngine engine;
    private AnalyticsEventQueue queue;
    private AnalyticsStorageWorker worker;

    @BeforeEach
    public void setUp() throws Exception {
        dbPath = tempDir.resolve("worker_test.db");
        engine = new SqliteAnalyticsStorageEngine();
        engine.initialize(dbPath);

        queue = new AnalyticsEventQueue();
        worker = new AnalyticsStorageWorker(engine, queue);
        worker.start();
    }

    @AfterEach
    public void tearDown() {
        if (worker != null && worker.isRunning()) {
            worker.stopAndFlush(2000L);
        }
    }

    @Test
    public void testWorkerAsyncFlushAndStop() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        AnalyticsEventQueue.SessionStartEvent startEvent = new AnalyticsEventQueue.SessionStartEvent(
                UUID.randomUUID(), playerUuid, "WorkerTestPlayer", 9999L);

        queue.enqueue(startEvent);

        // 停止時フラッシュ
        worker.stopAndFlush(3000L);
        assertFalse(worker.isRunning());

        // DBに書き込まれているか検証
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT last_known_name FROM player_identity WHERE player_uuid = '" + playerUuid + "'")) {
            assertTrue(rs.next());
            assertEquals("WorkerTestPlayer", rs.getString("last_known_name"));
        }
    }
}
