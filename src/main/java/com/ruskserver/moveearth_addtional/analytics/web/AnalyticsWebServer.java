package com.ruskserver.moveearth_addtional.analytics.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ruskserver.moveearth_addtional.analytics.config.AnalyticsConfig;
import com.ruskserver.moveearth_addtional.analytics.query.AnalyticsQueryService;
import com.ruskserver.moveearth_addtional.analytics.query.dto.TimeWindow;
import com.ruskserver.moveearth_addtional.analytics.query.export.AnalyticsExportService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * プレイヤー分析WebダッシュボードおよびREST APIを提供する軽量内蔵HTTPサーバー
 * (127.0.0.1限定バインド・トークン認証・レート制限・CORS制限)
 */
public class AnalyticsWebServer {

    public static final AnalyticsWebServer INSTANCE = new AnalyticsWebServer();

    private static final Gson GSON = new GsonBuilder().create();
    private HttpServer server;

    // --- レート制限（1秒間に最大20リクエスト） ---
    private static final int MAX_REQUESTS_PER_SECOND = 20;
    private static final int MAX_RATE_LIMIT_CLIENTS = 4096;
    private static final long RATE_LIMIT_TTL_MILLIS = 10 * 60 * 1000L;
    private static final Map<String, RateLimitTracker> rateLimitMap = new ConcurrentHashMap<>();

    private static class RateLimitTracker {
        long currentSecond = 0L;
        AtomicInteger count = new AtomicInteger(0);
        volatile long lastSeenMillis;

        synchronized boolean allowRequest(long nowSec) {
            if (nowSec != currentSecond) {
                currentSecond = nowSec;
                count.set(1);
                return true;
            }
            return count.incrementAndGet() <= MAX_REQUESTS_PER_SECOND;
        }
    }

    public AnalyticsWebServer() {
    }

    public synchronized void start() {
        start(AnalyticsConfig.getWebServerPort());
    }

