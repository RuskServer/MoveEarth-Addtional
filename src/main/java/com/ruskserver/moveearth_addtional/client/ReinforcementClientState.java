package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_ReinforcementSnapshotPacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_ReinforcementDeltaPacket;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementGreedyMesher;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementMaterial;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client copy of the reinforcement entries the server shows this player, plus per-chunk meshes for
 * the overlay.
 *
 * <p>Snapshots are diffed against the current state: only chunks whose mesh actually changes are
 * queued, and the queue is drained by the renderer under a per-frame time budget
 * ({@link #processDirty}). A chunk waiting in the queue keeps drawing its previous mesh, so a large
 * first snapshot fills in over a few frames instead of stalling one.
 */
public final class ReinforcementClientState {
    private static final int DURABILITY_BANDS = 16;
    /** Ints per merged face in {@link ChunkBucket#faceData()}: x, y, z, sizeX, sizeY, sizeZ, face, style. */
    public static final int FACE_STRIDE = 8;
    private static final Map<BlockPos, Entry> ENTRIES = new HashMap<>();
    /** Packed block position to the mesher's visual state; doubles as the occupancy set. */
    private static final Long2LongOpenHashMap VISUAL_STATES = new Long2LongOpenHashMap();
    private static final Long2ObjectOpenHashMap<LongOpenHashSet> POSITIONS_BY_CHUNK = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectLinkedOpenHashMap<ChunkBucket> CHUNKS = new Long2ObjectLinkedOpenHashMap<>();
    private static final Collection<ChunkBucket> CHUNK_VIEW = Collections.unmodifiableCollection(CHUNKS.values());
    private static final LongLinkedOpenHashSet DIRTY = new LongLinkedOpenHashSet();
    private static final Long2IntOpenHashMap STYLE_IDS = new Long2IntOpenHashMap();
    private static final List<VisualStyle> STYLES = new ArrayList<>();
    private static ResourceLocation dimension;
    private static boolean allowed;
    private static boolean overlayActive;
    /** Bumped whenever an update carried anything new; lets the scan schedule back off when idle. */
    private static long changeCount;

    static {
        STYLE_IDS.defaultReturnValue(-1);
    }

    private ReinforcementClientState() {
    }

    public static void update(S2C_ReinforcementSnapshotPacket packet) {
        long gameTime = clientGameTime();
        boolean sameContext = packet.dimension().equals(dimension) && allowed == packet.allowed();
        Map<BlockPos, Entry> nextEntries = new HashMap<>(Math.max(16, packet.entries().size() * 2));
        Long2LongOpenHashMap nextStates = new Long2LongOpenHashMap(packet.entries().size());
        for (S2C_ReinforcementSnapshotPacket.Entry raw : packet.entries()) {
            Entry entry = new Entry(raw.pos().immutable(), raw.material(), raw.durability(), raw.enabled(),
                    activationEndTick(raw.activationTicksRemaining(), gameTime),
                    raw.constructionInProgress(), raw.siegeDisabled());
            nextEntries.put(entry.pos(), entry);
            nextStates.put(entry.pos().asLong(), visualState(entry));
        }

        boolean changed;
        if (!sameContext) {
            ENTRIES.clear();
            VISUAL_STATES.clear();
            POSITIONS_BY_CHUNK.clear();
            CHUNKS.clear();
            DIRTY.clear();
            for (Long2LongMap.Entry state : nextStates.long2LongEntrySet()) addPosition(state.getLongKey());
            DIRTY.addAll(POSITIONS_BY_CHUNK.keySet());
            changed = true;
        } else {
            LongSet affected = new LongLinkedOpenHashSet();
            ReinforcementSnapshotDiff.changedPositions(view(VISUAL_STATES), view(nextStates),
                    pos -> markChunksTouching(pos, affected));
            changed = !affected.isEmpty() || nextEntries.size() != ENTRIES.size();
            for (Long2LongMap.Entry state : VISUAL_STATES.long2LongEntrySet()) {
                if (!nextStates.containsKey(state.getLongKey())) removePosition(state.getLongKey());
            }
            for (Long2LongMap.Entry state : nextStates.long2LongEntrySet()) {
                if (!VISUAL_STATES.containsKey(state.getLongKey())) addPosition(state.getLongKey());
            }
            if (!changed) {
                for (Entry entry : nextEntries.values()) {
                    Entry previous = ENTRIES.get(entry.pos());
                    if (previous == null || !previous.sameData(entry)) {
                        changed = true;
                        break;
                    }
                }
            }
            DIRTY.addAll(affected);
        }
        dimension = packet.dimension();
        allowed = packet.allowed();
        ENTRIES.clear();
        ENTRIES.putAll(nextEntries);
        VISUAL_STATES.clear();
        VISUAL_STATES.putAll(nextStates);
        if (!allowed) overlayActive = false;
        if (changed) changeCount++;
    }

    public static void update(S2C_ReinforcementDeltaPacket packet) {
        if (!allowed || dimension == null || !dimension.equals(packet.dimension())) return;
        long gameTime = clientGameTime();
        LongSet affected = new LongLinkedOpenHashSet();
        boolean changed = false;
        for (BlockPos pos : packet.removals()) {
            long key = pos.asLong();
            if (!VISUAL_STATES.containsKey(key)) continue;
            markChunksTouching(key, affected);
            VISUAL_STATES.remove(key);
            removePosition(key);
            ENTRIES.remove(pos);
            changed = true;
        }
        for (S2C_ReinforcementDeltaPacket.Entry raw : packet.upserts()) {
            Entry entry = new Entry(raw.pos().immutable(), raw.material(), raw.durability(), raw.enabled(),
                    activationEndTick(raw.activationTicksRemaining(), gameTime),
                    raw.constructionInProgress(), raw.siegeDisabled());
            long key = entry.pos().asLong();
            long state = visualState(entry);
            boolean present = VISUAL_STATES.containsKey(key);
            if (!present || VISUAL_STATES.get(key) != state) {
                markChunksTouching(key, affected);
            }
            if (!present) addPosition(key);
            VISUAL_STATES.put(key, state);
            Entry previous = ENTRIES.put(entry.pos(), entry);
            if (previous == null || !previous.sameData(entry)) changed = true;
        }
        DIRTY.addAll(affected);
        if (changed) changeCount++;
    }

    /**
     * Rebuilds queued chunk meshes until {@code budgetNanos} is spent. At least one chunk is rebuilt
     * per call, so the queue always drains.
     */
    public static void processDirty(long budgetNanos) {
        if (DIRTY.isEmpty()) return;
        long deadline = System.nanoTime() + budgetNanos;
        do {
            rebuildChunk(DIRTY.removeFirstLong());
        } while (!DIRTY.isEmpty() && System.nanoTime() < deadline);
    }

    /** Stable read-only view; avoids copying up to 8192 entries every rendered frame. */
    public static Collection<ChunkBucket> chunks() {
        return CHUNK_VIEW;
    }

    public static int styleCount() {
        return STYLES.size();
    }

    public static VisualStyle style(int id) {
        return STYLES.get(id);
    }

    public static Entry at(BlockPos pos) {
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

    static long changeCount() {
        return changeCount;
    }

    public static void clear() {
        ENTRIES.clear();
        VISUAL_STATES.clear();
        POSITIONS_BY_CHUNK.clear();
        CHUNKS.clear();
        DIRTY.clear();
        STYLE_IDS.clear();
        STYLES.clear();
        dimension = null;
        allowed = false;
        overlayActive = false;
    }

    /** Ticks left until a curing entry activates, counted on the client clock. */
    public static int activationTicksRemaining(Entry entry) {
        return entry.activationTicksRemaining(clientGameTime());
    }

    /**
     * Converts the packet's activation field to an absolute client game tick.
     *
     * <p>The packets currently carry ticks remaining at send time. Storing the end tick lets the HUD
     * count down between scans and keeps the countdown out of the mesh diff. If the packets switch to
     * an absolute end tick, this is the one place to change.
     */
    static long activationEndTick(int ticksRemaining, long clientGameTime) {
        return clientGameTime + Math.max(0, ticksRemaining);
    }

    private static long clientGameTime() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0L : level.getGameTime();
    }

    /** One reinforced block as this client knows it. */
    public record Entry(BlockPos pos, ReinforcementMaterial material, int durability, boolean enabled,
                        long activationEndTick, boolean constructionInProgress, boolean siegeDisabled) {
        public int activationTicksRemaining(long gameTime) {
            return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, activationEndTick - gameTime));
        }

        /** Equal apart from the activation clock, which drifts by a tick or two between sends. */
        boolean sameData(Entry other) {
            return material == other.material && durability == other.durability && enabled == other.enabled
                    && constructionInProgress == other.constructionInProgress
                    && siegeDisabled == other.siegeDisabled
                    && (enabled || Math.abs(activationEndTick - other.activationEndTick) <= 20L);
        }
    }

    /** The banded inputs of {@code ReinforcementVisualStyle}; shared by every face with the same state. */
    public record VisualStyle(ReinforcementMaterial material, int durability, boolean enabled,
                              boolean siegeDisabled) { }

    public static final class ChunkBucket {
        private final int chunkX;
        private final int chunkZ;
        private final int minY;
        private final int maxY;
        private final int[] faceData;
        private final int faceCount;
        private final AABB bounds;

        private ChunkBucket(int chunkX, int chunkZ, int minY, int maxY, int[] faceData, int faceCount) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.minY = minY;
            this.maxY = maxY;
            this.faceData = faceData;
            this.faceCount = faceCount;
            this.bounds = new AABB(chunkX << 4, minY, chunkZ << 4,
                    (chunkX << 4) + 16, maxY + 1, (chunkZ << 4) + 16).inflate(0.03D);
        }

        public int chunkX() { return chunkX; }
        public int chunkZ() { return chunkZ; }
        public int minY() { return minY; }
        public int maxY() { return maxY; }
        /** {@link #FACE_STRIDE} ints per face; read-only. */
        public int[] faceData() { return faceData; }
        public int faceCount() { return faceCount; }
        public AABB bounds() { return bounds; }
    }

    private static long visualState(Entry entry) {
        long state = entry.material().ordinal();
        state |= (long) durabilityBand(entry) << 3;
        if (entry.enabled()) state |= 1L << 16;
        if (entry.constructionInProgress()) state |= 1L << 17;
        if (entry.siegeDisabled()) state |= 1L << 18;
        return state;
    }

    private static VisualStyle styleOf(long state) {
        ReinforcementMaterial[] materials = ReinforcementMaterial.values();
        ReinforcementMaterial material = materials[(int) Math.min(materials.length - 1, state & 7L)];
        int band = (int) ((state >> 3) & 0x1FFFL);
        int durability = Math.round(material.maxDurability() * band / (float) (DURABILITY_BANDS - 1));
        return new VisualStyle(material, durability, (state & (1L << 16)) != 0L, (state & (1L << 18)) != 0L);
    }

    private static int styleId(long state) {
        int id = STYLE_IDS.get(state);
        if (id >= 0) return id;
        id = STYLES.size();
        STYLES.add(styleOf(state));
        STYLE_IDS.put(state, id);
        return id;
    }

    private static int durabilityBand(Entry entry) {
        return Math.max(0, Math.min(DURABILITY_BANDS - 1, Math.round(
                (DURABILITY_BANDS - 1) * entry.durability()
                        / (float) Math.max(1, entry.material().maxDurability()))));
    }

    private static void addPosition(long pos) {
        POSITIONS_BY_CHUNK.computeIfAbsent(chunkKey(pos), ignored -> new LongOpenHashSet()).add(pos);
    }

    private static void removePosition(long pos) {
        long chunk = chunkKey(pos);
        LongOpenHashSet positions = POSITIONS_BY_CHUNK.get(chunk);
        if (positions == null) return;
        positions.remove(pos);
        if (positions.isEmpty()) POSITIONS_BY_CHUNK.remove(chunk);
    }

    private static void rebuildChunk(long chunk) {
        LongOpenHashSet positions = POSITIONS_BY_CHUNK.get(chunk);
        if (positions == null || positions.isEmpty()) {
            CHUNKS.remove(chunk);
            return;
        }
        List<ReinforcementGreedyMesher.Cell> cells = new ArrayList<>(positions.size());
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        LongIterator iterator = positions.iterator();
        while (iterator.hasNext()) {
            long pos = iterator.nextLong();
            if (!VISUAL_STATES.containsKey(pos)) continue;
            int y = BlockPos.getY(pos);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            cells.add(new ReinforcementGreedyMesher.Cell(BlockPos.getX(pos), y, BlockPos.getZ(pos),
                    VISUAL_STATES.get(pos)));
        }
        if (cells.isEmpty()) {
            CHUNKS.remove(chunk);
            return;
        }
        List<ReinforcementGreedyMesher.Quad> quads = ReinforcementGreedyMesher.mesh(cells,
                (x, y, z) -> VISUAL_STATES.containsKey(BlockPos.asLong(x, y, z)));
        int[] data = new int[quads.size() * FACE_STRIDE];
        int offset = 0;
        for (ReinforcementGreedyMesher.Quad quad : quads) {
            data[offset] = quad.x();
            data[offset + 1] = quad.y();
            data[offset + 2] = quad.z();
            data[offset + 3] = quad.sizeX();
            data[offset + 4] = quad.sizeY();
            data[offset + 5] = quad.sizeZ();
            data[offset + 6] = quad.face().ordinal();
            data[offset + 7] = styleId(quad.state());
            offset += FACE_STRIDE;
        }
        CHUNKS.put(chunk, new ChunkBucket(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), minY, maxY,
                data, quads.size()));
    }

    private static void markChunksTouching(long pos, LongSet affected) {
        ReinforcementSnapshotDiff.chunksTouching(BlockPos.getX(pos), BlockPos.getZ(pos),
                (chunkX, chunkZ) -> affected.add(ChunkPos.asLong(chunkX, chunkZ)));
    }

    private static ReinforcementSnapshotDiff.States view(Long2LongOpenHashMap states) {
        return new ReinforcementSnapshotDiff.States() {
            @Override
            public boolean contains(long pos) {
                return states.containsKey(pos);
            }

            @Override
            public long get(long pos) {
                return states.get(pos);
            }

            @Override
            public void forEach(ReinforcementSnapshotDiff.PositionStateConsumer consumer) {
                for (Long2LongMap.Entry entry : states.long2LongEntrySet()) {
                    consumer.accept(entry.getLongKey(), entry.getLongValue());
                }
            }
        };
    }

    private static long chunkKey(long pos) {
        return ChunkPos.asLong(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4);
    }
}
