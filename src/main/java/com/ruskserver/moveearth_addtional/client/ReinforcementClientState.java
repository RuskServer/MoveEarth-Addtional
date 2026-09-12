package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.network.S2C_ReinforcementSnapshotPacket;
import com.ruskserver.moveearth_addtional.network.S2C_ReinforcementDeltaPacket;
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
import java.util.HashSet;
import java.util.Set;

public final class ReinforcementClientState {
    private static final int DURABILITY_BANDS = 16;
    private static final Map<BlockPos, S2C_ReinforcementSnapshotPacket.Entry> ENTRIES = new LinkedHashMap<>();
    private static final Map<Long, Set<BlockPos>> POSITIONS_BY_CHUNK = new HashMap<>();
    private static final LongOpenHashSet OCCUPIED = new LongOpenHashSet();
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
        POSITIONS_BY_CHUNK.clear();
        OCCUPIED.clear();
        CHUNKS.clear();
        for (var entry : packet.entries()) {
            putEntry(entry.pos(), entry);
        }
        for (long chunk : POSITIONS_BY_CHUNK.keySet()) rebuildChunk(chunk);
        if (!allowed) overlayActive = false;
    }

    public static void update(S2C_ReinforcementDeltaPacket packet) {
        if (!allowed || dimension == null || !dimension.equals(packet.dimension())) return;
        Set<Long> affectedChunks = new HashSet<>();
        for (BlockPos pos : packet.removals()) {
            markAffectedChunks(pos, affectedChunks);
            removeEntry(pos);
        }
        for (S2C_ReinforcementDeltaPacket.Entry changed : packet.upserts()) {
            BlockPos pos = changed.pos();
            markAffectedChunks(pos, affectedChunks);
            putEntry(pos, new S2C_ReinforcementSnapshotPacket.Entry(pos, changed.material(),
                    changed.durability(), changed.enabled(), changed.activationTicksRemaining(),
                    changed.constructionInProgress(), changed.siegeDisabled()));
        }
        for (long chunk : affectedChunks) rebuildChunk(chunk);
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
        POSITIONS_BY_CHUNK.clear();
        OCCUPIED.clear();
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

    private static void putEntry(BlockPos rawPos, S2C_ReinforcementSnapshotPacket.Entry entry) {
        BlockPos pos = rawPos.immutable();
        ENTRIES.put(pos, entry);
        OCCUPIED.add(pos.asLong());
        POSITIONS_BY_CHUNK.computeIfAbsent(chunkKey(pos), ignored -> new HashSet<>()).add(pos);
    }

    private static void removeEntry(BlockPos pos) {
        BlockPos removed = ENTRIES.remove(pos) == null ? null : pos;
        if (removed == null) return;
        OCCUPIED.remove(pos.asLong());
        long chunk = chunkKey(pos);
        Set<BlockPos> positions = POSITIONS_BY_CHUNK.get(chunk);
        if (positions == null) return;
        positions.remove(pos);
        if (positions.isEmpty()) POSITIONS_BY_CHUNK.remove(chunk);
    }

    private static void rebuildChunk(long chunk) {
        Set<BlockPos> positions = POSITIONS_BY_CHUNK.get(chunk);
        if (positions == null || positions.isEmpty()) {
            CHUNKS.remove(chunk);
            return;
        }
        List<ReinforcementGreedyMesher.Cell> cells = new ArrayList<>(positions.size());
        Map<Long, S2C_ReinforcementSnapshotPacket.Entry> styles = new HashMap<>();
        for (BlockPos pos : positions) {
            S2C_ReinforcementSnapshotPacket.Entry entry = ENTRIES.get(pos);
            if (entry == null) continue;
            long state = visualState(entry);
            styles.computeIfAbsent(state, ignored -> visualStyle(entry));
            cells.add(new ReinforcementGreedyMesher.Cell(pos.getX(), pos.getY(), pos.getZ(), state));
        }
        if (cells.isEmpty()) {
            CHUNKS.remove(chunk);
            return;
        }
        ReinforcementGreedyMesher.Cell first = cells.getFirst();
        ChunkBucket bucket = new ChunkBucket(first.x() >> 4, first.z() >> 4);
        cells.forEach(bucket::include);
        ReinforcementGreedyMesher.mesh(cells,
                        (x, y, z) -> OCCUPIED.contains(BlockPos.asLong(x, y, z)))
                .forEach(quad -> bucket.add(new MergedFace(quad, styles.get(quad.state()))));
        CHUNKS.put(chunk, bucket);
    }

    private static void markAffectedChunks(BlockPos pos, Set<Long> chunks) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        chunks.add(net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ));
        int localX = pos.getX() & 15;
        int localZ = pos.getZ() & 15;
        if (localX == 0) chunks.add(net.minecraft.world.level.ChunkPos.asLong(chunkX - 1, chunkZ));
        if (localX == 15) chunks.add(net.minecraft.world.level.ChunkPos.asLong(chunkX + 1, chunkZ));
        if (localZ == 0) chunks.add(net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ - 1));
        if (localZ == 15) chunks.add(net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ + 1));
    }

    private static long chunkKey(BlockPos pos) {
        return net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