    public synchronized void start(int port) {
        if (!AnalyticsConfig.isWebServerEnabled() || server != null) {
            return;
        }

        try {
            String host = AnalyticsConfig.getWebServerHost();
            server = HttpServer.create(new InetSocketAddress(host, port), 0);

            server.createContext("/", new StaticDashboardHandler());
            server.createContext("/api/overview", new OverviewApiHandler());
            server.createContext("/api/players", new PlayersApiHandler());
            server.createContext("/api/player", new PlayerDetailApiHandler());
            server.createContext("/api/groups", new GroupsApiHandler());
            server.createContext("/api/detectors", new DetectorsApiHandler());
            server.createContext("/api/heatmap", new HeatmapApiHandler());
            server.createContext("/api/health", new HealthApiHandler());
            server.createContext("/api/export", new ExportApiHandler());

            server.setExecutor(Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "MoveEarth-Analytics-Web-Worker");
                t.setDaemon(true);
                return t;
            }));

            server.start();
            System.out.println("[MoveEarth] プレイヤー分析Webダッシュボードを開始しました: http://" + host + ":" + port);
        } catch (Exception e) {
            System.err.println("[MoveEarth] Webダッシュボードの起動に失敗しました: " + e.getMessage());
        }
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
            rateLimitMap.clear();
            System.out.println("[MoveEarth] プレイヤー分析Webダッシュボードを停止しました。");
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    // --- 認証およびレート制限チェック ---

    private static boolean checkAuthAndRateLimit(HttpExchange exchange) throws IOException {
        String clientKey = exchange.getRemoteAddress() != null
                ? exchange.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";

        long nowMillis = System.currentTimeMillis();
        long nowSec = nowMillis / 1000L;
        rateLimitMap.entrySet().removeIf(e -> nowMillis - e.getValue().lastSeenMillis > RATE_LIMIT_TTL_MILLIS);
        if (rateLimitMap.size() >= MAX_RATE_LIMIT_CLIENTS && !rateLimitMap.containsKey(clientKey)) {
            sendResponse(exchange, 429, "{\"error\":\"Too many clients\"}", "application/json; charset=UTF-8");
            return false;
        }
        RateLimitTracker tracker = rateLimitMap.computeIfAbsent(clientKey, k -> new RateLimitTracker());
        tracker.lastSeenMillis = nowMillis;
        if (!tracker.allowRequest(nowSec)) {
            sendResponse(exchange, 429, "{\"error\":\"Too Many Requests. Rate limit exceeded (20 req/sec).\"}", "application/json; charset=UTF-8");
            return false;
        }

        if (!AnalyticsConfig.isWebServerRequireAuth()) {
            return true;
        }

        String expectedToken = AnalyticsConfig.getAuthToken();
        if (expectedToken == null || expectedToken.isEmpty()) {
            return true;
        }

        // 1. Authorization: Bearer <token>
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring("Bearer ".length()).trim();
            if (expectedToken.equals(token)) {
                return true;
            }
        }

        // 2. Query param ?token=<token>
        Map<String, String> params = parseQueryParams(exchange.getRequestURI());
        String tokenParam = params.get("token");
        if (expectedToken.equals(tokenParam)) {
            return true;
        }

        sendResponse(exchange, 401, "{\"error\":\"Unauthorized. Valid token required.\"}", "application/json; charset=UTF-8");
        return false;
    }

    // --- 各種ハンドラー ---

    private static class StaticDashboardHandler implements HttpHandler {
        private byte[] cachedHtml;

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
                return;
            }

            if (cachedHtml == null) {
                try (InputStream is = getClass().getResourceAsStream("/assets/moveearth_addtional/web/index.html")) {
                    if (is != null) {
                        cachedHtml = is.readAllBytes();
                    }
                }
            }

            if (cachedHtml != null) {
                sendBinaryResponse(exchange, 200, cachedHtml, "text/html; charset=UTF-8");
            } else {
                sendResponse(exchange, 404, "Dashboard HTML resource not found.", "text/plain");
            }
        }
    }

    private static class OverviewApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuthAndRateLimit(exchange)) return;

            Map<String, String> params = parseQueryParams(exchange.getRequestURI());
            TimeWindow window = parseWindow(params.get("window"));

            try {
                var overview = AnalyticsQueryService.INSTANCE.getOverviewSummaryAsync(window).get();
                sendResponse(exchange, 200, GSON.toJson(overview), "application/json; charset=UTF-8");
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}", "application/json; charset=UTF-8");
            }
        }
    }

    private static class PlayersApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuthAndRateLimit(exchange)) return;

            Map<String, String> params = parseQueryParams(exchange.getRequestURI());
            TimeWindow window = parseWindow(params.get("window"));
            int limit = clampInt(params.get("limit"), 100, 1, 100);

            try {
                var players = AnalyticsQueryService.INSTANCE.getTopActivePlayersAsync(window, limit).get();
                String json = GSON.toJson(players);
                sendResponse(exchange, 200, json, "application/json; charset=UTF-8");
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}", "application/json; charset=UTF-8");
            }
        }
    }

    private static class PlayerDetailApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuthAndRateLimit(exchange)) return;

            Map<String, String> params = parseQueryParams(exchange.getRequestURI());
            String uuidStr = params.get("uuid");
            TimeWindow window = parseWindow(params.get("window"));

            if (uuidStr == null) {
                sendResponse(exchange, 400, "{\"error\":\"Missing uuid parameter\"}", "application/json; charset=UTF-8");
                return;
            }

            try {
                UUID uuid = UUID.fromString(uuidStr);
                var player = AnalyticsQueryService.INSTANCE.getPlayerSummaryAsync(uuid, window).get();
                if (player.isPresent()) {
                    sendResponse(exchange, 200, GSON.toJson(player.get()), "application/json; charset=UTF-8");
                } else {
                    sendResponse(exchange, 404, "{\"error\":\"Player not found\"}", "application/json; charset=UTF-8");
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}", "application/json; charset=UTF-8");
            }
        }
    }

    private static class GroupsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuthAndRateLimit(exchange)) return;

            Map<String, String> params = parseQueryParams(exchange.getRequestURI());
            TimeWindow window = parseWindow(params.get("window"));

            try {
                var groups = AnalyticsQueryService.INSTANCE.getAllGroupSummariesAsync(window).get();
                sendResponse(exchange, 200, GSON.toJson(groups), "application/json; charset=UTF-8");
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}", "application/json; charset=UTF-8");
            }
        }
    }

    private static class DetectorsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuthAndRateLimit(exchange)) return;

            Map<String, String> params = parseQueryParams(exchange.getRequestURI());
            String group = params.get("group");
            if (group == null) {
                sendResponse(exchange, 400, "{\"error\":\"Missing group UUID\"}", "application/json; charset=UTF-8");
                return;
            }

            try {
                UUID groupOwnerUuid = UUID.fromString(group);
                TimeWindow window = parseWindow(params.get("window"));
                var detectors = AnalyticsQueryService.INSTANCE
                        .getDetectorSummariesAsync(groupOwnerUuid, window)
                        .get();
                sendResponse(exchange, 200, GSON.toJson(detectors), "application/json; charset=UTF-8");
            } catch (IllegalArgumentException e) {
                sendResponse(exchange, 400, "{\"error\":\"Invalid group UUID\"}", "application/json; charset=UTF-8");
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}", "application/json; charset=UTF-8");
            }
        }
    }

    private static class HeatmapApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuthAndRateLimit(exchange)) return;

            Map<String, String> params = parseQueryParams(exchange.getRequestURI());
            String dim = params.getOrDefault("dimension", "minecraft:overworld");
            TimeWindow window = parseWindow(params.get("window"));
            int limit = clampInt(params.get("limit"), 100, 1, 500);

            try {
                var cells = AnalyticsQueryService.INSTANCE.getSpatialHeatmapAsync(dim, window, limit).get();
                sendResponse(exchange, 200, GSON.toJson(cells), "application/json; charset=UTF-8");
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}", "application/json; charset=UTF-8");
            }
        }
    }

    private static class HealthApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuthAndRateLimit(exchange)) return;

            try {
                var health = AnalyticsQueryService.INSTANCE.getCollectorHealthAsync().get();
                sendResponse(exchange, 200, GSON.toJson(health), "application/json; charset=UTF-8");
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}", "application/json; charset=UTF-8");
            }
        }
    }

    private static class ExportApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuthAndRateLimit(exchange)) return;

            Map<String, String> params = parseQueryParams(exchange.getRequestURI());
            String formatStr = params.getOrDefault("format", "csv").toLowerCase();
            TimeWindow window = parseWindow(params.get("window"));

            AnalyticsExportService.ExportFormat format = "jsonl".equals(formatStr)
                    ? AnalyticsExportService.ExportFormat.JSONL
                    : AnalyticsExportService.ExportFormat.CSV;

            Path tempExportDir = null;
            Path exportFile = null;
            try {
                tempExportDir = Files.createTempDirectory("me_analytics_web_export");
                exportFile = AnalyticsExportService.INSTANCE.exportPlayersToDirAsync(tempExportDir, format, window).get();

                exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + exportFile.getFileName() + "\"");
                sendFileResponse(exchange, 200, exportFile, "application/octet-stream");
            } catch (Exception e) {
                sendResponse(exchange, 500, "Export failed: " + e.getMessage(), "text/plain; charset=UTF-8");
            } finally {
                if (exportFile != null) {
                    try {
                        Files.deleteIfExists(exportFile);
                    } catch (Exception ignored) {
                    }
                }
                if (tempExportDir != null) {
                    try {
                        Files.deleteIfExists(tempExportDir);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    // --- ヘルパーメソッド ---

    private static void sendResponse(HttpExchange exchange, int statusCode, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        sendBinaryResponse(exchange, statusCode, bytes, contentType);
    }

    private static void sendBinaryResponse(HttpExchange exchange, int statusCode, byte[] data, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);

        // CORS制限: 自ループバックオリジンのみ許可
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && (origin.startsWith("http://localhost:") || origin.startsWith("http://127.0.0.1:"))) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
        }

        exchange.sendResponseHeaders(statusCode, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private static void sendFileResponse(HttpExchange exchange, int statusCode, Path file, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, Files.size(file));
        try (OutputStream output = exchange.getResponseBody(); InputStream input = Files.newInputStream(file)) {
            input.transferTo(output);
        }
    }

    private static Map<String, String> parseQueryParams(URI uri) {
        Map<String, String> map = new HashMap<>();
        String query = uri.getRawQuery();
        if (query == null || query.isEmpty()) return map;

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                map.put(key, val);
            }
        }
        return map;
    }

    private static TimeWindow parseWindow(String val) {
        if (val == null) return TimeWindow.DAYS_7;
        return switch (val.toLowerCase()) {
            case "30d", "30days" -> TimeWindow.DAYS_30;
            case "all", "all_time" -> TimeWindow.ALL_TIME;
            default -> TimeWindow.DAYS_7;
        };
    }

    private static int clampInt(String val, int def, int min, int max) {
        if (val == null) return def;
        try {
            int parsed = Integer.parseInt(val);
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
