package com.ruskserver.moveearth_addtional.pvp.deploy;

import com.ruskserver.moveearth_addtional.pvp.PvpMatchManager;
import com.ruskserver.moveearth_addtional.pvp.map.PvpMapDefinition;
import com.ruskserver.moveearth_addtional.pvp.map.PvpMapGeometry;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Scans a PvP map's battle area into a {@link PvpTacticalMap}, a few thousand columns a tick so a large area
 * does not stall the server. Each column is read from the top of the area down to the first block with a map
 * colour, so a roof above the area is left out.
 */
public final class PvpTacticalScanner {
    public static final PvpTacticalScanner INSTANCE = new PvpTacticalScanner();
    private static final int CELLS_PER_TICK = 2048;

    private final Deque<Job> queue = new ArrayDeque<>();
    private BiConsumer<MinecraftServer, PvpTacticalMap> listener = (server, map) -> { };

    private PvpTacticalScanner() {
    }

    /** Called with every finished scan, such as to send it to players deploying on that map. */
    public void onFinished(BiConsumer<MinecraftServer, PvpTacticalMap> listener) {
        this.listener = listener;
    }

    public boolean scanning(String mapId) {
        return queue.stream().anyMatch(job -> job.map.id().equals(mapId));
    }

    /** Queues a scan of {@code map}, replacing one already queued; {@code requester} is told when it is done. */
    public void request(PvpMapDefinition map, UUID requester) {
        queue.removeIf(job -> job.map.id().equals(map.id()));
        PvpMapGeometry.Box box = map.bounds();
        if (box.tooLarge()) return;
        queue.add(new Job(map, box, requester));
    }

    public void tick(MinecraftServer server) {
        Job job = queue.peek();
        if (job == null) return;
        ServerLevel arena = server.getLevel(PvpMatchManager.ARENA);
        if (arena == null) {
            queue.poll();
            return;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int done = 0; done < CELLS_PER_TICK && job.next < job.colors.length; done++, job.next++) {
            int cellX = job.next % job.width;
            int cellZ = job.next / job.width;
            int x = job.box.minX() + cellX * job.cellSize;
            int z = job.box.minZ() + cellZ * job.cellSize;
            int top = Math.min(job.box.maxY(), arena.getChunk(x >> 4, z >> 4)
                    .getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15));
            int found = job.box.minY();
            int color = 0;
            for (int y = top; y >= job.box.minY(); y--) {
                BlockState state = arena.getBlockState(cursor.set(x, y, z));
                MapColor mapColor = state.getMapColor(arena, cursor);
                if (mapColor != MapColor.NONE) {
                    found = y;
                    color = mapColor.id;
                    break;
                }
            }
            job.colors[job.next] = (byte) color;
            job.heights[job.next] = (byte) Math.min(255, Math.max(0, found - job.box.minY()));
        }
        if (job.next < job.colors.length) return;
        queue.poll();
        PvpTacticalMap result = new PvpTacticalMap(job.map.id(), System.currentTimeMillis(), job.box.minX(),
                job.box.minZ(), job.cellSize, job.width, job.depth, job.box.minY(), job.colors, job.heights);
        PvpTacticalSavedData.get(server).put(result);
        Moveearth_addtional.LOGGER.info("Scanned PvP tactical map {} ({}x{} cells of {} blocks)",
                result.mapId(), result.width(), result.depth(), result.cellSize());
        ServerPlayer requester = job.requester == null ? null : server.getPlayerList().getPlayer(job.requester);
        if (requester != null) {
            requester.sendSystemMessage(MoveEarthMessage.success("マップ [" + job.map.displayName()
                    + "] の戦術マップを作成しました（" + result.width() + "×" + result.depth() + "マス）。"));
        }
        listener.accept(server, result);
    }

    public void clear() {
        queue.clear();
    }

    private static final class Job {
        final PvpMapDefinition map;
        final PvpMapGeometry.Box box;
        final UUID requester;
        final int cellSize;
        final int width;
        final int depth;
        final byte[] colors;
        final byte[] heights;
        int next;

        Job(PvpMapDefinition map, PvpMapGeometry.Box box, UUID requester) {
            this.map = map;
            this.box = box;
            this.requester = requester;
            this.cellSize = PvpMapGeometry.cellSize(box, PvpTacticalMap.MAX_CELLS);
            this.width = PvpMapGeometry.cells(box.width(), cellSize);
            this.depth = PvpMapGeometry.cells(box.depth(), cellSize);
            this.colors = new byte[width * depth];
            this.heights = new byte[width * depth];
        }
    }
}
