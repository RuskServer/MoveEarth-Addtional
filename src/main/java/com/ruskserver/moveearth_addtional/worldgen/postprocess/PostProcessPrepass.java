package com.ruskserver.moveearth_addtional.worldgen.postprocess;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.time.OpenTimeService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs world-generation post-processing ahead of players, while the server is closed.
 *
 * <p>Generation leaves blocks, mostly water at aquifers, carvers and river banks, marked to be updated
 * the first time their chunk runs. Pregeneration only makes chunks, so that work waits for the first
 * player to come near and then lands on the server tick all at once: a profile showed it at 28% of the
 * tick while players flew into new ground, with spikes past a second. This pass does it beforehand.
 *
 * <p>It walks a square spiral over the area. Each saved chunk is checked on disk first, off the server
 * thread, and only fully generated chunks with post-processing left are loaded, never generated: a
 * ticket brings the chunk and its neighbours in, the chunk's post-processing runs within a per-tick
 * budget, the water is given a few seconds to settle, and the ticket goes. How many chunks are in hand
 * at once follows the server's tick time. Progress and the chunks picked but unfinished are saved, so a
 * restart resumes without skipping any. It works only while the server is closed, unless started with
 * {@code now}.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class PostProcessPrepass {
    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("moveearth_postprocess", Comparator.comparingLong(ChunkPos::toLong));
    private static final int READS_PER_TICK = 64;
    private static final int MAX_READS_IN_FLIGHT = 256;
    private static final int MAX_WAITING = 1024;
    private static final long BUDGET_NANOS = 20_000_000L;
    private static final int SETTLE_TICKS = 60;
    private static final int GIVE_UP_TICKS = 1200;
    private static final int MAX_ACTIVE = 48;
    /** Keeps the pass off the area's outermost chunks, whose neighbours may never have been generated. */
    private static final int EDGE_MARGIN = 2;
    private static final int MAX_RADIUS = 2500;

    private static final Queue<Long> READY = new ConcurrentLinkedQueue<>();
    /** Chunks restored from a save, checked on disk again before anything loads them. */
    private static final Queue<Long> RECHECK = new ConcurrentLinkedQueue<>();
    private static final Set<Long> READING = ConcurrentHashMap.newKeySet();
    private static final AtomicLong CHECKED = new AtomicLong();
    private static final Map<Long, Active> ACTIVE = new LinkedHashMap<>();
    private static long processed;
    private static long spentNanos;
    private static long directly;
    private static long slowestNanos;
    private static long slowestChunk;
    private static long failed;
    private static int maxActive = 8;
    private static boolean restored;
    private static boolean waiting;

    private PostProcessPrepass() { }

    private static final class Active {
        final long added;
        long processedAt = -1L;

        Active(long added) {
            this.added = added;
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        PostProcessJobSavedData job = PostProcessJobSavedData.get(server);
        if (!job.active()) return;
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, job.dimension()));
        if (level == null) return;
        if (!restored) {
            RECHECK.addAll(job.pending());
            CHECKED.set(job.checked());
            processed = job.processed();
            failed = job.failed();
            restored = true;
        }
        long tick = server.getTickCount();
        if (!job.anytime() && OpenTimeService.isOpen(server)) {
            // Opening hours: hand everything back and wait for closing time.
            if (!ACTIVE.isEmpty()) release(level, true);
            waiting = true;
            return;
        }
        waiting = false;
        if (tick % 20L == 0L) adapt(server);
        read(level, job);
        activate(level, tick);
        work(level, tick);
        if (tick % 200L == 0L) persist(job);
        if (job.nextIndex() >= ChunkSpiral.total(job.radius()) && READING.isEmpty() && READY.isEmpty()
                && RECHECK.isEmpty() && ACTIVE.isEmpty()) {
            finish(server, job);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PostProcessJobSavedData job = PostProcessJobSavedData.get(event.getServer());
        if (job.active()) persist(job);
        reset();
    }

    /** Fewer chunks in hand when ticks run long, more when there is room. */
    private static void adapt(MinecraftServer server) {
        double mspt = server.getAverageTickTimeNanos() / 1_000_000.0;
        if (mspt > 40.0) maxActive = Math.max(1, maxActive / 2);
        else if (mspt < 30.0) maxActive = Math.min(MAX_ACTIVE, maxActive + 2);
    }

    /** Checks the next chunks on disk, off the server thread: restored ones first, then the spiral. */
    private static void read(ServerLevel level, PostProcessJobSavedData job) {
        long total = ChunkSpiral.total(job.radius());
        for (int issued = 0; issued < READS_PER_TICK && READING.size() < MAX_READS_IN_FLIGHT
                && READY.size() + ACTIVE.size() < MAX_WAITING; issued++) {
            ChunkPos pos;
            Long restored = RECHECK.poll();
            if (restored != null) {
                pos = new ChunkPos(restored);
            } else if (job.nextIndex() < total) {
                int[] offset = ChunkSpiral.offset(job.nextIndex());
                job.advance(job.nextIndex() + 1);
                pos = new ChunkPos(job.centerX() + offset[0], job.centerZ() + offset[1]);
            } else {
                return;
            }
            long key = pos.toLong();
            if (READING.contains(key)) continue;
            LevelChunk loaded = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (loaded != null) {
                CHECKED.incrementAndGet();
                if (PostProcessCheck.needed(loaded.getPostProcessing())) READY.add(key);
                continue;
            }
            READING.add(key);
            try {
                level.getChunkSource().chunkMap.read(pos).whenComplete((tag, error) -> {
                    CHECKED.incrementAndGet();
                    // Missing or unreadable chunks are skipped: loading them could generate new ground.
                    if (error == null && tag.isPresent() && PostProcessCheck.needed(tag.get())) READY.add(key);
                    READING.remove(key);
                });
            } catch (RuntimeException exception) {
                READING.remove(key);
                CHECKED.incrementAndGet();
                failed++;
            }
        }
    }

    private static void activate(ServerLevel level, long tick) {
        Long key;
        while (ACTIVE.size() < maxActive && (key = READY.poll()) != null) {
            if (ACTIVE.containsKey(key)) continue;
            ChunkPos pos = new ChunkPos(key);
            // Distance 1: the chunk can tick, and its neighbours are loaded for water spreading into them.
            level.getChunkSource().addRegionTicket(TICKET, pos, 1, pos);
            ACTIVE.put(key, new Active(tick));
        }
    }

    private static void work(ServerLevel level, long tick) {
        long deadline = System.nanoTime() + BUDGET_NANOS;
        Iterator<Map.Entry<Long, Active>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Active> entry = iterator.next();
            ChunkPos pos = new ChunkPos(entry.getKey());
            Active active = entry.getValue();
            if (active.processedAt < 0L) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
                if (chunk != null && neighboursLoaded(level, pos)) {
                    if (System.nanoTime() >= deadline) continue;
                    // Every chunk here was found waiting on disk. The chunk system usually runs the work
                    // itself as the ticket makes the chunk tick; whatever it has not reached runs here.
                    if (PostProcessCheck.needed(chunk.getPostProcessing())) {
                        long started = System.nanoTime();
                        chunk.postProcessGeneration();
                        long spent = System.nanoTime() - started;
                        spentNanos += spent;
                        directly++;
                        if (spent > slowestNanos) {
                            slowestNanos = spent;
                            slowestChunk = entry.getKey();
                        }
                    }
                    processed++;
                    active.processedAt = tick;
                } else if (tick - active.added > GIVE_UP_TICKS) {
                    level.getChunkSource().removeRegionTicket(TICKET, pos, 1, pos);
                    iterator.remove();
                    failed++;
                }
            } else if (tick - active.processedAt >= SETTLE_TICKS) {
                level.getChunkSource().removeRegionTicket(TICKET, pos, 1, pos);
                iterator.remove();
            }
        }
    }

    private static boolean neighboursLoaded(ServerLevel level, ChunkPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (level.getChunkSource().getChunkNow(pos.x + dx, pos.z + dz) == null) return false;
            }
        }
        return true;
    }

    /** Drops every ticket; unfinished chunks go back to the queue when {@code requeue}. */
    private static void release(ServerLevel level, boolean requeue) {
        for (Map.Entry<Long, Active> entry : ACTIVE.entrySet()) {
            ChunkPos pos = new ChunkPos(entry.getKey());
            level.getChunkSource().removeRegionTicket(TICKET, pos, 1, pos);
            if (requeue && entry.getValue().processedAt < 0L) READY.add(entry.getKey());
        }
        ACTIVE.clear();
    }

    private static void persist(PostProcessJobSavedData job) {
        job.counted(CHECKED.get(), processed, failed);
        List<Long> pending = new ArrayList<>(READY);
        pending.addAll(READING);
        pending.addAll(RECHECK);
        ACTIVE.forEach((key, active) -> {
            if (active.processedAt < 0L) pending.add(key);
        });
        job.pending(pending);
    }

    private static void finish(MinecraftServer server, PostProcessJobSavedData job) {
        persist(job);
        String summary = "post-processing pass finished: " + job.checked() + " chunks checked, "
                + job.processed() + " processed, " + job.failed() + " skipped";
        Moveearth_addtional.LOGGER.info("[MoveEarth] {}", summary);
        server.getPlayerList().getPlayers().stream().filter(player -> player.hasPermissions(2))
                .forEach(player -> player.sendSystemMessage(Component.literal(
                        "[MoveEarth] 後処理の先行実行が完了しました（確認 " + job.checked() + " / 処理 "
                                + job.processed() + " / スキップ " + job.failed() + "）")));
        job.stop();
        reset();
    }

    private static void reset() {
        READY.clear();
        RECHECK.clear();
        READING.clear();
        ACTIVE.clear();
        CHECKED.set(0L);
        processed = 0L;
        failed = 0L;
        spentNanos = 0L;
        directly = 0L;
        slowestNanos = 0L;
        slowestChunk = 0L;
        maxActive = 8;
        restored = false;
        waiting = false;
    }

    // ---------------------------------------------------------------- commands

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("moveearth")
                .then(Commands.literal("postprocess")
                        .requires(source -> source.hasPermission(3))
                        .then(Commands.literal("start")
                                .executes(context -> start(context.getSource(), -1, false))
                                .then(Commands.literal("now")
                                        .executes(context -> start(context.getSource(), -1, true)))
                                .then(Commands.argument("radiusChunks", IntegerArgumentType.integer(1, MAX_RADIUS))
                                        .executes(context -> start(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "radiusChunks"), false))
                                        .then(Commands.literal("now")
                                                .executes(context -> start(context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "radiusChunks"), true)))))
                        .then(Commands.literal("status").executes(context -> status(context.getSource())))
                        .then(Commands.literal("stop").executes(context -> stop(context.getSource())))));
    }

    private static int start(CommandSourceStack source, int radius, boolean now) {
        MinecraftServer server = source.getServer();
        PostProcessJobSavedData job = PostProcessJobSavedData.get(server);
        if (job.active()) {
            source.sendFailure(Component.literal("[MoveEarth] すでに実行中です。/moveearth postprocess status で確認、stop で中止できます"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        WorldBorder border = level.getWorldBorder();
        int centerX = ((int) Math.floor(border.getCenterX())) >> 4;
        int centerZ = ((int) Math.floor(border.getCenterZ())) >> 4;
        if (radius < 0) {
            double halfBlocks = border.getSize() / 2.0;
            if (halfBlocks / 16.0 > MAX_RADIUS) {
                source.sendFailure(Component.literal("[MoveEarth] ワールドボーダーが広すぎます。半径（チャンク）を指定してください"));
                return 0;
            }
            radius = Math.max(1, (int) Math.floor(halfBlocks / 16.0) - EDGE_MARGIN);
        }
        reset();
        job.start(level.dimension().location(), centerX, centerZ, radius, now);
        int chosen = radius;
        source.sendSuccess(() -> Component.literal("[MoveEarth] 後処理の先行実行を開始しました: "
                + level.dimension().location() + " 中心チャンク(" + centerX + ", " + centerZ + ") 半径 "
                + chosen + " チャンク（" + ChunkSpiral.total(chosen) + " チャンク）"
                + (now ? "。時間を問わず実行します" : "。閉場時間のみ進みます")), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        PostProcessJobSavedData job = PostProcessJobSavedData.get(source.getServer());
        if (!job.active()) {
            source.sendSuccess(() -> Component.literal("[MoveEarth] 後処理の先行実行は動いていません"), false);
            return 0;
        }
        long total = ChunkSpiral.total(job.radius());
        double percent = 100.0 * Math.min(job.nextIndex(), total) / Math.max(1L, total);
        String text = String.format(java.util.Locale.ROOT,
                "[MoveEarth] 後処理の先行実行: %.1f%%（%d / %d）確認 %d / 処理 %d / スキップ %d / 処理中 %d（上限 %d）/ 待機 %d%s",
                percent, Math.min(job.nextIndex(), total), total, CHECKED.get(), processed, failed,
                ACTIVE.size(), maxActive, READY.size() + READING.size() + RECHECK.size(),
                waiting ? " — 開場中のため停止中" : "");
        source.sendSuccess(() -> Component.literal(text), false);
        if (directly > 0) {
            ChunkPos slowest = new ChunkPos(slowestChunk);
            String timing = String.format(java.util.Locale.ROOT,
                    "[MoveEarth] この処理で直接実行した %d チャンク: 平均 %.1f ms / 最大 %.1f ms（チャンク %d, %d、ブロック座標 %d, %d）",
                    directly, spentNanos / 1e6 / directly, slowestNanos / 1e6, slowest.x, slowest.z,
                    slowest.getMiddleBlockX(), slowest.getMiddleBlockZ());
            source.sendSuccess(() -> Component.literal(timing), false);
        }
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        PostProcessJobSavedData job = PostProcessJobSavedData.get(server);
        if (!job.active()) {
            source.sendFailure(Component.literal("[MoveEarth] 後処理の先行実行は動いていません"));
            return 0;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, job.dimension()));
        if (level != null) release(level, false);
        job.stop();
        reset();
        source.sendSuccess(() -> Component.literal("[MoveEarth] 後処理の先行実行を中止しました"), true);
        return 1;
    }
}
