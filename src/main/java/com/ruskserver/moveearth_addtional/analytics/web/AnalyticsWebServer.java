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

import java.io.ByteArrayOutputStream;
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
import java.util.concurrent.Executors;

/**
 * プレイヤー分析WebダッシュボードおよびREST APIを提供する軽量内蔵HTTPサーバー
 */
public class AnalyticsWebServer {

    public static final AnalyticsWebServer INSTANCE = new AnalyticsWebServer();

    private static final Gson GSON = new GsonBuilder().create();
    private HttpServer server;

    public AnalyticsWebServer() {
    }

    public synchronized void start() {
        start(AnalyticsConfig.WEB_SERVER_PORT);
    }

    public synchronized void start(int port) {
        if (!AnalyticsConfig.WEB_SERVER_ENABLED || server != null) {
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new StaticDashboardHandler());
            server.createContext("/api/players", new PlayersApiHandler());
            server.createContext("/api/player", new PlayerDetailApiHandler());
            server.createContext("/api/groups", new GroupsApiHandler());
            server.createContext("/api/heatmap", new HeatmapApiHandler());
            server.createContext("/api/health", new HealthApiHandler());
            server.createContext("/api/export", new ExportApiHandler());

            server.setExecutor(Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "MoveEarth-Analytics-Web-Worker");
                t.setDaemon(true);
                return t;
            }));

            server.start();
            System.out.println("[MoveEarth] プレイヤー分析Webダッシュボードを開始しました: http://localhost:" + port);
        } catch (Exception e) {
            System.err.println("[MoveEarth] Webダッシュボードの起動に失敗しました: " + e.getMessage());
        }
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
            System.out.println("[MoveEarth] プレイヤー分析Webダッシュボードを停止しました。");
        }
    }

    public boolean isRunning() {
        return server != null;
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

    private static class PlayersApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQueryParams(exchange.getRequestURI());
            TimeWindow window = parseWindow(params.get("window"));
            int limit = parseInt(params.get("limit"), 100);

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
            Map<String, String> params = parseQueryParams(exchange.getRequestURI());
            TimeWindow window = parseWindow(params.get("window"));

            try {
                // サンプルまたはトップアクティブプレイヤーの拠点一覧を返却
                var topPlayers = AnalyticsQueryService.INSTANCE.getTopActivePlayersAsync(window, 50).get();
                List<Object> groups = new ArrayList<>();
                Set<UUID> seenOwners = new HashSet<>();

                for (var p : topPlayers) {
                    if (p.primaryGroupOwnerUuid() != null && seenOwners.add(p.primaryGroupOwnerUuid())) {
                        AnalyticsQueryService.INSTANCE.getGroupSummaryAsync(p.primaryGroupOwnerUuid(), window).get()
                                .ifPresent(groups::add);
                    }
                }

                sendResponse(exchange, 200, GSON.toJson(groups), "application/json; charset=UTF-8");
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}", "application/json; charset=UTF-8");
            }
        }
    }

    private static class HeatmapApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQueryParams(exchange.getRequestURI());
            String dim = params.getOrDefault("dimension", "minecraft:overworld");
            TimeWindow window = parseWindow(params.get("window"));
            int limit = parseInt(params.get("limit"), 50);

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
            try {
                var health = AnalyticsQueryService.INSTANCE.getCollectorHealthAsync().get();
                sendResponse(exchange, 200, GSON.toJson(health), "application/json; charset=UTF-8");
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}", "application/json; charset=UTF-8");
            }
        }
    }

    private class ExportApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQueryParams(exchange.getRequestURI());
            String formatStr = params.getOrDefault("format", "csv").toLowerCase();
            TimeWindow window = parseWindow(params.get("window"));

            AnalyticsExportService.ExportFormat format = "jsonl".equals(formatStr)
                    ? AnalyticsExportService.ExportFormat.JSONL
                    : AnalyticsExportService.ExportFormat.CSV;

            try {
                Path tempExportDir = Files.createTempDirectory("me_analytics_web_export");
                Path exportFile = AnalyticsExportService.INSTANCE.exportPlayersToDirAsync(tempExportDir, format, window).get();

                byte[] data = Files.readAllBytes(exportFile);
                exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + exportFile.getFileName() + "\"");
                sendBinaryResponse(exchange, 200, data, "application/octet-stream");

                Files.deleteIfExists(exportFile);
                Files.deleteIfExists(tempExportDir);
            } catch (Exception e) {
                sendResponse(exchange, 500, "Export failed: " + e.getMessage(), "text/plain; charset=UTF-8");
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
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
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

    private static int parseInt(String val, int def) {
        if (val == null) return def;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
