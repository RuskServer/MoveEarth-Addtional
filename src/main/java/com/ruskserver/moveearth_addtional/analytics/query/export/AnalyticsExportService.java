package com.ruskserver.moveearth_addtional.analytics.query.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ruskserver.moveearth_addtional.analytics.query.AnalyticsQueryService;
import com.ruskserver.moveearth_addtional.analytics.model.ChunkLoadSample;
import com.ruskserver.moveearth_addtional.analytics.model.ServerPerformanceSample;
import com.ruskserver.moveearth_addtional.analytics.model.ChunkProfileRecord;
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
import java.util.Locale;
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

    public CompletableFuture<Path> exportPerformanceToDirAsync(
            Path exportDir, ExportFormat format, TimeWindow window) {
        return AnalyticsQueryService.INSTANCE.getServerPerformanceAsync(window, 100_000)
                .thenApplyAsync(samples -> writeExport(exportDir, format, window, "server_performance",
                        writer -> writePerformance(writer, samples, format)));
    }

    public CompletableFuture<Path> exportChunkLoadsToDirAsync(
            Path exportDir, ExportFormat format, TimeWindow window, String dimension) {
        return AnalyticsQueryService.INSTANCE.getChunkLoadHistoryAsync(dimension, window, 100_000)
                .thenApplyAsync(samples -> writeExport(exportDir, format, window, "chunk_load",
                        writer -> writeChunkLoads(writer, samples, format)));
    }

    public CompletableFuture<Path> exportChunkProfilesToDirAsync(
            Path exportDir, ExportFormat format, TimeWindow window) {
        return AnalyticsQueryService.INSTANCE.getChunkProfilesAsync(window, 10_000)
                .thenApplyAsync(records -> writeExport(exportDir, format, window, "chunk_profiles",
                        writer -> writeChunkProfiles(writer, records, format)));
    }

    private Path writeExport(Path exportDir, ExportFormat format, TimeWindow window,
                             String prefix, ExportWriter exportWriter) {
        try {
            Files.createDirectories(exportDir);
            String timestamp = FILE_DATE_FMT.format(new Date());
            Path outputPath = exportDir.resolve(prefix + "_" + window.getId() + "_" + timestamp
                    + "." + format.getExtension());
            try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
                exportWriter.write(writer);
            }
            return outputPath;
        } catch (Exception e) {
            throw new RuntimeException("エクスポートの書き出しに失敗しました: " + e.getMessage(), e);
        }
    }

    private void writePerformance(BufferedWriter writer, List<ServerPerformanceSample> samples,
                                  ExportFormat format) throws Exception {
        if (format == ExportFormat.JSONL) {
            for (ServerPerformanceSample sample : samples) {
                writer.write(GSON.toJson(sample));
                writer.newLine();
            }
            return;
        }
        writer.write("recorded_at,tps,mspt,loaded_chunks,online_players");
        writer.newLine();
        for (ServerPerformanceSample sample : samples) {
            writer.write(String.format(Locale.ROOT, "%d,%.3f,%.3f,%d,%d",
                    sample.recordedAtEpochSec(), sample.tps(), sample.mspt(),
                    sample.loadedChunks(), sample.onlinePlayers()));
            writer.newLine();
        }
    }

    private void writeChunkLoads(BufferedWriter writer, List<ChunkLoadSample> samples,
                                 ExportFormat format) throws Exception {
        if (format == ExportFormat.JSONL) {
            for (ChunkLoadSample sample : samples) {
                writer.write(GSON.toJson(sample));
                writer.newLine();
            }
            return;
        }
        writer.write("recorded_at,dimension,chunk_x,chunk_z,entity_count,block_entity_count,load_score");
        writer.newLine();
        for (ChunkLoadSample sample : samples) {
            writer.write(String.format(Locale.ROOT, "%d,%s,%d,%d,%d,%d,%.3f",
                    sample.recordedAtEpochSec(), escapeCsv(sample.dimension()), sample.chunkX(), sample.chunkZ(),
                    sample.entityCount(), sample.blockEntityCount(), sample.loadScore()));
            writer.newLine();
        }
    }

    private void writeChunkProfiles(BufferedWriter writer, List<ChunkProfileRecord> records,
                                    ExportFormat format) throws Exception {
        if (format == ExportFormat.JSONL) {
            for (ChunkProfileRecord record : records) {
                writer.write(GSON.toJson(record));
                writer.newLine();
            }
            return;
        }
        writer.write("session_id,started_at,finished_at,trigger,dimension,chunk_x,chunk_z,sampled_ticks,average_tick_ms,maximum_tick_ms,total_ms,entity_ms,block_entity_ms,scheduled_tick_ms,entity_calls,block_entity_calls,scheduled_tick_calls");
        writer.newLine();
        for (ChunkProfileRecord record : records) {
            writer.write(String.format(Locale.ROOT,
                    "%s,%d,%d,%s,%s,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%d,%d,%d",
                    record.sessionId(), record.startedAtEpochSec(), record.finishedAtEpochSec(),
                    escapeCsv(record.trigger()), escapeCsv(record.dimension()), record.chunkX(), record.chunkZ(),
                    record.sampledTicks(), record.averageTickMs(), record.maximumTickMs(), record.totalMs(),
                    record.entityMs(), record.blockEntityMs(), record.scheduledTickMs(), record.entityCalls(),
                    record.blockEntityCalls(), record.scheduledTickCalls()));
            writer.newLine();
        }
    }

    @FunctionalInterface
    private interface ExportWriter {
        void write(BufferedWriter writer) throws Exception;
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
