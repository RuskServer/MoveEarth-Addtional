package com.ruskserver.moveearth_addtional.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bounded pool of safe positions observed while overworld chunks are loaded normally.
 * Random spawning may load these known-generated chunks, but never probes an unknown
 * chunk and therefore cannot start expensive terrain generation during login.
 */
public final class RandomSpawnSavedData extends SavedData {
    static final int MAX_CANDIDATES = 8_192;

    private final List<Long> positions = new ArrayList<>();
    private final Set<Long> positionSet = new HashSet<>();
    private int replacementCursor;

    public List<BlockPos> positions() {
        return positions.stream().map(BlockPos::of).toList();
    }

    public void remember(BlockPos position) {
        long packed = position.immutable().asLong();
        if (!positionSet.add(packed)) return;
        if (positions.size() < MAX_CANDIDATES) {
            positions.add(packed);
        } else {
            int index = Math.floorMod(replacementCursor++, positions.size());
            positionSet.remove(positions.set(index, packed));
        }
        setDirty();
    }

    public void forget(BlockPos position) {
        long packed = position.asLong();
        if (!positionSet.remove(packed)) return;
        positions.remove(packed);
        if (positions.isEmpty()) replacementCursor = 0;
        else replacementCursor = Math.floorMod(replacementCursor, positions.size());
        setDirty();
    }

    public void forgetColumn(int x, int z) {
        boolean changed = positions.removeIf(packed -> {
            BlockPos position = BlockPos.of(packed);
            if (position.getX() != x || position.getZ() != z) return false;
            positionSet.remove(packed);
            return true;
        });
        if (!changed) return;
        if (positions.isEmpty()) replacementCursor = 0;
        else replacementCursor = Math.floorMod(replacementCursor, positions.size());
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("SchemaVersion", 1);
        tag.putLongArray("Positions", positions);
        tag.putInt("ReplacementCursor", replacementCursor);
        return tag;
    }

    public static RandomSpawnSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RandomSpawnSavedData data = new RandomSpawnSavedData();
        for (long packed : tag.getLongArray("Positions")) {
            if (data.positions.size() >= MAX_CANDIDATES) break;
            if (data.positionSet.add(packed)) data.positions.add(packed);
        }
        if (!data.positions.isEmpty()) {
            data.replacementCursor = Math.floorMod(tag.getInt("ReplacementCursor"), data.positions.size());
        }
        return data;
    }

    public static RandomSpawnSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RandomSpawnSavedData::new, RandomSpawnSavedData::load, null),
                "moveearth_random_spawn_pool");
    }
}
