package com.ruskserver.moveearth_addtional.compat.paintings;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** How many paintings each player uploaded on the open day it last uploaded ({@link PaintingUploadPolicy}). */
public final class PaintingUploadSavedData extends SavedData {
    private record Count(String day, int count) {
    }

    private final Map<UUID, Count> counts = new HashMap<>();

    public int usedToday(UUID player, String today) {
        Count count = counts.get(player);
        return count == null ? 0 : PaintingUploadPolicy.usedToday(count.day(), count.count(), today);
    }

    public void record(UUID player, String today) {
        counts.put(player, new Count(today, usedToday(player, today) + 1));
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        counts.forEach((player, count) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", player);
            entry.putString("Day", count.day());
            entry.putInt("Count", count.count());
            list.add(entry);
        });
        tag.put("Uploads", list);
        return tag;
    }

    public static PaintingUploadSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PaintingUploadSavedData data = new PaintingUploadSavedData();
        ListTag list = tag.getList("Uploads", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Player")) continue;
            data.counts.put(entry.getUUID("Player"), new Count(entry.getString("Day"), entry.getInt("Count")));
        }
        return data;
    }

    public static PaintingUploadSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PaintingUploadSavedData::new, PaintingUploadSavedData::load, null),
                "moveearth_painting_uploads");
    }
}
