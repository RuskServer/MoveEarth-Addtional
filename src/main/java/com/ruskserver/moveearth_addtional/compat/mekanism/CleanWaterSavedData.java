package com.ruskserver.moveearth_addtional.compat.mekanism;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Water sources a bucket placed in one level ({@link CleanWaterRule}). Bounded:
 * past the cap the oldest are forgotten. A source that is gone is dropped the
 * next time it is looked at.
 */
public final class CleanWaterSavedData extends SavedData {
    static final int MAX_SOURCES = 32_768;

    private final Set<Long> sources = new LinkedHashSet<>();

    public boolean contains(BlockPos pos) {
        return sources.contains(pos.asLong());
    }

    public void mark(BlockPos pos) {
        long key = pos.asLong();
        if (sources.contains(key)) return;
        if (sources.size() >= MAX_SOURCES) {
            Iterator<Long> oldest = sources.iterator();
            oldest.next();
            oldest.remove();
        }
        sources.add(key);
        setDirty();
    }

    public void forget(BlockPos pos) {
        if (sources.remove(pos.asLong())) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray("Sources", sources.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    public static CleanWaterSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        CleanWaterSavedData data = new CleanWaterSavedData();
        for (long key : tag.getLongArray("Sources")) data.sources.add(key);
        return data;
    }

    public static CleanWaterSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CleanWaterSavedData::new, CleanWaterSavedData::load, null),
                "moveearth_clean_water");
    }
}
