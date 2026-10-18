package com.ruskserver.moveearth_addtional.analytics;

import com.ruskserver.moveearth_addtional.analytics.model.PlayerActivityBucket;
import com.ruskserver.moveearth_addtional.analytics.model.ChunkLoadSample;
import com.ruskserver.moveearth_addtional.analytics.model.ChunkProfileRecord;
import com.ruskserver.moveearth_addtional.analytics.model.ServerPerformanceSample;
import com.ruskserver.moveearth_addtional.analytics.query.AnalyticsQueryService;
import com.ruskserver.moveearth_addtional.analytics.query.dto.TimeWindow;
import com.ruskserver.moveearth_addtional.analytics.query.export.AnalyticsExportService;
import com.ruskserver.moveearth_addtional.analytics.query.export.ExportRowSink;
import com.ruskserver.moveearth_addtional.analytics.query.export.ExportTable;
import com.ruskserver.moveearth_addtional.analytics.queue.AnalyticsEventQueue;
import com.ruskserver.moveearth_addtional.analytics.storage.SqliteAnalyticsStorageEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipFile;

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

        ServerPerformanceSample performance = new ServerPerformanceSample(now - 60L, 19.5D, 51.2D, 300, 8);
        ChunkLoadSample chunk = new ChunkLoadSample(now - 60L, "minecraft:overworld", 5, -2, 8, 12, 38.0D);
        ChunkProfileRecord profile = new ChunkProfileRecord(UUID.randomUUID(), now - 50L, now - 40L,
                "manual", "minecraft:overworld", 5, -2, 10, 1.5D, 3.5D, 15.0D,
                7.0D, 6.0D, 2.0D, 20L, 10L, 5L);
        engine.writeBatch(List.of(s1, new AnalyticsEventQueue.PlayerActivityFlushEvent(List.of(b1)),
                new AnalyticsEventQueue.PerformanceSampleEvent(performance, List.of(chunk)),
                new AnalyticsEventQueue.ChunkProfileEvent(List.of(profile))));
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

    @Test
    public void testPerformanceAndChunkCsvExports() throws Exception {
        Path exportDir = tempDir.resolve("performance_exports");
        Path performanceFile = AnalyticsExportService.INSTANCE.exportPerformanceToDirAsync(
                exportDir, AnalyticsExportService.ExportFormat.CSV, TimeWindow.DAYS_7).get();
        Path chunkFile = AnalyticsExportService.INSTANCE.exportChunkLoadsToDirAsync(
                exportDir, AnalyticsExportService.ExportFormat.CSV, TimeWindow.DAYS_7,
                "minecraft:overworld").get();
        Path profileFile = AnalyticsExportService.INSTANCE.exportChunkProfilesToDirAsync(
                exportDir, AnalyticsExportService.ExportFormat.CSV, TimeWindow.DAYS_7).get();

        List<String> performanceLines = Files.readAllLines(performanceFile);
        assertEquals("recorded_at,tps,mspt,loaded_chunks,online_players", performanceLines.getFirst());
        assertTrue(performanceLines.get(1).contains("19.500"));
        List<String> chunkLines = Files.readAllLines(chunkFile);
        assertTrue(chunkLines.getFirst().startsWith("recorded_at,dimension,chunk_x"));
        assertTrue(chunkLines.get(1).contains("minecraft:overworld,5,-2"));
        List<String> profileLines = Files.readAllLines(profileFile);
        assertTrue(profileLines.getFirst().startsWith("session_id,started_at,finished_at,trigger"));
        assertTrue(profileLines.get(1).contains("manual,minecraft:overworld,5,-2"));
    }

    @Test
    public void testTableExportAddsPlayerNames() throws Exception {
        AnalyticsExportService.TableExport export = AnalyticsExportService.INSTANCE.exportTableToDirAsync(
                tempDir.resolve("tables"), AnalyticsExportService.ExportFormat.CSV, TimeWindow.DAYS_7,
                ExportTable.PLAYER_5M).get();
        List<String> lines = Files.readAllLines(export.path());
        assertTrue(lines.getFirst().startsWith("bucket_at,player_uuid,last_known_name,dimension"));
        assertEquals(1, export.rows());
        assertFalse(export.truncated());
        assertTrue(lines.get(1).contains("ExportPlayer"));
    }

    @Test
    public void testEveryTableExportsAsJsonl() throws Exception {
        for (ExportTable table : ExportTable.values()) {
            AnalyticsExportService.TableExport export = AnalyticsExportService.INSTANCE.exportTableToDirAsync(
                    tempDir.resolve("jsonl_" + table.id()), AnalyticsExportService.ExportFormat.JSONL,
                    TimeWindow.ALL_TIME, table).get();
            assertTrue(Files.exists(export.path()), table.id());
            assertEquals(export.rows(), Files.readAllLines(export.path()).size(), table.id());
        }
    }

    @Test
    public void testRowLimitIsReportedAsTruncation() throws Exception {
        long now = System.currentTimeMillis() / 1000L;
        engine.writeBatch(List.of(new AnalyticsEventQueue.SessionStartEvent(
                UUID.randomUUID(), UUID.randomUUID(), "SecondPlayer", now - 900L)));
        List<Object[]> rows = new ArrayList<>();
        boolean truncated = engine.exportTable(ExportTable.PLAYERS, 0L, now, 1, new ExportRowSink() {
            @Override public void columns(List<String> columns) { }
            @Override public void row(Object[] values) { rows.add(values); }
        });
        assertTrue(truncated);
        assertEquals(1, rows.size());
    }

    @Test
    public void testAllExportZipsEveryFileWithAReadme() throws Exception {
        Path zip = AnalyticsExportService.INSTANCE.exportAllToZipAsync(
                tempDir.resolve("all"), AnalyticsExportService.ExportFormat.CSV, TimeWindow.DAYS_7).get();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            List<String> names = file.stream().map(entry -> entry.getName()).toList();
            assertTrue(names.contains("README.txt"));
            assertEquals(ExportTable.values().length + 5 + 1, names.size(), names.toString());
            for (ExportTable table : ExportTable.values()) {
                assertTrue(names.stream().anyMatch(name -> name.startsWith(table.id() + "_")), table.id());
            }
        }
        try (var leftovers = Files.list(tempDir.resolve("all"))) {
            assertEquals(1, leftovers.count(), "only the zip remains");
        }
    }
}
