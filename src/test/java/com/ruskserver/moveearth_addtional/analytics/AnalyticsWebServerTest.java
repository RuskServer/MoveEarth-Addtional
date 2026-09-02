package com.ruskserver.moveearth_addtional.analytics;

import com.ruskserver.moveearth_addtional.analytics.config.AnalyticsConfig;
import com.ruskserver.moveearth_addtional.analytics.model.PlayerActivityBucket;
import com.ruskserver.moveearth_addtional.analytics.model.DetectorActivityBucket;
import com.ruskserver.moveearth_addtional.analytics.query.AnalyticsQueryService;
import com.ruskserver.moveearth_addtional.analytics.queue.AnalyticsEventQueue;
import com.ruskserver.moveearth_addtional.analytics.storage.SqliteAnalyticsStorageEngine;
import com.ruskserver.moveearth_addtional.analytics.web.AnalyticsWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
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

    @BeforeEach
    public void setUp() throws Exception {
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
                new AnalyticsEventQueue.DetectorActivityFlushEvent(List.of(detector))
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
                .uri(URI.create("http://127.0.0.1:8080/"))
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
                .uri(URI.create("http://127.0.0.1:8080/api/players?window=7d&limit=10"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("Unauthorized"));
    }

    @Test
    public void testGetPlayersApiWithBearerToken() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:8080/api/players?window=7d&limit=10"))
                .header("Authorization", "Bearer " + AnalyticsConfig.getAuthToken())
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("WebPlayer"));
    }

    @Test
    public void testGetOverviewApiWithQueryToken() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:8080/api/overview?window=7d&token=" + AnalyticsConfig.getAuthToken()))
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
                .uri(URI.create("http://127.0.0.1:8080/api/health?token=" + AnalyticsConfig.getAuthToken()))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("queueDepth"));
    }

    @Test
    public void testLimitClampingAndGroupsApi() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        // 負数や巨大値のlimitを指定しても正常に200が返る
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:8080/api/players?window=7d&limit=-1&token=" + AnalyticsConfig.getAuthToken()))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());

        // Groups API
        HttpRequest groupReq = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:8080/api/groups?window=7d&token=" + AnalyticsConfig.getAuthToken()))
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
                .uri(URI.create("http://127.0.0.1:8080/api/detectors?group="
                        + groupOwnerUuid + "&window=7d&token=" + token))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("北門\\u003cscript\\u003e"));

        HttpRequest invalid = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:8080/api/detectors?group=invalid&token=" + token))
                .GET()
                .build();
        assertEquals(400, client.send(invalid, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    public void testRateLimitExceeded() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String token = AnalyticsConfig.getAuthToken();

        boolean got429 = false;
        // 40回連続リクエスト（秒跨ぎがあっても確実に20req/secを超過させる）
        for (int i = 0; i < 40; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:8080/api/health?token=" + token))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                got429 = true;
                assertTrue(response.body().contains("Too Many Requests"));
                break;
            }
        }
        assertTrue(got429, "20 req/sec を超えた場合に 429 Too Many Requests が返却されるべき");
    }
}
