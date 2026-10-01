package com.ruskserver.moveearth_addtional.s2.time;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Legacy gamerule restoration data. New freezes are runtime-only and never alter gamerules.
 */
public final class ClosedHoursFreezeSavedData extends SavedData {
    private boolean frozen;
    private boolean savedDaylightCycle = true;
    private boolean savedWeatherCycle = true;

    public boolean frozen() { return frozen; }
    public boolean savedDaylightCycle() { return savedDaylightCycle; }
    public boolean savedWeatherCycle() { return savedWeatherCycle; }

    public boolean matchesRestoredRules(CompoundTag rules) {
        return ClosedHoursFreeze.restorationSaved(savedDaylightCycle, savedWeatherCycle,
                rules.getString("doDaylightCycle"), rules.getString("doWeatherCycle"));
    }

    public void release() {
        frozen = false;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("SchemaVersion", 1);
        tag.putBoolean("Frozen", frozen);
        tag.putBoolean("SavedDaylightCycle", savedDaylightCycle);
        tag.putBoolean("SavedWeatherCycle", savedWeatherCycle);
        return tag;
    }

    public static ClosedHoursFreezeSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ClosedHoursFreezeSavedData data = new ClosedHoursFreezeSavedData();
        data.frozen = tag.getBoolean("Frozen");
        data.savedDaylightCycle = !tag.contains("SavedDaylightCycle") || tag.getBoolean("SavedDaylightCycle");
        data.savedWeatherCycle = !tag.contains("SavedWeatherCycle") || tag.getBoolean("SavedWeatherCycle");
        return data;
    }

    public static ClosedHoursFreezeSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ClosedHoursFreezeSavedData::new, ClosedHoursFreezeSavedData::load, null),
                "moveearth_closed_hours_freeze");
    }
}
