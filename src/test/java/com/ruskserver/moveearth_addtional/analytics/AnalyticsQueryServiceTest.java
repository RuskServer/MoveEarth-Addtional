package com.ruskserver.moveearth_addtional.analytics;

import com.ruskserver.moveearth_addtional.analytics.group.GroupRelation;
import com.ruskserver.moveearth_addtional.analytics.model.*;
import com.ruskserver.moveearth_addtional.analytics.query.AnalyticsQueryService;
import com.ruskserver.moveearth_addtional.analytics.query.dto.*;
import com.ruskserver.moveearth_addtional.analytics.queue.AnalyticsEventQueue;
import com.ruskserver.moveearth_addtional.analytics.storage.SqliteAnalyticsStorageEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class AnalyticsQueryServiceTest {

    @TempDir
    Path tempDir;

    private SqliteAnalyticsStorageEngine engine;
    private AnalyticsQueryService queryService;

    private UUID player1Uuid;
    private UUID player2Uuid;
    private UUID groupOwnerUuid;

    @BeforeEach
    public void setUp() throws Exception {
        Path dbPath = tempDir.resolve("query_test.db");
        engine = new SqliteAnalyticsStorageEngine();
        engine.initialize(dbPath);

        queryService = new AnalyticsQueryService();
        queryService.setStorageEngineOverride(engine);
        queryService.clearCache();

        player1Uuid = UUID.randomUUID();
        player2Uuid = UUID.randomUUID();
        groupOwnerUuid = UUID.randomUUID();

        populateSampleData();
    }

    private void populateSampleData() throws Exception {
        long now = System.currentTimeMillis() / 1000L;

        // Identity
        AnalyticsEventQueue.SessionStartEvent start1 = new AnalyticsEventQueue.SessionStartEvent(
                UUID.randomUUID(), player1Uuid, "PlayerOne", now - 50_000L);
        AnalyticsEventQueue.SessionStartEvent start2 = new AnalyticsEventQueue.SessionStartEvent(
                UUID.randomUUID(), player2Uuid, "PlayerTwo", now - 30_000L);
        AnalyticsEventQueue.SessionStartEvent startOwner = new AnalyticsEventQueue.SessionStartEvent(
                UUID.randomUUID(), groupOwnerUuid, "BaseOwner", now - 100_000L);

        // Sessions
        SessionRecord s1 = new SessionRecord(
                UUID.randomUUID(), player1Uuid, "PlayerOne", now - 10_000L, now - 4_000L, 6000, 4500, 1500);
        AnalyticsEventQueue.SessionEndEvent end1 = new AnalyticsEventQueue.SessionEndEvent(s1);

        // Player Activity 5m
        PlayerActivityBucket pAct1 = new PlayerActivityBucket(
                now - 2000L, player1Uuid, "minecraft:overworld", groupOwnerUuid, 300, 150.0, 20, 10, 5, 4, 1, 0, 250.0, 2);
        PlayerActivityBucket pAct2 = new PlayerActivityBucket(
                now - 1000L, player1Uuid, "minecraft:overworld", groupOwnerUuid, 250, 100.0, 15, 5, 2, 2, 0, 1, 150.0, 1);
        PlayerActivityBucket pAct3 = new PlayerActivityBucket(
                now - 500L, player2Uuid, "minecraft:the_nether", null, 100, 50.0, 5, 2, 0, 1, 0, 0, 50.0, 0);

        // Spatial Activity 5m
        SpatialActivityBucket spat1 = new SpatialActivityBucket(
                now - 1000L, "minecraft:overworld", 10, 20, YBand.SURFACE, groupOwnerUuid, GroupRelation.MEMBER, 50, 3);
        SpatialActivityBucket spat2 = new SpatialActivityBucket(
                now - 500L, "minecraft:overworld", 10, 20, YBand.SURFACE, groupOwnerUuid, GroupRelation.MEMBER, 30, 2);

        // Detector Activity 5m
        DetectorActivityBucket det1 = new DetectorActivityBucket(
                now - 1000L, "minecraft:overworld", "hash123", "北門", groupOwnerUuid, 15.0, 5.0, 2, 3, 2);

        // Health Metric
        AnalyticsEventQueue.HealthMetricEvent health = new AnalyticsEventQueue.HealthMetricEvent(
                now - 100L, 5, 0L, 12L, engine.getDatabaseSizeBytes());

        engine.writeBatch(List.of(
                start1, start2, startOwner, end1,
                new AnalyticsEventQueue.PlayerActivityFlushEvent(List.of(pAct1, pAct2, pAct3)),
                new AnalyticsEventQueue.SpatialActivityFlushEvent(List.of(spat1, spat2)),
                new AnalyticsEventQueue.DetectorActivityFlushEvent(List.of(det1)),
                health
        ));
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (engine != null && engine.isOpen()) {
            engine.close();
        }
    }

    @Test
    public void testPlayerSummaryAsync() throws Exception {
        Optional<PlayerSummaryDto> result = queryService.getPlayerSummaryAsync(player1Uuid, TimeWindow.DAYS_7).get();

        assertTrue(result.isPresent());
        PlayerSummaryDto dto = result.get();
        assertEquals(player1Uuid, dto.playerUuid());
        assertEquals("PlayerOne", dto.lastKnownName());
        assertEquals(1, dto.sessionCount());
        assertEquals(6000, dto.totalOnlineSeconds());
        assertEquals(550, dto.totalActiveSeconds()); // 300 + 250 (5mバケット集計)
        assertEquals(1500, dto.totalAfkSeconds());
        assertEquals(35, dto.totalBreaks()); // 20 + 15
        assertEquals(15, dto.totalPlaces()); // 10 + 5
        assertEquals(7, dto.totalCrafts()); // 5 + 2
        assertEquals(6, dto.totalPveKills()); // 4 + 2
        assertEquals(1, dto.totalPvpKills()); // 1 + 0
        assertEquals(1, dto.totalDeaths()); // 0 + 1
        assertEquals(400.0, dto.totalJobsXp(), 0.001); // 250 + 150
        assertEquals(3, dto.totalTpaSuccesses()); // 2 + 1
        assertEquals(250.0, dto.totalDistanceBlocks(), 0.001); // 150 + 100
        assertEquals("minecraft:overworld", dto.primaryDimension());
        assertEquals(groupOwnerUuid, dto.primaryGroupOwnerUuid());
    }

    @Test
    public void testTopActivePlayersAsync() throws Exception {
        List<PlayerSummaryDto> topList = queryService.getTopActivePlayersAsync(TimeWindow.DAYS_7, 10).get();

        assertEquals(2, topList.size());
        assertEquals(player1Uuid, topList.get(0).playerUuid()); // 550アクティブ秒
        assertEquals(player2Uuid, topList.get(1).playerUuid()); // 100アクティブ秒
    }

    @Test
    public void testGroupSummaryAsync() throws Exception {
        Optional<GroupSummaryDto> result = queryService.getGroupSummaryAsync(groupOwnerUuid, TimeWindow.DAYS_7).get();

        assertTrue(result.isPresent());
        GroupSummaryDto dto = result.get();
        assertEquals(groupOwnerUuid, dto.groupOwnerUuid());
        assertEquals("BaseOwner", dto.ownerName());
        assertEquals(1, dto.detectorCount());
        assertEquals(15.0, dto.totalMemberMinutes(), 0.001);
        assertEquals(5.0, dto.totalVisitorMinutes(), 0.001);
        assertEquals(2, dto.totalIntrusionSessions());
        assertEquals(3, dto.maxDistinctMembers());
        assertEquals(2, dto.maxDistinctVisitors());
    }

    @Test
    public void testDetectorSummariesExposeReadableName() throws Exception {
        List<DetectorSummaryDto> detectors = queryService
                .getDetectorSummariesAsync(groupOwnerUuid, TimeWindow.DAYS_7)
                .get();

        assertEquals(1, detectors.size());
        DetectorSummaryDto detector = detectors.getFirst();
        assertEquals("北門", detector.detectorName());
        assertEquals("minecraft:overworld", detector.dimension());
        assertEquals(2, detector.totalIntrusionSessions());
        assertEquals(15.0, detector.totalMemberMinutes(), 0.001);
    }

    @Test
    public void testSpatialHeatmapAsync() throws Exception {
        List<SpatialHeatmapCellDto> cells = queryService.getSpatialHeatmapAsync("minecraft:overworld", TimeWindow.DAYS_7, 10).get();

        assertEquals(1, cells.size());
        SpatialHeatmapCellDto cell = cells.getFirst();
        assertEquals("minecraft:overworld", cell.dimension());
        assertEquals(10, cell.cellX());
        assertEquals(20, cell.cellZ());
        assertEquals(YBand.SURFACE.name(), cell.yBand());
        assertEquals(groupOwnerUuid, cell.groupOwnerUuid());
        assertEquals(80, cell.totalActiveSamples()); // 50 + 30
        assertEquals(3, cell.maxUniquePlayers());
    }

    @Test
    public void testCollectorHealthAsync() throws Exception {
        CollectorHealthDto health = queryService.getCollectorHealthAsync().get();

        assertNotNull(health);
        assertEquals(5, health.queueDepth());
        assertEquals(0L, health.droppedEventsTotal());
        assertEquals(12L, health.lastFlushDurationMs());
        assertTrue(health.databaseSizeBytes() > 0);
    }

    @Test
    public void testSafeFallbackOnMissingData() throws Exception {
        UUID unknownUuid = UUID.randomUUID();

        // 存在しないプレイヤー
        Optional<PlayerSummaryDto> pResult = queryService.getPlayerSummaryAsync(unknownUuid, TimeWindow.DAYS_7).get();
        assertTrue(pResult.isEmpty());

        // 存在しないグループ (存在しない場合は空Option)
        Optional<GroupSummaryDto> gResult = queryService.getGroupSummaryAsync(unknownUuid, TimeWindow.DAYS_7).get();
        assertTrue(gResult.isEmpty());
    }
}
