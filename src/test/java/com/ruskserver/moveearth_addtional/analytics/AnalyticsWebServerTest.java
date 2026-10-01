package com.ruskserver.moveearth_addtional.analytics;

import com.ruskserver.moveearth_addtional.analytics.config.AnalyticsConfig;
import com.ruskserver.moveearth_addtional.analytics.model.PlayerActivityBucket;
import com.ruskserver.moveearth_addtional.analytics.model.DetectorActivityBucket;
import com.ruskserver.moveearth_addtional.analytics.model.ChunkLoadSample;
import com.ruskserver.moveearth_addtional.analytics.model.ChunkProfileRecord;
import com.ruskserver.moveearth_addtional.analytics.model.ServerPerformanceSample;
import com.ruskserver.moveearth_addtional.analytics.query.AnalyticsQueryService;
import com.ruskserver.moveearth_addtional.analytics.queue.AnalyticsEventQueue;
import com.ruskserver.moveearth_addtional.analytics.storage.SqliteAnalyticsStorageEngine;
import com.ruskserver.moveearth_addtional.analytics.web.AnalyticsWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class AnalyticsWebServerTest {

    @TempDir
    Path tempDir;

    private SqliteAnalyticsStorageEngine engine;
    private UUID groupOwnerUuid;
    private int webPort;

    @BeforeEach
    public void setUp() throws Exception {
        AnalyticsConfig.resetToDefaults();
        try (ServerSocket socket = new ServerSocket(0)) {
            webPort = socket.getLocalPort();
        }
        AnalyticsConfig.setWebServerHost("127.0.0.1");
        AnalyticsConfig.setWebServerPort(webPort);
        Path dbPath = tempDir.resolve("web_test.db");
        engine = new SqliteAnalyticsStorageEngine();
        engine.initialize(dbPath);

        AnalyticsQueryService.INSTANCE.setStorageEngineOverride(engine);
        AnalyticsQueryService.INSTANCE.clearCache();

        long now = System.currentTimeMillis() / 1000L;
        UUID p1 = UUID.randomUUID();
        groupOwnerUuid = UUID.randomUUID();
        AnalyticsEventQueue.SessionStartEvent s1 = new AnalyticsEventQueue.SessionStartEvent(
                UUID.randomUUID(), p1, "WebPlayer", now - 1000L);
        PlayerActivityBucket b1 = new PlayerActivityBucket(
                now - 500L, p1, "minecraft:overworld", null, 300, 100.0, 10, 5, 2, 1, 0, 0, 100.0, 1);
        DetectorActivityBucket detector = new DetectorActivityBucket(
                now - 500L,
                "minecraft:overworld",
                "web_detector",
                "北門<script>",
                groupOwnerUuid,
                3.0,
                2.0,
                1,
                2,
                1
        );

        engine.writeBatch(List.of(
                s1,
                new AnalyticsEventQueue.PlayerActivityFlushEvent(List.of(b1)),
                new AnalyticsEventQueue.DetectorActivityFlushEvent(List.of(detector)),
                new AnalyticsEventQueue.PerformanceSampleEvent(
                        new ServerPerformanceSample(now - 60L, 19.8D, 49.1D, 250, 5),
                        List.of(new ChunkLoadSample(now - 60L, "minecraft:overworld", 3, 4, 9, 6, 24.0D))),
                new AnalyticsEventQueue.ChunkProfileEvent(List.of(
                        new ChunkProfileRecord(UUID.randomUUID(), now - 50L, now - 40L,
                                "automatic", "minecraft:overworld", 3, 4, 10,
                                2.0D, 6.0D, 20.0D, 8.0D, 9.0D, 3.0D, 20L, 12L, 4L)))
        ));

        AnalyticsWebServer.INSTANCE.start();
    }

    @AfterEach
    public void tearDown() throws Exception {
        AnalyticsWebServer.INSTANCE.stop();
        AnalyticsConfig.resetToDefaults();
        if (engine != null && engine.isOpen()) {
            engine.close();
        }
    }

    @Test
    public void testGetIndexHtmlWithoutAuth() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri("/"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("MoveEarth Analytics Dashboard"));
        assertTrue(response.body().contains("escapeHtml(d.detectorName)"));
    }

    @Test
    public void testGetApiUnauthorizedWithoutToken() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri("/api/players?window=7d&limit=10"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("Unauthorized"));
        assertTrue(response.body().contains("AUTH_TOKEN_MISSING"));
    }

    @Test
    public void testQueryTokenIsRejectedAndNotAcceptedAsAuthentication() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri("/api/health?token=stale-token"))
                .GET().build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("AUTH_TOKEN_MISSING"));
    }

    @Test
    public void testGetPlayersApiWithBearerToken() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri("/api/players?window=7d&limit=10"))
                .header("Authorization", "Bearer " + AnalyticsConfig.getAuthToken())
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("WebPlayer"));
    }

    @Test
    public void testGetOverviewApiWithBearerToken() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri("/api/overview?window=7d"))
                .header("Authorization", "Bearer " + AnalyticsConfig.getAuthToken())
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("activeUniquePlayers"));
    }

    @Test
    public void testGetHealthApiWithToken() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri("/api/health"))
                .header("Authorization", "Bearer " + AnalyticsConfig.getAuthToken())
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("queueDepth"));
    }

    @Test
    public void testPerformanceAndChunkLoadApis() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String token = AnalyticsConfig.getAuthToken();
        HttpResponse<String> performance = client.send(HttpRequest.newBuilder()
                        .uri(uri("/api/performance?window=7d"))
                        .header("Authorization", "Bearer " + token)
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, performance.statusCode());
        assertTrue(performance.body().contains("\"tps\":19.8"));

        HttpResponse<String> chunks = client.send(HttpRequest.newBuilder()
                        .uri(uri("/api/chunks?window=7d&dimension=minecraft%3Aoverworld"))
                        .header("Authorization", "Bearer " + token)
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, chunks.statusCode());
        assertTrue(chunks.body().contains("\"summaries\""));
        assertTrue(chunks.body().contains("\"chunkX\":3"));

        HttpResponse<String> profiles = client.send(HttpRequest.newBuilder()
                        .uri(uri("/api/profiles?window=7d"))
                        .header("Authorization", "Bearer " + token)
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, profiles.statusCode());
        assertTrue(profiles.body().contains("\"status\""));
        assertTrue(profiles.body().contains("\"maximumTickMs\":6.0"));
    }

    @Test
    public void testLimitClampingAndGroupsApi() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        // 負数や巨大値のlimitを指定しても正常に200が返る
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri("/api/players?window=7d&limit=-1"))
                .header("Authorization", "Bearer " + AnalyticsConfig.getAuthToken())
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());

        // Groups API
        HttpRequest groupReq = HttpRequest.newBuilder()
                .uri(uri("/api/groups?window=7d"))
                .header("Authorization", "Bearer " + AnalyticsConfig.getAuthToken())
                .GET()
                .build();
        HttpResponse<String> groupResp = client.send(groupReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, groupResp.statusCode());
    }

    @Test
    public void testDetectorApiReturnsNamedDetectorAndRejectsInvalidUuid() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String token = AnalyticsConfig.getAuthToken();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri("/api/detectors?group=" + groupOwnerUuid + "&window=7d"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("北門\\u003cscript\\u003e"));

        HttpRequest invalid = HttpRequest.newBuilder()
                .uri(uri("/api/detectors?group=invalid"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        assertEquals(400, client.send(invalid, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    public void testRateLimitExceeded() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String token = AnalyticsConfig.getAuthToken();

        // 60件を同時に送る。秒の境目を跨いでもどちらかの秒に21件以上が入るので必ず制限を超える
        // (逐次送信だと遅い環境では1秒あたり20件に届かず、テストが不安定になっていた)。
        List<java.util.concurrent.CompletableFuture<HttpResponse<String>>> burst = new java.util.ArrayList<>();
        for (int i = 0; i < 60; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri("/api/health"))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            burst.add(client.sendAsync(request, HttpResponse.BodyHandlers.ofString()));
        }
        boolean got429 = false;
        for (var pending : burst) {
            HttpResponse<String> response = pending.get(30, java.util.concurrent.TimeUnit.SECONDS);
            if (response.statusCode() == 429) {
                got429 = true;
                assertTrue(response.body().contains("Too Many Requests"));
            }
        }
        assertTrue(got429, "20 req/sec を超えた場合に 429 Too Many Requests が返却されるべき");
    }

    @Test
    public void seasonTwoEndpointsServeEventsFunnelAndState() throws Exception {
        long now = System.currentTimeMillis() / 1000L;
        UUID player = UUID.randomUUID();
        engine.writeBatch(List.of(
                new AnalyticsEventQueue.GameEventLogEvent(new com.ruskserver.moveearth_addtional.analytics.event
                        .GameEventRecord(now - 30L, "onboarding.wilderness", player, null, null, null, null, null,
                        0L, null)),
                new AnalyticsEventQueue.GameEventLogEvent(new com.ruskserver.moveearth_addtional.analytics.event
                        .GameEventRecord(now - 20L, "siege.core_fallen", null, UUID.randomUUID(),
                        "minecraft:overworld", 10, 64, 10, 3L, "attacker=x"))));

        HttpResponse<String> events = get("/api/events?window=1d&type=siege.");
        assertEquals(200, events.statusCode());
        assertTrue(events.body().contains("siege.core_fallen"));
        assertFalse(events.body().contains("onboarding.wilderness"));

        HttpResponse<String> aggregate = get("/api/events/aggregate?window=7d&group_by=type");
        assertEquals(200, aggregate.statusCode());
        assertTrue(aggregate.body().contains("\"key\":\"onboarding.wilderness\""));

        HttpResponse<String> onboarding = get("/api/onboarding?window=7d");
        assertEquals(200, onboarding.statusCode());
        assertTrue(onboarding.body().contains("\"newPlayers\":1"));

        assertEquals(200, get("/api/s2").statusCode());
        assertTrue(get("/api/s2").body().contains("\"eventTypes\""));
        assertEquals(200, get("/api/wars?window=7d").statusCode());
        assertEquals(200, get("/api/economy?window=30d").statusCode());
        assertEquals(200, get("/api/nations/history?window=7d").statusCode());
        assertEquals(400, get("/api/events?nation=not-a-uuid").statusCode());
    }

    private HttpResponse<String> get(String pathAndQuery) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                .uri(uri(pathAndQuery))
                .header("Authorization", "Bearer " + AnalyticsConfig.getAuthToken())
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String pathAndQuery) {
        return URI.create("http://127.0.0.1:" + webPort + pathAndQuery);
    }
}
