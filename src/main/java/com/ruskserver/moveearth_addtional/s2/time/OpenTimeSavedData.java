package com.ruskserver.moveearth_addtional.s2.time;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Monotonic clock that advances only while the configured server opening window is active. */
public final class OpenTimeSavedData extends SavedData {
    private long openTicks;

    public long openTicks() { return openTicks; }

    public void advance(long elapsedTicks) {
        long advanced = OpenTimePolicy.advance(openTicks, elapsedTicks);
        if (advanced == openTicks) return;
        openTicks = advanced;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("SchemaVersion", 1);
        tag.putLong("OpenTicks", openTicks);
        return tag;
    }

    public static OpenTimeSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        OpenTimeSavedData data = new OpenTimeSavedData();
        data.openTicks = Math.max(0L, tag.getLong("OpenTicks"));
        return data;
    }

    public static OpenTimeSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(OpenTimeSavedData::new, OpenTimeSavedData::load, null),
                "moveearth_open_time");
    }
}
