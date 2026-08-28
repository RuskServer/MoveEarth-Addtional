package com.ruskserver.moveearth_addtional.analytics;

import com.ruskserver.moveearth_addtional.analytics.model.PlayerActivityBucket;
import com.ruskserver.moveearth_addtional.analytics.query.AnalyticsQueryService;
import com.ruskserver.moveearth_addtional.analytics.query.dto.TimeWindow;
import com.ruskserver.moveearth_addtional.analytics.query.export.AnalyticsExportService;
import com.ruskserver.moveearth_addtional.analytics.queue.AnalyticsEventQueue;
import com.ruskserver.moveearth_addtional.analytics.storage.SqliteAnalyticsStorageEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class AnalyticsExportServiceTest {

    @TempDir
    Path tempDir;

    private SqliteAnalyticsStorageEngine engine;

    @BeforeEach
    public void setUp() throws Exception {
        Path dbPath = tempDir.resolve("export_test.db");
        engine = new SqliteAnalyticsStorageEngine();
        engine.initialize(dbPath);

        AnalyticsQueryService.INSTANCE.setStorageEngineOverride(engine);
        AnalyticsQueryService.INSTANCE.clearCache();

        // テストデータ
        long now = System.currentTimeMillis() / 1000L;
        UUID p1 = UUID.randomUUID();
        AnalyticsEventQueue.SessionStartEvent s1 = new AnalyticsEventQueue.SessionStartEvent(
                UUID.randomUUID(), p1, "ExportPlayer", now - 1000L);
        PlayerActivityBucket b1 = new PlayerActivityBucket(
                now - 500L, p1, "minecraft:overworld", null, 300, 100.0, 10, 5, 2, 1, 0, 0, 100.0, 1);

        engine.writeBatch(List.of(s1, new AnalyticsEventQueue.PlayerActivityFlushEvent(List.of(b1))));
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (engine != null && engine.isOpen()) {
            engine.close();
        }
    }

    @Test
    public void testExportCsv() throws Exception {
        Path exportDir = tempDir.resolve("exports");
        Path csvFile = AnalyticsExportService.INSTANCE.exportPlayersToDirAsync(
                exportDir, AnalyticsExportService.ExportFormat.CSV, TimeWindow.DAYS_7).get();

        assertTrue(Files.exists(csvFile));
        List<String> lines = Files.readAllLines(csvFile);
        assertTrue(lines.size() >= 2);
        assertTrue(lines.get(0).startsWith("uuid,name,first_seen"));
        assertTrue(lines.get(1).contains("ExportPlayer"));
    }

    @Test
    public void testExportJsonl() throws Exception {
        Path exportDir = tempDir.resolve("exports");
        Path jsonlFile = AnalyticsExportService.INSTANCE.exportPlayersToDirAsync(
                exportDir, AnalyticsExportService.ExportFormat.JSONL, TimeWindow.DAYS_7).get();

        assertTrue(Files.exists(jsonlFile));
        List<String> lines = Files.readAllLines(jsonlFile);
        assertEquals(1, lines.size());
        assertTrue(lines.getFirst().contains("\"lastKnownName\":\"ExportPlayer\""));
    }
}
