package com.ruskserver.moveearth_addtional.s2.reinforcement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;

public final class ReinforcementSavedData extends SavedData {
    private static final int MAX_SYNC_ENTRIES = 8192;
    private static final int CLEANUP_BUDGET_PER_SECOND = 512;
    private static final String TAG_VERSION = "Version";
    private static final String TAG_PALETTE = "Palette";
    private static final String TAG_CHUNKS = "Chunks";
    private static final String TAG_POSITIONS = "Pos";
    private static final String TAG_MATERIALS = "Mat";
    private static final String TAG_DURABILITY = "Hp";
    private static final String TAG_FLAGS = "Flags";
    private static final String TAG_TIMER_INDICES = "TimerIdx";
    private static final String TAG_STARTED_AT = "Started";
    private static final String TAG_ACTIVATES_AT = "Activates";
    private static final String TAG_DELAY_POSITIONS = "RepairDelayPos";
    private static final String TAG_DELAY_DEADLINES = "RepairDelayUntil";
    private final Map<BlockPos, ReinforcementEntry> entries = new HashMap<>();
    private final Map<Long, java.util.Set<BlockPos>> entriesByChunk = new HashMap<>();
    private final java.util.Set<BlockPos> constructionEntries = new HashSet<>();
    private final ArrayDeque<BlockPos> cleanupQueue = new ArrayDeque<>();
    /** Chunk order of the current cleanup sweep; refreshed once per full sweep instead of every second. */
    private long[] cleanupChunkOrder = new long[0];
    private int cleanupChunkCursor;
    // Transient invalidation only. HP/activation changes do not change the installed armor's mass.
    private final ReinforcementMassRevisions massRevisions = new ReinforcementMassRevisions();

    public long massRevision() { return massRevisions.revision(); }

    public long massRevisionInside(int minX, int minZ, int maxX, int maxZ) {
        return massRevisions.inside(minX, minZ, maxX, maxZ);
    }

    private void massChanged(BlockPos pos) {
        massRevisions.changed(pos.getX(), pos.getZ());
    }

    /** Physics/accounting scan: unlike the client overlay it must never truncate entries. */
    public void forEachInside(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                             java.util.function.BiConsumer<BlockPos, ReinforcementEntry> consumer) {
        for (int x = minX >> 4; x <= maxX >> 4; x++) {
            for (int z = minZ >> 4; z <= maxZ >> 4; z++) {
                Set<BlockPos> positions = entriesByChunk.get(net.minecraft.world.level.ChunkPos.asLong(x, z));
                if (positions == null) continue;
                for (BlockPos pos : positions) {
                    if (pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY
                            && pos.getY() <= maxY && pos.getZ() >= minZ && pos.getZ() <= maxZ) {
                        consumer.accept(pos, entries.get(pos));
                    }
                }
            }
        }
    }
    // Independent of the block entry: breaking/replacing a block must not erase battle damage history.
    private final RepairCooldowns repairBlockedUntil = new RepairCooldowns();

    public long repairBlockedUntil(BlockPos pos, long now) {
        return repairBlockedUntil.until(pos.asLong(), now);
    }

    public void recordDamage(BlockPos pos, long now, long delay) {
        if (delay <= 0L) return;
        repairBlockedUntil.hit(pos.asLong(), now, delay);
        setDirty();
    }

    /** Preserve battle damage when an assembly relocates a block into a Sable plot. */
    public void copyRepairDelay(BlockPos source, BlockPos destination, long now) {
        long until = repairBlockedUntil(source, now);
        if (until > now) recordDamage(destination, now, until - now);
    }

