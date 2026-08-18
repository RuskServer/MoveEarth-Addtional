package com.ruskserver.moveearth_addtional.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DetectorBlockPositionSavedData extends SavedData {

    private final Set<BlockPos> positions = new HashSet<>();

    public DetectorBlockPositionSavedData() {
    }

    public void addPosition(BlockPos pos) {
        if (positions.add(pos)) {
            this.setDirty();
        }
    }

    public void removePosition(BlockPos pos) {
        if (positions.remove(pos)) {
            this.setDirty();
        }
    }

    public BlockPos getOverlapPosition(BlockPos newPos) {
        ChunkPos newChunk = new ChunkPos(newPos);
        for (BlockPos pos : positions) {
            if (pos.equals(newPos)) {
                continue; // 自分自身と同じ座標は重複判定から除外
            }
            ChunkPos posChunk = new ChunkPos(pos);
            if (Math.abs(newChunk.x - posChunk.x) <= 3 && Math.abs(newChunk.z - posChunk.z) <= 3) {
                return pos; // 重複している既存ブロックの座標を返す
            }
        }
        return null;
    }

    public boolean isTooClose(BlockPos newPos) {
        return getOverlapPosition(newPos) != null;
    }

    public Set<BlockPos> getPositions() {
        return Set.copyOf(this.positions);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        List<Long> longList = new ArrayList<>();
        for (BlockPos pos : positions) {
            longList.add(pos.asLong());
        }
        long[] array = new long[longList.size()];
        for (int i = 0; i < longList.size(); i++) {
            array[i] = longList.get(i);
        }
        tag.putLongArray("DetectorPositions", array);
        return tag;
    }

    public static DetectorBlockPositionSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        DetectorBlockPositionSavedData data = new DetectorBlockPositionSavedData();
        if (tag.contains("DetectorPositions")) {
            long[] array = tag.getLongArray("DetectorPositions");
            for (long val : array) {
                data.positions.add(BlockPos.of(val));
            }
        }
        return data;
    }

    public static DetectorBlockPositionSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        DetectorBlockPositionSavedData::new,
                        DetectorBlockPositionSavedData::load,
                        null
                ),
                "detector_block_positions"
        );
    }
}
