package com.ruskserver.moveearth_addtional.economy;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Active play time per player on this world, in ticks: time online and not idle by the earning rule
 * ({@link EarningPolicy#activeTicksAfter}). Decides when an account stops being new. Kept apart from
 * vanilla's PLAY_TIME statistic, which counts idling and starts at zero for everyone on a new world.
 */
public final class ActivePlayTimeSavedData extends SavedData {
    private final Map<UUID, Long> active = new HashMap<>();

    public long ticks(UUID playerId) {
        Long value = playerId == null ? null : active.get(playerId);
        return value == null ? 0L : value;
    }

    /** Credits one more second online; called once a second per player. */
    public void tick(UUID playerId, boolean idle) {
        if (playerId == null || idle) return;
        active.put(playerId, EarningPolicy.activeTicksAfter(ticks(playerId), false));
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        active.forEach((playerId, ticks) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", playerId);
            entry.putLong("Ticks", ticks);
            list.add(entry);
        });
        tag.put("Active", list);
        return tag;
    }

    public static ActivePlayTimeSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ActivePlayTimeSavedData data = new ActivePlayTimeSavedData();
        ListTag list = tag.getList("Active", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            if (entry.hasUUID("Player")) data.active.put(entry.getUUID("Player"), Math.max(0L, entry.getLong("Ticks")));
        }
        return data;
    }

    public static ActivePlayTimeSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ActivePlayTimeSavedData::new, ActivePlayTimeSavedData::load, null),
                "moveearth_active_play_time");
    }
}