    /**
     * Moves reinforcement with blocks Sable relocated (assembly, disassembly, merge). {@code moves} maps each
     * moved source block to its destination; the entries are written to {@code target} (the destination
     * level's data, usually this one). All sources are read and cleared before any destination is written,
     * so overlapping source and destination sets never lose an entry. Battle damage history is copied for
     * every moved block, reinforced or not. Returns the changed positions (sources and destinations).
     */
    public List<BlockPos> transfer(Map<BlockPos, BlockPos> moves, ReinforcementSavedData target, long now) {
        if (moves.isEmpty()) return List.of();
        var delays = com.ruskserver.moveearth_addtional.compat.vehicle.BlockRelocationPlan.<BlockPos, Long>of(
                moves.keySet(), moves::get, source -> {
                    long until = repairBlockedUntil(source, now);
                    return until > now ? until : null;
                });
        delays.apply(source -> { }, (destination, until) -> target.recordDamage(destination, now, until - now));
        var plan = com.ruskserver.moveearth_addtional.compat.vehicle.BlockRelocationPlan.<BlockPos,
                ReinforcementEntry>of(moves.keySet(), moves::get, entries::get);
        if (plan.isEmpty()) return List.of();
        List<BlockPos> changed = new ArrayList<>(plan.moves().size() * 2);
        plan.apply(source -> {
            remove(source);
            changed.add(source);
        }, (destination, entry) -> {
            target.put(destination, entry);
            changed.add(destination.immutable());
        });
        return List.copyOf(changed);
    }

    public Optional<ReinforcementEntry> get(BlockPos pos) {
        return Optional.ofNullable(entries.get(pos));
    }

    public void put(BlockPos pos, ReinforcementEntry entry) {
        BlockPos immutable = pos.immutable();
        if (!entries.containsKey(immutable)) index(immutable);
        ReinforcementEntry previous = entries.put(immutable, entry);
        if (previous == null || previous.material() != entry.material()) massChanged(immutable);
        if (entry.activatesAt() > 0L) constructionEntries.add(immutable);
        else constructionEntries.remove(immutable);
        setDirty();
    }

    public void remove(BlockPos pos) {
        if (entries.remove(pos) != null) {
            massChanged(pos);
            unindex(pos);
            constructionEntries.remove(pos);
            setDirty();
        }
    }

