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
    /** Nations owning storage inside a box of one dimension, with how many pieces each owns there. */
    public Map<UUID, Integer> ownersInside(ResourceLocation dimension, int minX, int minY, int minZ,
                                           int maxX, int maxY, int maxZ) {
        Map<UUID, Integer> result = new LinkedHashMap<>();
        owners.forEach((key, owner) -> {
            if (!key.dimension().equals(dimension)) return;
            BlockPos pos = BlockPos.of(key.pos());
            if (pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ) result.merge(owner, 1, Integer::sum);
        });
        return result;
    }

    /** Every record, for the abandoned-storage sweep. */
    public void forEach(TriConsumer consumer) {
        owners.forEach((key, owner) -> consumer.accept(key.dimension(), BlockPos.of(key.pos()), owner));
    }

    @FunctionalInterface
    public interface TriConsumer {
        void accept(ResourceLocation dimension, BlockPos pos, UUID owner);
    }

    /** Drops every record of a disbanded nation so its former members are not locked out. */
    public void removeNation(UUID nationId) {
        if (nationId != null && owners.values().removeIf(nationId::equals)) setDirty();
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
