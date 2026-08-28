package com.ruskserver.moveearth_addtional.analytics.query.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ruskserver.moveearth_addtional.analytics.query.AnalyticsQueryService;
import com.ruskserver.moveearth_addtional.analytics.query.dto.PlayerSummaryDto;
import com.ruskserver.moveearth_addtional.analytics.query.dto.TimeWindow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * プレイヤー分析データをJSONLまたはCSV形式でファイルへ非同期エクスポートするサービス
 */
public class AnalyticsExportService {

    public static final AnalyticsExportService INSTANCE = new AnalyticsExportService();

    private static final Gson GSON = new GsonBuilder().create();
    private static final SimpleDateFormat FILE_DATE_FMT = new SimpleDateFormat("yyyyMMdd_HHmmss");

    public enum ExportFormat {
        JSONL("jsonl"),
        CSV("csv");

        private final String extension;

        ExportFormat(String extension) {
            this.extension = extension;
        }

        public String getExtension() {
            return extension;
        }
    }

    public CompletableFuture<Path> exportPlayersAsync(MinecraftServer server, ExportFormat format, TimeWindow window) {
        Path exportDir = server.getWorldPath(LevelResource.ROOT).resolve("moveearth/analytics/exports");
        return exportPlayersToDirAsync(exportDir, format, window);
    }

    public CompletableFuture<Path> exportPlayersToDirAsync(Path exportDir, ExportFormat format, TimeWindow window) {
        return AnalyticsQueryService.INSTANCE.getTopActivePlayersAsync(window, 1000)
                .thenApplyAsync(players -> {
                    try {
                        Files.createDirectories(exportDir);
                        String timestamp = FILE_DATE_FMT.format(new Date());
                        String fileName = "player_analytics_" + window.getId() + "_" + timestamp + "." + format.getExtension();
                        Path outputPath = exportDir.resolve(fileName);

                        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
                            if (format == ExportFormat.JSONL) {
                                writePlayersJsonl(writer, players);
                            } else {
                                writePlayersCsv(writer, players);
                            }
                        }
                        return outputPath;
                    } catch (Exception e) {
                        throw new RuntimeException("エクスポートの書き出しに失敗しました: " + e.getMessage(), e);
                    }
                });
    }

    private void writePlayersJsonl(BufferedWriter writer, List<PlayerSummaryDto> players) throws Exception {
        for (PlayerSummaryDto p : players) {
            writer.write(GSON.toJson(p));
            writer.newLine();
        }
    }

    private void writePlayersCsv(BufferedWriter writer, List<PlayerSummaryDto> players) throws Exception {
        writer.write("uuid,name,first_seen,last_seen,sessions,online_sec,active_sec,afk_sec,breaks,places,crafts,pve_kills,pvp_kills,deaths,jobs_xp,tpa_count,distance_m,primary_dimension,primary_group");
        writer.newLine();

        for (PlayerSummaryDto p : players) {
            writer.write(String.format("%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.2f,%d,%.2f,%s,%s",
                    p.playerUuid(),
                    escapeCsv(p.lastKnownName()),
                    p.firstSeenAtEpochSec(),
                    p.lastSeenAtEpochSec(),
                    p.sessionCount(),
                    p.totalOnlineSeconds(),
                    p.totalActiveSeconds(),
                    p.totalAfkSeconds(),
                    p.totalBreaks(),
                    p.totalPlaces(),
                    p.totalCrafts(),
                    p.totalPveKills(),
                    p.totalPvpKills(),
                    p.totalDeaths(),
                    p.totalJobsXp(),
                    p.totalTpaSuccesses(),
                    p.totalDistanceBlocks(),
                    p.primaryDimension(),
                    p.primaryGroupOwnerUuid() != null ? p.primaryGroupOwnerUuid().toString() : ""
            ));
            writer.newLine();
        }
    }

    private String escapeCsv(String str) {
        if (str == null) return "";
        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }
}
