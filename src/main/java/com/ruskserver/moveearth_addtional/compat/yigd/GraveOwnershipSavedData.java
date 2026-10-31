package com.ruskserver.moveearth_addtional.compat.yigd;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Keeps ownership independent of later membership changes and moved grave blocks. */
public final class GraveOwnershipSavedData extends SavedData {
    private final Map<UUID, GraveAccessPolicy.Ownership> graves = new HashMap<>();

    private final Map<UUID, Placement> placements = new HashMap<>();

    public GraveAccessPolicy.Ownership find(UUID id) { return graves.get(id); }

    public GraveAccessPolicy.Ownership remember(UUID id, GraveAccessPolicy.Ownership owner) {
        var previous = graves.putIfAbsent(id, owner);
        if (previous == null) setDirty();
        return previous == null ? owner : previous;
    }

    public void placed(UUID id, ResourceLocation dimension, BlockPos pos, boolean insideHomeTerritory) {
        var previous = graves.get(id);
        if (previous == null) return;
        var placement = new Placement(dimension, pos.immutable());
        if (placement.equals(placements.put(id, placement))) return;
        setDirty();
        var next = previous.placed(insideHomeTerritory);
        if (!next.equals(previous)) { graves.put(id, next); setDirty(); }
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        graves.forEach((id, owner) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Grave", id);
            entry.putUUID("Player", owner.player());
            if (owner.nation() != null) entry.putUUID("Nation", owner.nation());
            entry.putBoolean("Unlocked", owner.unlocked());
            var placement = placements.get(id);
            if (placement != null) {
                entry.putString("Dimension", placement.dimension().toString());
                entry.putLong("Position", placement.pos().asLong());
            }
            list.add(entry);
        });
        tag.put("Graves", list);
        return tag;
    }

    public static GraveOwnershipSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new GraveOwnershipSavedData();
        ListTag list = tag.getList("Graves", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Grave") || !entry.hasUUID("Player")) continue;
            data.graves.put(entry.getUUID("Grave"), new GraveAccessPolicy.Ownership(
                    entry.getUUID("Player"), entry.hasUUID("Nation") ? entry.getUUID("Nation") : null,
                    entry.getBoolean("Unlocked")));
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            if (dimension != null) data.placements.put(entry.getUUID("Grave"),
                    new Placement(dimension, BlockPos.of(entry.getLong("Position"))));
        }
        return data;
    }

    private record Placement(ResourceLocation dimension, BlockPos pos) { }

    public static GraveOwnershipSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(
                GraveOwnershipSavedData::new, GraveOwnershipSavedData::load, null), "moveearth_grave_ownership");
    }
}
