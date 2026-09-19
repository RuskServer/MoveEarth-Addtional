package com.ruskserver.moveearth_addtional.s2.siege;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Inventory payloads are stored independently from chunks, making wreckage restart-safe. */
public final class StorageWreckageSavedData extends SavedData {
    private final Map<Key, Wreckage> wreckage = new LinkedHashMap<>();

    public Wreckage get(ResourceLocation dimension, BlockPos pos) {
        return wreckage.get(new Key(dimension, pos.asLong()));
    }

    public void put(Wreckage value) {
        wreckage.put(new Key(value.dimension(), value.pos().asLong()), value);
        setDirty();
    }

    public void updateItems(ResourceLocation dimension, BlockPos pos, List<ItemStack> items) {
        Key key = new Key(dimension, pos.asLong());
        Wreckage before = wreckage.get(key);
        if (before == null) return;
        wreckage.put(key, before.withItems(items));
        setDirty();
    }

    public Wreckage remove(ResourceLocation dimension, BlockPos pos) {
        Wreckage removed = wreckage.remove(new Key(dimension, pos.asLong()));
        if (removed != null) setDirty();
        return removed;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Wreckage value : wreckage.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Dimension", value.dimension().toString());
            entry.putLong("Pos", value.pos().asLong());
            if (value.ownerNation() != null) entry.putUUID("Owner", value.ownerNation());
            if (value.siegeId() != null) entry.putUUID("Siege", value.siegeId());
            if (value.vehicleId() != null) entry.putUUID("Vehicle", value.vehicleId());
            if (value.destroyerId() != null) entry.putUUID("Destroyer", value.destroyerId());
            entry.putString("Source", value.source());
            entry.putLong("CreatedOpenTick", value.createdOpenTick());
            ListTag items = new ListTag();
            for (ItemStack stack : value.items()) if (!stack.isEmpty()) items.add(stack.save(registries));
            entry.put("Items", items);
            list.add(entry);
        }
        tag.put("Wreckage", list);
        return tag;
    }

    public static StorageWreckageSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        StorageWreckageSavedData data = new StorageWreckageSavedData();
        ListTag list = tag.getList("Wreckage", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            if (dimension == null) continue;
            List<ItemStack> items = new ArrayList<>();
            ListTag itemList = entry.getList("Items", Tag.TAG_COMPOUND);
            for (int slot = 0; slot < itemList.size(); slot++)
                ItemStack.parse(registries, itemList.getCompound(slot)).ifPresent(items::add);
            BlockPos pos = BlockPos.of(entry.getLong("Pos"));
            Wreckage value = new Wreckage(dimension, pos,
                    entry.hasUUID("Owner") ? entry.getUUID("Owner") : null,
                    entry.hasUUID("Siege") ? entry.getUUID("Siege") : null,
                    entry.hasUUID("Vehicle") ? entry.getUUID("Vehicle") : null,
                    entry.hasUUID("Destroyer") ? entry.getUUID("Destroyer") : null,
                    entry.getString("Source"), Math.max(0L, entry.getLong("CreatedOpenTick")), items);
            data.wreckage.put(new Key(dimension, pos.asLong()), value);
        }
        return data;
    }

    public static StorageWreckageSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(StorageWreckageSavedData::new, StorageWreckageSavedData::load, null),
                "moveearth_storage_wreckage");
    }

    private record Key(ResourceLocation dimension, long pos) { }
    public record Wreckage(ResourceLocation dimension, BlockPos pos, UUID ownerNation, UUID siegeId,
                           UUID vehicleId, UUID destroyerId, String source, long createdOpenTick,
                           List<ItemStack> items) {
        public Wreckage {
            pos = pos.immutable();
            source = source == null ? "unknown" : source;
            items = items == null ? List.of() : items.stream().map(ItemStack::copy).toList();
        }
        public Wreckage withItems(List<ItemStack> next) {
            return new Wreckage(dimension, pos, ownerNation, siegeId, vehicleId, destroyerId,
                    source, createdOpenTick, next);
        }
    }
}