    public int removeWhere(Predicate<BlockPos> predicate) {
        int removed = 0;
        var iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next().getKey();
            if (!predicate.test(pos)) continue;
            iterator.remove();
            massChanged(pos);
            unindex(pos);
            constructionEntries.remove(pos);
            removed++;
        }
        if (removed > 0) setDirty();
        return removed;
    }

    /**
     * Reinforced, non-air blocks within {@code radius} (Euclidean, block corners), nearest first and capped at
     * {@value #MAX_SYNC_ENTRIES}. Use {@link #aroundUnordered} when order and the cap are irrelevant.
     */
    public List<LocatedEntry> around(ServerLevel level, BlockPos center, int radius) {
        return located(ReinforcementAroundQuery.select(spatialSource(), center.getX(), center.getY(),
                center.getZ(), radius, pos -> !level.getBlockState(pos).isAir(), true, MAX_SYNC_ENTRIES));
    }

    /**
     * Same selection as {@link #around} without sorting and without the client-sync cap: for damage, blast
     * snapshots and other callers that visit every hit. The result is a detached list, so callers may modify
     * reinforcements while iterating it.
     */
    public List<LocatedEntry> aroundUnordered(ServerLevel level, BlockPos center, int radius) {
        return located(ReinforcementAroundQuery.select(spatialSource(), center.getX(), center.getY(),
                center.getZ(), radius, pos -> !level.getBlockState(pos).isAir(), false, Integer.MAX_VALUE));
    }

    private List<LocatedEntry> located(List<BlockPos> positions) {
        if (positions.isEmpty()) return List.of();
        List<LocatedEntry> result = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) result.add(new LocatedEntry(pos, entries.get(pos)));
        return result;
    }

    private ReinforcementAroundQuery.Source<BlockPos> spatialSource() {
        return new ReinforcementAroundQuery.Source<>() {
            private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

            @Override
            public long countInChunks(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ, long cap) {
                long count = 0L;
                for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                    for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                        Set<BlockPos> indexed = entriesByChunk.get(
                                net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ));
                        if (indexed != null) count += indexed.size();
                        if (count > cap) return count;
                    }
                }
                return count;
            }

            @Override
            public void forEachInChunks(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ,
                                        java.util.function.Consumer<BlockPos> consumer) {
                for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                    for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                        Set<BlockPos> indexed = entriesByChunk.get(
                                net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ));
                        if (indexed != null) indexed.forEach(consumer);
                    }
                }
            }

            @Override
            public BlockPos at(int x, int y, int z) {
                return entries.containsKey(cursor.set(x, y, z)) ? new BlockPos(x, y, z) : null;
            }

            @Override public int x(BlockPos value) { return value.getX(); }
            @Override public int y(BlockPos value) { return value.getY(); }
            @Override public int z(BlockPos value) { return value.getZ(); }
        };
    }

    public List<LocatedEntry> inside(ServerLevel level, int minX, int minY, int minZ,
                                     int maxX, int maxY, int maxZ) {
        List<LocatedEntry> result = new ArrayList<>();
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                Set<BlockPos> indexed = entriesByChunk.get(net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ));
                if (indexed == null) continue;
                for (BlockPos pos : indexed) {
                    if (pos.getX() < minX || pos.getX() > maxX || pos.getY() < minY || pos.getY() > maxY
                            || pos.getZ() < minZ || pos.getZ() > maxZ || !level.hasChunkAt(pos)
                            || level.getBlockState(pos).isAir()) continue;
                    result.add(new LocatedEntry(pos, entries.get(pos)));
                    if (result.size() >= MAX_SYNC_ENTRIES) return List.copyOf(result);
                }
            }
        }
        return List.copyOf(result);
    }

    /** Recovery accounting is not a client scan: never truncate it at MAX_SYNC_ENTRIES or load chunks. */
    public RecoveryWalls recoveryHealth(ServerLevel level, BlockPos center, int radius, int cap,
                              boolean maximum, java.util.function.Predicate<BlockPos> owned) {
        int cx = center.getX() >> 4;
        int cz = center.getZ() >> 4;
        var values = java.util.stream.IntStream.builder();
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                var positions = entriesByChunk.get(net.minecraft.world.level.ChunkPos.asLong(x, z));
                if (positions == null) continue;
                for (BlockPos pos : positions) {
                    ReinforcementEntry entry = entries.get(pos);
                    if (entry == null || !entry.enabled() || entry.durability() <= 0 || !owned.test(pos)) continue;
                    if (level.hasChunkAt(pos) ? level.getBlockState(pos).isAir() : !maximum) continue;
                    values.add(maximum ? entry.maxDurability() : entry.durability());
                }
            }
        }
        int[] health = values.build().toArray();
        return new RecoveryWalls(com.ruskserver.moveearth_addtional.s2.recovery.RecoveryObjectivePolicy
                .wallHealth(java.util.Arrays.stream(health), cap), Math.min(Math.max(0, cap), health.length));
    }

    public record RecoveryWalls(int health, int blocks) { }

    /**
     * One construction/cleanup pass. {@link AdvanceResult#progressed()} lists only entries whose stored state
     * changed; a pending entry whose activation countdown merely ticked is neither re-synced nor marked dirty
     * (clients derive the countdown from the absolute activation tick).
     */
    public AdvanceResult advance(ServerLevel level, long gameTime) {
        // Expired deadlines are inert (until() clamps to now), so dropping them is not worth an autosave.
        repairBlockedUntil.expire(gameTime);
        List<BlockPos> activated = new ArrayList<>();
        List<BlockPos> completed = new ArrayList<>();
        List<BlockPos> progressed = new ArrayList<>();
        Set<BlockPos> removed = new LinkedHashSet<>();
        boolean changed = false;
        Iterator<BlockPos> iterator = constructionEntries.iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            ReinforcementEntry before = entries.get(pos);
            if (before == null) {
                iterator.remove();
                continue;
            }
            if (!level.hasChunkAt(pos)) continue;
            if (level.getBlockState(pos).isAir()) {
                entries.remove(pos);
                massChanged(pos);
                iterator.remove();
                unindex(pos);
                removed.add(pos.immutable());
                changed = true;
                continue;
            }
            ReinforcementEntry.Step step = before.step(gameTime);
            if (!step.changed()) continue;
            ReinforcementEntry after = step.after();
            entries.put(pos, after);
            if (!after.constructing()) iterator.remove();
            changed = true;
            progressed.add(pos.immutable());
            if (step.activated()) activated.add(pos.immutable());
            if (step.completed()) completed.add(pos.immutable());
        }
        if (cleanupQueue.isEmpty() && !entriesByChunk.isEmpty()) {
            if (cleanupChunkCursor >= cleanupChunkOrder.length) {
                cleanupChunkOrder = entriesByChunk.keySet().stream().mapToLong(Long::longValue).toArray();
                cleanupChunkCursor = 0;
            }
            java.util.Set<BlockPos> chunkEntries = entriesByChunk.get(cleanupChunkOrder[cleanupChunkCursor++]);
            if (chunkEntries != null) cleanupQueue.addAll(chunkEntries);
        }
        for (int checked = 0; checked < CLEANUP_BUDGET_PER_SECOND && !cleanupQueue.isEmpty(); checked++) {
            BlockPos pos = cleanupQueue.removeFirst();
            if (!entries.containsKey(pos) || !level.hasChunkAt(pos)) continue;
            if (!level.getBlockState(pos).isAir()) continue;
            removeInternal(pos);
            removed.add(pos.immutable());
            changed = true;
        }
        if (changed) setDirty();
        return new AdvanceResult(List.copyOf(activated), List.copyOf(completed),
                List.copyOf(progressed), List.copyOf(removed));
    }

    /**
     * Format version 2: one compound per chunk column holding primitive arrays (see
     * {@link ReinforcementPackedChunk}) plus one material palette and two repair-delay arrays, instead of a
     * compound per block. Only this format is written; {@link #load} still reads version 1.
     */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(TAG_VERSION, ReinforcementPackedChunk.FORMAT_VERSION);
        ReinforcementPackedChunk.Palette palette = ReinforcementPackedChunk.Palette.current();
        ListTag paletteTag = new ListTag();
        for (String id : palette.ids()) paletteTag.add(StringTag.valueOf(id));
        tag.put(TAG_PALETTE, paletteTag);
        ListTag chunks = new ListTag();
        for (Set<BlockPos> positions : entriesByChunk.values()) {
            ReinforcementPackedChunk.Writer writer = new ReinforcementPackedChunk.Writer(palette, positions.size());
            for (BlockPos pos : positions) {
                ReinforcementEntry entry = entries.get(pos);
                if (entry != null) writer.add(pos.asLong(), entry);
            }
            if (writer.size() == 0) continue;
            ReinforcementPackedChunk.Columns columns = writer.finish();
            CompoundTag chunk = new CompoundTag();
            chunk.putLongArray(TAG_POSITIONS, columns.positions());
            chunk.putByteArray(TAG_MATERIALS, columns.materials());
            chunk.putIntArray(TAG_DURABILITY, columns.durability());
            chunk.putByteArray(TAG_FLAGS, columns.flags());
            if (columns.timerIndices().length > 0) {
                chunk.putIntArray(TAG_TIMER_INDICES, columns.timerIndices());
                chunk.putLongArray(TAG_STARTED_AT, columns.startedAt());
                chunk.putLongArray(TAG_ACTIVATES_AT, columns.activatesAt());
            }
            chunks.add(chunk);
        }
        tag.put(TAG_CHUNKS, chunks);
        Map<Long, Long> delays = repairBlockedUntil.snapshot();
        long[] delayPositions = new long[delays.size()];
        long[] delayDeadlines = new long[delays.size()];
        int index = 0;
        for (Map.Entry<Long, Long> delay : delays.entrySet()) {
            delayPositions[index] = delay.getKey();
            delayDeadlines[index++] = delay.getValue();
        }
        tag.putLongArray(TAG_DELAY_POSITIONS, delayPositions);
        tag.putLongArray(TAG_DELAY_DEADLINES, delayDeadlines);
        return tag;
    }

    public static ReinforcementSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ReinforcementSavedData data = new ReinforcementSavedData();
        // Version 1 repair delays: one compound per position.
        ListTag delays = tag.getList("RepairDelays", Tag.TAG_COMPOUND);
        for (int i = 0; i < delays.size(); i++) {
            CompoundTag delay = delays.getCompound(i);
            data.repairBlockedUntil.restore(delay.getLong("Pos"), delay.getLong("Until"));
        }
        long[] delayPositions = tag.getLongArray(TAG_DELAY_POSITIONS);
        long[] delayDeadlines = tag.getLongArray(TAG_DELAY_DEADLINES);
        for (int i = 0; i < Math.min(delayPositions.length, delayDeadlines.length); i++) {
            data.repairBlockedUntil.restore(delayPositions[i], delayDeadlines[i]);
        }
        // Version 1 entries: one compound per reinforced block.
        ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entryTag = list.getCompound(index);
            data.restore(BlockPos.of(entryTag.getLong("Pos")), ReinforcementPackedChunk.legacyEntry(
                    entryTag.getString("Material"), entryTag.getInt("Durability"), entryTag.getBoolean("Enabled"),
                    entryTag.contains("ActivatesAt", Tag.TAG_LONG),
                    entryTag.getLong("ConstructionStartedAt"), entryTag.getLong("ActivatesAt")));
        }
        // Version 2 entries: packed per chunk column.
        if (tag.contains(TAG_CHUNKS, Tag.TAG_LIST)) {
            ListTag paletteTag = tag.getList(TAG_PALETTE, Tag.TAG_STRING);
            List<String> ids = new ArrayList<>(paletteTag.size());
            for (int i = 0; i < paletteTag.size(); i++) ids.add(paletteTag.getString(i));
            ReinforcementPackedChunk.Palette palette = ReinforcementPackedChunk.Palette.read(ids);
            ListTag chunks = tag.getList(TAG_CHUNKS, Tag.TAG_COMPOUND);
            for (int i = 0; i < chunks.size(); i++) {
                CompoundTag chunk = chunks.getCompound(i);
                ReinforcementPackedChunk.decode(new ReinforcementPackedChunk.Columns(
                        chunk.getLongArray(TAG_POSITIONS), chunk.getByteArray(TAG_MATERIALS),
                        chunk.getIntArray(TAG_DURABILITY), chunk.getByteArray(TAG_FLAGS),
                        chunk.getIntArray(TAG_TIMER_INDICES), chunk.getLongArray(TAG_STARTED_AT),
                        chunk.getLongArray(TAG_ACTIVATES_AT)),
                        palette, (pos, entry) -> data.restore(BlockPos.of(pos), entry));
            }
        }
        return data;
    }

    private void restore(BlockPos pos, ReinforcementEntry entry) {
        if (entries.put(pos, entry) == null) index(pos);
        if (entry.constructing()) constructionEntries.add(pos);
        else constructionEntries.remove(pos);
    }

    public static ReinforcementSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ReinforcementSavedData::new, ReinforcementSavedData::load, null),
                "moveearth_reinforcements");
    }

    private void index(BlockPos pos) {
        entriesByChunk.computeIfAbsent(net.minecraft.world.level.ChunkPos.asLong(
                pos.getX() >> 4, pos.getZ() >> 4), ignored -> new HashSet<>()).add(pos);
    }

    private void unindex(BlockPos pos) {
        long chunk = net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        java.util.Set<BlockPos> indexed = entriesByChunk.get(chunk);
        if (indexed == null) return;
        indexed.remove(pos);
        if (indexed.isEmpty()) entriesByChunk.remove(chunk);
    }

    private void removeInternal(BlockPos pos) {
        entries.remove(pos);
        massChanged(pos);
        constructionEntries.remove(pos);
        unindex(pos);
    }

    public record LocatedEntry(BlockPos pos, ReinforcementEntry entry) {
    }

    public record AdvanceResult(List<BlockPos> activated, List<BlockPos> completed,
                                List<BlockPos> progressed,
                                List<BlockPos> removed) {
    }
}
