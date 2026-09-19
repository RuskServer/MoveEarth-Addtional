package com.ruskserver.moveearth_addtional.s2.nation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Explicit ownership for storage placed after the loot system was introduced. */
public final class NationStorageOwnershipSavedData extends SavedData {
    private final Map<Key, UUID> owners = new LinkedHashMap<>();

    public UUID owner(ResourceLocation dimension, BlockPos pos) { return owners.get(new Key(dimension, pos.asLong())); }
    public void put(ResourceLocation dimension, BlockPos pos, UUID owner) {
        if (owner != null) { owners.put(new Key(dimension, pos.asLong()), owner); setDirty(); }
    }
    public void remove(ResourceLocation dimension, BlockPos pos) {
        if (owners.remove(new Key(dimension, pos.asLong())) != null) setDirty();
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        owners.forEach((key, owner) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Dimension", key.dimension().toString());
            entry.putLong("Pos", key.pos());
            entry.putUUID("Owner", owner);
            list.add(entry);
        });
        tag.put("Owners", list);
        return tag;
    }

    public static NationStorageOwnershipSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        NationStorageOwnershipSavedData data = new NationStorageOwnershipSavedData();
        ListTag list = tag.getList("Owners", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            if (dimension != null && entry.hasUUID("Owner"))
                data.owners.put(new Key(dimension, entry.getLong("Pos")), entry.getUUID("Owner"));
        }
        return data;
    }

    public static NationStorageOwnershipSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(NationStorageOwnershipSavedData::new,
                        NationStorageOwnershipSavedData::load, null), "moveearth_storage_owners");
    }
    private record Key(ResourceLocation dimension, long pos) { }
}
