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
import java.util.Set;

/** When each owned chest was first seen outside every nation's land, in open-time ticks ({@link AbandonedStoragePolicy}). */
public final class AbandonedStorageSavedData extends SavedData {
    public record Key(ResourceLocation dimension, long pos) {
        public static Key of(ResourceLocation dimension, BlockPos pos) {
            return new Key(dimension, pos.asLong());
        }
    }

    private final Map<Key, Long> lostAt = new LinkedHashMap<>();

    public long lostAt(ResourceLocation dimension, BlockPos pos) {
        return lostAt.getOrDefault(Key.of(dimension, pos), 0L);
    }

    /** @return whether this is the first time it was seen abandoned */
    public boolean mark(Key key, long now) {
        if (lostAt.containsKey(key)) return false;
        lostAt.put(key, Math.max(1L, now));
        setDirty();
        return true;
    }

    /** Back inside a nation's land: the clock starts over if it is ever lost again. */
    public void clear(Key key) {
        if (lostAt.remove(key) != null) setDirty();
    }

    /** Drops marks of chests whose ownership record is gone (broken, or moved away). */
    public void retainOnly(Set<Key> owned) {
        if (lostAt.keySet().retainAll(owned)) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        lostAt.forEach((key, at) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Dimension", key.dimension().toString());
            entry.putLong("Pos", key.pos());
            entry.putLong("LostAt", at);
            list.add(entry);
        });
        tag.put("Abandoned", list);
        return tag;
    }

    public static AbandonedStorageSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        AbandonedStorageSavedData data = new AbandonedStorageSavedData();
        ListTag list = tag.getList("Abandoned", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            if (dimension == null) continue;
            data.lostAt.put(new Key(dimension, entry.getLong("Pos")), Math.max(1L, entry.getLong("LostAt")));
        }
        return data;
    }

    public static AbandonedStorageSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(AbandonedStorageSavedData::new, AbandonedStorageSavedData::load, null),
                "moveearth_abandoned_storage");
    }
}
