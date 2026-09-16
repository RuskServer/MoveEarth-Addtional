package com.ruskserver.moveearth_addtional.s2.reinforcement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Comparator;
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
    private final Map<BlockPos, ReinforcementEntry> entries = new HashMap<>();
    private final Map<Long, java.util.Set<BlockPos>> entriesByChunk = new HashMap<>();
    private final java.util.Set<BlockPos> constructionEntries = new HashSet<>();
    private final ArrayDeque<BlockPos> cleanupQueue = new ArrayDeque<>();
    private int cleanupChunkCursor;

    public Optional<ReinforcementEntry> get(BlockPos pos) {
        return Optional.ofNullable(entries.get(pos));
    }

    public void put(BlockPos pos, ReinforcementEntry entry) {
        BlockPos immutable = pos.immutable();
        if (!entries.containsKey(immutable)) index(immutable);
        entries.put(immutable, entry);
        if (entry.activatesAt() > 0L) constructionEntries.add(immutable);
        else constructionEntries.remove(immutable);
        setDirty();
    }

    public void remove(BlockPos pos) {
        if (entries.remove(pos) != null) {
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
            unindex(pos);
            constructionEntries.remove(pos);
            removed++;
        }
        if (removed > 0) setDirty();
        return removed;
    }

    public List<LocatedEntry> around(ServerLevel level, BlockPos center, int radius) {
        long radiusSquared = (long) radius * radius;
        int minChunkX = (center.getX() - radius) >> 4;
        int maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4;
        int maxChunkZ = (center.getZ() + radius) >> 4;
        List<BlockPos> nearby = new ArrayList<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                java.util.Set<BlockPos> indexed = entriesByChunk.get(net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ));
                if (indexed != null) nearby.addAll(indexed);
            }
        }
        return nearby.stream()
                .filter(pos -> pos.distSqr(center) <= radiusSquared)
                .filter(pos -> !level.getBlockState(pos).isAir())
                .sorted(Comparator.comparingDouble(pos -> pos.distSqr(center)))
                .limit(MAX_SYNC_ENTRIES)
                .map(pos -> new LocatedEntry(pos, entries.get(pos)))
                .toList();
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

    public AdvanceResult advance(ServerLevel level, long gameTime) {
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
                iterator.remove();
                unindex(pos);
                removed.add(pos.immutable());
                changed = true;
                continue;
            }
            progressed.add(pos.immutable());
            ReinforcementEntry after = before.advance(gameTime);
            if (after.equals(before)) continue;
            entries.put(pos, after);
            if (after.activatesAt() <= 0L) iterator.remove();
            changed = true;
            if (!before.enabled() && after.enabled() && after.damaged()) {
                activated.add(pos.immutable());
            }
            if (before.durability() < before.maxDurability() && !after.damaged()) {
                completed.add(pos.immutable());
            }
        }
        if (cleanupQueue.isEmpty() && !entriesByChunk.isEmpty()) {
            List<Long> chunks = new ArrayList<>(entriesByChunk.keySet());
            cleanupChunkCursor = Math.floorMod(cleanupChunkCursor, chunks.size());
            java.util.Set<BlockPos> chunkEntries = entriesByChunk.get(chunks.get(cleanupChunkCursor));
            cleanupChunkCursor = (cleanupChunkCursor + 1) % chunks.size();
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

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, ReinforcementEntry> value : entries.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putLong("Pos", value.getKey().asLong());
            entryTag.putString("Material", value.getValue().material().id());
            entryTag.putInt("Durability", value.getValue().durability());
            entryTag.putBoolean("Enabled", value.getValue().enabled());
            entryTag.putLong("ConstructionStartedAt", value.getValue().constructionStartedAt());
            entryTag.putLong("ActivatesAt", value.getValue().activatesAt());
            list.add(entryTag);
        }
        tag.put("Entries", list);
        return tag;
    }

    public static ReinforcementSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ReinforcementSavedData data = new ReinforcementSavedData();
        ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entryTag = list.getCompound(index);
            ReinforcementMaterial material = ReinforcementMaterial.fromId(entryTag.getString("Material"));
            boolean hasConstructionTiming = entryTag.contains("ActivatesAt", Tag.TAG_LONG);
            BlockPos pos = BlockPos.of(entryTag.getLong("Pos"));
            data.entries.put(pos, new ReinforcementEntry(
                    material, entryTag.getInt("Durability"), entryTag.getBoolean("Enabled"),
                    hasConstructionTiming ? entryTag.getLong("ConstructionStartedAt") : 0L,
                    hasConstructionTiming ? entryTag.getLong("ActivatesAt") : 0L));
            data.index(pos);
            if (data.entries.get(pos).activatesAt() > 0L) data.constructionEntries.add(pos);
        }
        return data;
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
