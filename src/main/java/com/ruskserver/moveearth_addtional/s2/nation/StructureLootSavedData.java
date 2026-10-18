package com.ruskserver.moveearth_addtional.s2.nation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Structure loot containers that have been opened ({@link StructureLootPolicy}). Rolling the loot
 * clears the loot table, so without this record a chest would lock again after its first visitor.
 * Bounded: past the cap the oldest are forgotten.
 */
public final class StructureLootSavedData extends SavedData {
    static final int MAX_BLOCKS = 200_000;
    static final int MAX_ENTITIES = 20_000;

    private final Map<ResourceLocation, Set<Long>> blocks = new HashMap<>();
    private final Set<UUID> entities = new LinkedHashSet<>();

    public boolean remembers(ResourceLocation dimension, BlockPos pos) {
        Set<Long> set = blocks.get(dimension);
        return set != null && set.contains(pos.asLong());
    }

    public void remember(ResourceLocation dimension, BlockPos pos) {
        Set<Long> set = blocks.computeIfAbsent(dimension, ignored -> new LinkedHashSet<>());
        if (!set.add(pos.asLong())) return;
        if (blockCount() > MAX_BLOCKS) {
            Iterator<Long> oldest = set.iterator();
            oldest.next();
            oldest.remove();
        }
        setDirty();
    }

    public void forget(ResourceLocation dimension, BlockPos pos) {
        Set<Long> set = blocks.get(dimension);
        if (set != null && set.remove(pos.asLong())) setDirty();
    }

    public boolean remembers(UUID entity) {
        return entities.contains(entity);
    }

    public void remember(UUID entity) {
        if (!entities.add(entity)) return;
        if (entities.size() > MAX_ENTITIES) {
            Iterator<UUID> oldest = entities.iterator();
            oldest.next();
            oldest.remove();
        }
        setDirty();
    }

    private int blockCount() {
        int count = 0;
        for (Set<Long> set : blocks.values()) count += set.size();
        return count;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag byDimension = new CompoundTag();
        blocks.forEach((dimension, set) -> byDimension.put(dimension.toString(),
                new LongArrayTag(set.stream().mapToLong(Long::longValue).toArray())));
        tag.put("Blocks", byDimension);
        ListTag list = new ListTag();
        for (UUID entity : entities) list.add(NbtUtils.createUUID(entity));
        tag.put("Entities", list);
        return tag;
    }

    public static StructureLootSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        StructureLootSavedData data = new StructureLootSavedData();
        CompoundTag byDimension = tag.getCompound("Blocks");
        for (String key : byDimension.getAllKeys()) {
            ResourceLocation dimension = ResourceLocation.tryParse(key);
            if (dimension == null) continue;
            Set<Long> set = data.blocks.computeIfAbsent(dimension, ignored -> new LinkedHashSet<>());
            for (long pos : byDimension.getLongArray(key)) set.add(pos);
        }
        ListTag list = tag.getList("Entities", Tag.TAG_INT_ARRAY);
        for (Tag entry : list) data.entities.add(NbtUtils.loadUUID(entry));
        return data;
    }

    public static StructureLootSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(StructureLootSavedData::new, StructureLootSavedData::load, null),
                "moveearth_structure_loot");
    }
}
