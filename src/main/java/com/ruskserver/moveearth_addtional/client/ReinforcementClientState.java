package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.network.S2C_ReinforcementSnapshotPacket;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementGreedyMesher;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public final class ReinforcementClientState {
    private static final int DURABILITY_BANDS = 16;
    private static final Map<BlockPos, S2C_ReinforcementSnapshotPacket.Entry> ENTRIES = new LinkedHashMap<>();
    private static final Map<Long, ChunkBucket> CHUNKS = new LinkedHashMap<>();
    private static final Collection<ChunkBucket> CHUNK_VIEW = Collections.unmodifiableCollection(CHUNKS.values());
    private static ResourceLocation dimension;
    private static boolean allowed;
    private static boolean overlayActive;

    private ReinforcementClientState() {
    }

    public static void update(S2C_ReinforcementSnapshotPacket packet) {
        dimension = packet.dimension();
        allowed = packet.allowed();
        ENTRIES.clear();
        CHUNKS.clear();
        LongOpenHashSet occupied = new LongOpenHashSet(packet.entries().size());
        Map<Long, List<ReinforcementGreedyMesher.Cell>> cellsByChunk = new LinkedHashMap<>();
        Map<Long, S2C_ReinforcementSnapshotPacket.Entry> styles = new HashMap<>();
        for (var entry : packet.entries()) {
            BlockPos pos = entry.pos().immutable();
            ENTRIES.put(pos, entry);
            occupied.add(pos.asLong());
        }
        for (var entry : packet.entries()) {
            BlockPos pos = entry.pos();
            long chunk = net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
            long state = visualState(entry);
            styles.computeIfAbsent(state, ignored -> visualStyle(entry));
            cellsByChunk.computeIfAbsent(chunk, ignored -> new ArrayList<>())
                    .add(new ReinforcementGreedyMesher.Cell(pos.getX(), pos.getY(), pos.getZ(), state));
        }
        for (Map.Entry<Long, List<ReinforcementGreedyMesher.Cell>> chunk : cellsByChunk.entrySet()) {
            List<ReinforcementGreedyMesher.Cell> cells = chunk.getValue();
            ReinforcementGreedyMesher.Cell first = cells.getFirst();
            ChunkBucket bucket = new ChunkBucket(first.x() >> 4, first.z() >> 4);
            cells.forEach(bucket::include);
            ReinforcementGreedyMesher.mesh(cells,
                            (x, y, z) -> occupied.contains(BlockPos.asLong(x, y, z)))
                    .forEach(quad -> bucket.add(new MergedFace(quad, styles.get(quad.state()))));
            CHUNKS.put(chunk.getKey(), bucket);
        }
        if (!allowed) overlayActive = false;
    }

    /** Stable read-only view; avoids copying up to 8192 entries every rendered frame. */
    public static Collection<ChunkBucket> chunks() {
        return CHUNK_VIEW;
    }

    public static S2C_ReinforcementSnapshotPacket.Entry at(BlockPos pos) {
        return ENTRIES.get(pos);
    }

    public static ResourceLocation dimension() {
        return dimension;
    }

    public static boolean allowed() {
        return allowed;
    }

    public static boolean overlayActive() {
        return overlayActive;
    }

    public static void toggleOverlay() {
        overlayActive = !overlayActive;
    }

    public static void clear() {
        ENTRIES.clear();
        CHUNKS.clear();
        dimension = null;
        allowed = false;
        overlayActive = false;
    }

    public static final class ChunkBucket {
        private final int chunkX;
        private final int chunkZ;
        private final List<MergedFace> faces = new ArrayList<>();
        private int minY = Integer.MAX_VALUE;
        private int maxY = Integer.MIN_VALUE;

        private ChunkBucket(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        private void include(ReinforcementGreedyMesher.Cell cell) {
            minY = Math.min(minY, cell.y());
            maxY = Math.max(maxY, cell.y());
        }

        private void add(MergedFace face) { faces.add(face); }

        public int chunkX() { return chunkX; }
        public int chunkZ() { return chunkZ; }
        public int minY() { return minY; }
        public int maxY() { return maxY; }
        public List<MergedFace> faces() { return faces; }
    }

    public record MergedFace(ReinforcementGreedyMesher.Quad quad,
                             S2C_ReinforcementSnapshotPacket.Entry style) { }

    private static long visualState(S2C_ReinforcementSnapshotPacket.Entry entry) {
        long state = entry.material().ordinal();
        state |= (long) durabilityBand(entry) << 3;
        if (entry.enabled()) state |= 1L << 16;
        if (entry.constructionInProgress()) state |= 1L << 17;
        if (entry.siegeDisabled()) state |= 1L << 18;
        return state;
    }

    private static S2C_ReinforcementSnapshotPacket.Entry visualStyle(
            S2C_ReinforcementSnapshotPacket.Entry entry) {
        int durability = Math.round(entry.material().maxDurability()
                * durabilityBand(entry) / (float) (DURABILITY_BANDS - 1));
        return new S2C_ReinforcementSnapshotPacket.Entry(entry.pos(), entry.material(), durability,
                entry.enabled(), entry.activationTicksRemaining(),
                entry.constructionInProgress(), entry.siegeDisabled());
    }

    private static int durabilityBand(S2C_ReinforcementSnapshotPacket.Entry entry) {
        return Math.max(0, Math.min(DURABILITY_BANDS - 1, Math.round(
                (DURABILITY_BANDS - 1) * entry.durability()
                        / (float) Math.max(1, entry.material().maxDurability()))));
    }
}
