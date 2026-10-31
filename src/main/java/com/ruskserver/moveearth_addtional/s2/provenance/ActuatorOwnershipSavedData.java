package com.ruskserver.moveearth_addtional.s2.provenance;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;

/** Pistons have no block entity in which to keep their placer's identity. */
public final class ActuatorOwnershipSavedData extends SavedData {
    private record Key(ResourceLocation dimension, long pos) { }
    private final Map<Key, ActionSource> owners = new HashMap<>();
    public ActionSource find(ResourceLocation dimension, BlockPos pos) { return owners.get(new Key(dimension, pos.asLong())); }
    public void forget(ResourceLocation dimension, BlockPos pos) {
        if (owners.remove(new Key(dimension, pos.asLong())) != null) setDirty();
    }
    public void put(ResourceLocation dimension, BlockPos pos, ActionSource source) {
        Key key = new Key(dimension, pos.asLong());
        if (source == null || !source.attributable()) owners.remove(key); else owners.put(key, source);
        setDirty();
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        owners.forEach((key, source) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Dimension", key.dimension().toString());
            entry.putLong("Pos", key.pos());
            if (source.actor() != null) entry.putUUID("Actor", source.actor());
            if (source.nation() != null) entry.putUUID("Nation", source.nation());
            entries.add(entry);
        });
        tag.put("Owners", entries);
        return tag;
    }
    public static ActuatorOwnershipSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new ActuatorOwnershipSavedData();
        var entries = tag.getList("Owners", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompound(i);
            var dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            var source = new ActionSource(entry.hasUUID("Actor") ? entry.getUUID("Actor") : null,
                    entry.hasUUID("Nation") ? entry.getUUID("Nation") : null, ActionSource.Kind.ATTRIBUTED);
            if (dimension != null && source.attributable()) data.owners.put(new Key(dimension, entry.getLong("Pos")), source);
        }
        return data;
    }
    public static ActuatorOwnershipSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(
                ActuatorOwnershipSavedData::new, ActuatorOwnershipSavedData::load, null), "moveearth_actuator_ownership");
    }
}
