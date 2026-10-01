package com.ruskserver.moveearth_addtional.s2.siege;

import com.ruskserver.moveearth_addtional.ServerSchedule;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.LocalDate;
import java.util.UUID;

/** Per core, whether offline defense applied when the current open day's fight on it started. */
public final class OfflineDefenseDaySavedData extends SavedData {
    private final OfflineDefenseDays days = new OfflineDefenseDays();

    /** The open day in server time; opening hours never cross midnight. */
    public static long today() {
        return LocalDate.now(ServerSchedule.ZONE).toEpochDay();
    }

    /** Records the answer taken when a fight started on the core; see {@link OfflineDefenseDayPolicy#decides}. */
    public void record(UUID coreId, long day, boolean allowed) {
        if (days.record(coreId, day, allowed)) setDirty();
    }

    /** Today's answer, or null before a fight started on the core today. */
    public Boolean allowedOn(UUID coreId, long day) {
        return days.allowedOn(coreId, day);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag entries = new CompoundTag();
        days.forEach((id, snapshot) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Day", snapshot.day());
            entry.putBoolean("Allowed", snapshot.allowed());
            entries.put(id.toString(), entry);
        });
        tag.put("Cores", entries);
        return tag;
    }

    public static OfflineDefenseDaySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        OfflineDefenseDaySavedData data = new OfflineDefenseDaySavedData();
        CompoundTag entries = tag.getCompound("Cores");
        for (String key : entries.getAllKeys()) {
            try {
                CompoundTag entry = entries.getCompound(key);
                data.days.put(UUID.fromString(key),
                        new OfflineDefenseDays.Snapshot(entry.getLong("Day"), entry.getBoolean("Allowed")));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    public static OfflineDefenseDaySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(OfflineDefenseDaySavedData::new, OfflineDefenseDaySavedData::load, null),
                "moveearth_offline_defense_days");
    }
}
