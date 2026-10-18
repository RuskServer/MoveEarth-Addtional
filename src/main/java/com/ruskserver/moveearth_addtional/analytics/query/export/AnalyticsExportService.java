package com.ruskserver.moveearth_addtional.analytics.query.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ruskserver.moveearth_addtional.analytics.query.AnalyticsQueryService;
import com.ruskserver.moveearth_addtional.analytics.model.ChunkLoadSample;
import com.ruskserver.moveearth_addtional.analytics.model.ServerPerformanceSample;
import com.ruskserver.moveearth_addtional.analytics.model.ChunkProfileRecord;
import com.ruskserver.moveearth_addtional.analytics.query.dto.GameEventDto;
import com.ruskserver.moveearth_addtional.analytics.query.dto.PlayerSummaryDto;
import com.ruskserver.moveearth_addtional.analytics.query.dto.TimeWindow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * プレイヤー分析データをJSONLまたはCSV形式でファイルへ非同期エクスポートするサービス
 */
public class AnalyticsExportService {

    public static final AnalyticsExportService INSTANCE = new AnalyticsExportService();

    private static final Gson GSON = new GsonBuilder().create();
    private static final java.time.format.DateTimeFormatter FILE_DATE_FMT =
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    /** 大きな書き出しは 1 本ずつ、サーバースレッドとも共通プールとも別の場所で行う。 */
    private static final ExecutorService EXPORT_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MoveEarth-Analytics-Export");
        thread.setDaemon(true);
        return thread;
    });

    /** 書き出した 1 ファイル。 */
    public record TableExport(Path path, long rows, boolean truncated) { }

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
                        String timestamp = FILE_DATE_FMT.format(java.time.LocalDateTime.now());
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

    public CompletableFuture<Path> exportGameEventsToDirAsync(
            Path exportDir, ExportFormat format, TimeWindow window, String typePrefix) {
        return AnalyticsQueryService.INSTANCE.getGameEventsAsync(typePrefix, null, null, window, 200_000)
                .thenApplyAsync(events -> writeExport(exportDir, format, window, "game_events",
                        writer -> writeGameEvents(writer, events, format)));
    }

    /** 許可リストの生テーブル 1 つを書き出す。 */
    public CompletableFuture<TableExport> exportTableToDirAsync(
            Path exportDir, ExportFormat format, TimeWindow window, ExportTable table) {
        return CompletableFuture.supplyAsync(() -> writeTable(exportDir, format, window, table), EXPORT_EXECUTOR);
    }

    /**
     * 書き出せるものすべてを 1 つの ZIP にまとめる: プレイヤー集計、TPS、負荷チャンク、実測 CPU、
     * ゲームイベント、それに許可リストの全テーブル。中身の一覧と切り詰めの有無を README.txt に書く。
     */
    public CompletableFuture<Path> exportAllToZipAsync(Path exportDir, ExportFormat format, TimeWindow window) {
        return CompletableFuture.supplyAsync(() -> {
            Path work = null;
            try {
                Files.createDirectories(exportDir);
                work = Files.createTempDirectory(exportDir, ".all_");
                List<String> notes = new ArrayList<>();
                notes.add("MoveEarth analytics export / 期間: " + window.getId() + " / 形式: " + format.getExtension());
                notes.add("");
                notes.add(exportPlayersToDirAsync(work, format, window).join().getFileName() + "  プレイヤー集計");
                notes.add(exportPerformanceToDirAsync(work, format, window).join().getFileName() + "  TPS/MSPT");
                notes.add(exportChunkLoadsToDirAsync(work, format, window, "all").join().getFileName() + "  負荷チャンク");
                notes.add(exportChunkProfilesToDirAsync(work, format, window).join().getFileName() + "  実測CPU");
                notes.add(exportGameEventsToDirAsync(work, format, window, null).join().getFileName() + "  ゲームイベント");
                for (ExportTable table : ExportTable.values()) {
                    TableExport written = writeTable(work, format, window, table);
                    notes.add(written.path().getFileName() + "  " + table.label() + "  " + written.rows() + "行"
                            + (written.truncated() ? "（上限" + ExportTable.ROW_LIMIT + "行で切り詰め）" : ""));
                }
                Files.write(work.resolve("README.txt"), notes, StandardCharsets.UTF_8);
                Path zip = exportDir.resolve("analytics_all_" + window.getId() + "_" + FILE_DATE_FMT.format(java.time.LocalDateTime.now()) + ".zip");
                zipDirectory(work, zip);
                return zip;
            } catch (Exception e) {
                throw new RuntimeException("エクスポートの書き出しに失敗しました: " + e.getMessage(), e);
            } finally {
                deleteQuietly(work);
            }
        }, EXPORT_EXECUTOR);
    }

    private TableExport writeTable(Path exportDir, ExportFormat format, TimeWindow window, ExportTable table) {
        long[] rows = {0L};
        boolean[] truncated = {false};
        Path path = writeExport(exportDir, format, window, table.id(), writer -> {
            truncated[0] = AnalyticsQueryService.INSTANCE.exportTable(table, window, ExportTable.ROW_LIMIT,
                    new ExportRowSink() {
                        private List<String> columns = List.of();

                        @Override
                        public void columns(List<String> names) throws Exception {
                            columns = names;
                            if (format == ExportFormat.CSV && !names.isEmpty()) {
                                writer.write(String.join(",", names));
                                writer.newLine();
                            }
                        }

                        @Override
                        public void row(Object[] values) throws Exception {
                            rows[0]++;
                            if (format == ExportFormat.JSONL) {
                                Map<String, Object> record = new LinkedHashMap<>();
                                for (int i = 0; i < values.length; i++) record.put(columns.get(i), values[i]);
                                writer.write(GSON.toJson(record));
                            } else {
                                StringBuilder line = new StringBuilder();
                                for (int i = 0; i < values.length; i++) {
                                    if (i > 0) line.append(',');
                                    line.append(csvValue(values[i]));
                                }
                                writer.write(line.toString());
                            }
                            writer.newLine();
                        }
                    });
        });
        return new TableExport(path, rows[0], truncated[0]);
    }

    private String csvValue(Object value) {
        if (value == null) return "";
        if (value instanceof Double || value instanceof Float) {
            return String.format(Locale.ROOT, "%.6f", ((Number) value).doubleValue());
        }
        return escapeCsv(value.toString());
    }

    private static void zipDirectory(Path dir, Path zip) throws Exception {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip));
             Stream<Path> files = Files.list(dir)) {
            for (Path file : files.sorted().toList()) {
                out.putNextEntry(new ZipEntry(file.getFileName().toString()));
                Files.copy(file, out);
                out.closeEntry();
            }
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) return;
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private Path writeExport(Path exportDir, ExportFormat format, TimeWindow window,
                             String prefix, ExportWriter exportWriter) {
        try {
            Files.createDirectories(exportDir);
            String timestamp = FILE_DATE_FMT.format(java.time.LocalDateTime.now());
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

    private void writeGameEvents(BufferedWriter writer, List<GameEventDto> events,
                                 ExportFormat format) throws Exception {
        if (format == ExportFormat.JSONL) {
            for (GameEventDto event : events) {
                writer.write(GSON.toJson(event));
                writer.newLine();
            }
            return;
        }
        writer.write("occurred_at,type,player_uuid,player_name,nation_uuid,dimension,x,y,z,value,detail");
        writer.newLine();
        for (GameEventDto event : events) {
            writer.write(String.join(",",
                    Long.toString(event.occurredAt()), escapeCsv(event.type()), escapeCsv(event.playerUuid()),
                    escapeCsv(event.playerName()), escapeCsv(event.nationUuid()), escapeCsv(event.dimension()),
                    event.x() == null ? "" : event.x().toString(), event.y() == null ? "" : event.y().toString(),
                    event.z() == null ? "" : event.z().toString(), Long.toString(event.value()),
                    escapeCsv(event.detail())));
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
        if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }
}
