package com.ruskserver.moveearth_addtional.s2.reinforcement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;

public final class ReinforcementSavedData extends SavedData {
    private static final int MAX_SYNC_ENTRIES = 8192;
    private final Map<BlockPos, ReinforcementEntry> entries = new HashMap<>();

    public Optional<ReinforcementEntry> get(BlockPos pos) {
        return Optional.ofNullable(entries.get(pos));
    }

    public void put(BlockPos pos, ReinforcementEntry entry) {
        entries.put(pos.immutable(), entry);
        setDirty();
    }

    public void remove(BlockPos pos) {
        if (entries.remove(pos) != null) setDirty();
    }

    public List<LocatedEntry> around(ServerLevel level, BlockPos center, int radius) {
        long radiusSquared = (long) radius * radius;
        List<LocatedEntry> result = entries.entrySet().stream()
                .filter(value -> value.getKey().distSqr(center) <= radiusSquared)
                .filter(value -> !level.getBlockState(value.getKey()).isAir())
                .sorted(Comparator.comparingDouble(value -> value.getKey().distSqr(center)))
                .limit(MAX_SYNC_ENTRIES)
                .map(value -> new LocatedEntry(value.getKey(), value.getValue()))
                .toList();
        return result;
    }

    public AdvanceResult advance(ServerLevel level, long gameTime) {
        List<BlockPos> activated = new ArrayList<>();
        List<BlockPos> completed = new ArrayList<>();
        List<BlockPos> removed = new ArrayList<>();
        boolean changed = false;
        var iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, ReinforcementEntry> value = iterator.next();
            if (!level.hasChunkAt(value.getKey())) continue;
            if (level.getBlockState(value.getKey()).isAir()) {
                removed.add(value.getKey().immutable());
                iterator.remove();
                changed = true;
                continue;
            }
            ReinforcementEntry before = value.getValue();
            ReinforcementEntry after = before.advance(gameTime);
            if (after.equals(before)) continue;
            value.setValue(after);
            changed = true;
            if (!before.enabled() && after.enabled() && after.damaged()) {
                activated.add(value.getKey().immutable());
            }
            if (before.durability() < before.maxDurability() && !after.damaged()) {
                completed.add(value.getKey().immutable());
            }
        }
        if (changed) setDirty();
        return new AdvanceResult(List.copyOf(activated), List.copyOf(completed), List.copyOf(removed));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, ReinforcementEntry> value : entries.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putLong("Pos", value.getKey().asLong());
            entryTag.putString("Material", value.getValue().material().id());
            entryTag.putInt("Durability", value.getValue().durability());
            entryTag.putBoolean("Enabled", value.getValue().enabled());
            entryTag.putLong("ConstructionStartedAt", value.getValue().constructionStartedAt());
            entryTag.putLong("ActivatesAt", value.getValue().activatesAt());
            list.add(entryTag);
        }
        tag.put("Entries", list);
        return tag;
    }

    public static ReinforcementSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ReinforcementSavedData data = new ReinforcementSavedData();
        ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entryTag = list.getCompound(index);
            ReinforcementMaterial material = ReinforcementMaterial.fromId(entryTag.getString("Material"));
            boolean hasConstructionTiming = entryTag.contains("ActivatesAt", Tag.TAG_LONG);
            data.entries.put(BlockPos.of(entryTag.getLong("Pos")), new ReinforcementEntry(
                    material, entryTag.getInt("Durability"), entryTag.getBoolean("Enabled"),
                    hasConstructionTiming ? entryTag.getLong("ConstructionStartedAt") : 0L,
                    hasConstructionTiming ? entryTag.getLong("ActivatesAt") : 0L));
        }
        return data;
    }

    public static ReinforcementSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ReinforcementSavedData::new, ReinforcementSavedData::load, null),
                "moveearth_reinforcements");
    }

    public record LocatedEntry(BlockPos pos, ReinforcementEntry entry) {
    }

    public record AdvanceResult(List<BlockPos> activated, List<BlockPos> completed,
                                List<BlockPos> removed) {
    }
}
