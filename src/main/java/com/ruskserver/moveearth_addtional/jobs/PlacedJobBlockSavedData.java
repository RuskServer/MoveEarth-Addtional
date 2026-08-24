package com.ruskserver.moveearth_addtional.jobs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Remembers player-placed reward blocks so they cannot be placed and mined repeatedly. */
public final class PlacedJobBlockSavedData extends SavedData {
    private final Set<Long> positions = new HashSet<>();

    public void mark(BlockPos position) {
        if (positions.add(position.asLong())) {
            setDirty();
        }
    }

    public boolean consume(BlockPos position) {
        boolean removed = positions.remove(position.asLong());
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public void moveAll(List<BlockPos> moved, List<BlockPos> destroyed, Direction direction) {
        Set<Long> markedMoved = new HashSet<>();
        for (BlockPos position : moved) {
            long packed = position.asLong();
            if (positions.contains(packed)) {
                markedMoved.add(packed);
            }
        }

        boolean changed = positions.removeAll(markedMoved);
        for (long packed : markedMoved) {
            changed |= positions.add(BlockPos.of(packed).relative(direction).asLong());
        }
        for (BlockPos position : destroyed) {
            changed |= positions.remove(position.asLong());
        }
        if (changed) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] values = new long[positions.size()];
        int index = 0;
        for (long position : positions) {
            values[index++] = position;
        }
        tag.putLongArray("Positions", values);
        return tag;
    }

    public static PlacedJobBlockSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PlacedJobBlockSavedData data = new PlacedJobBlockSavedData();
        for (long position : tag.getLongArray("Positions")) {
            data.positions.add(position);
        }
        return data;
    }

    public static PlacedJobBlockSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PlacedJobBlockSavedData::new, PlacedJobBlockSavedData::load, null),
                "moveearth_jobs_placed_blocks");
    }
}
