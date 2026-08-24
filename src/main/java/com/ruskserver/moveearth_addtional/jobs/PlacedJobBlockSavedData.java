package com.ruskserver.moveearth_addtional.jobs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Remembers player-placed reward blocks so they cannot be placed and mined repeatedly. */
public final class PlacedJobBlockSavedData extends SavedData {
    private final Map<Long, BitSet> sections = new HashMap<>();

    public void mark(BlockPos position) {
        if (markInternal(position)) {
            setDirty();
        }
    }

    public boolean consume(BlockPos position) {
        boolean removed = consumeInternal(position);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public void moveAll(List<BlockPos> moved, List<BlockPos> destroyed, Direction direction) {
        List<BlockPos> markedMoved = new ArrayList<>();
        for (BlockPos position : moved) {
            if (contains(position)) {
                markedMoved.add(position.immutable());
            }
        }

        boolean changed = false;
        for (BlockPos position : markedMoved) {
            changed |= consumeInternal(position);
        }
        for (BlockPos position : markedMoved) {
            changed |= markInternal(position.relative(direction));
        }
        for (BlockPos position : destroyed) {
            changed |= consumeInternal(position);
        }
        if (changed) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag sectionTags = new ListTag();
        sections.forEach((section, bits) -> {
            if (!bits.isEmpty()) {
                CompoundTag sectionTag = new CompoundTag();
                sectionTag.putLong("Section", section);
                sectionTag.putLongArray("Bits", bits.toLongArray());
                sectionTags.add(sectionTag);
            }
        });
        tag.put("Sections", sectionTags);
        return tag;
    }

    public static PlacedJobBlockSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PlacedJobBlockSavedData data = new PlacedJobBlockSavedData();
        ListTag sectionTags = tag.getList("Sections", Tag.TAG_COMPOUND);
        for (int i = 0; i < sectionTags.size(); i++) {
            CompoundTag sectionTag = sectionTags.getCompound(i);
            BitSet bits = BitSet.valueOf(sectionTag.getLongArray("Bits"));
            if (!bits.isEmpty()) {
                data.sections.put(sectionTag.getLong("Section"), bits);
            }
        }
        // Migration from the original one-long-per-block format.
        for (long position : tag.getLongArray("Positions")) {
            data.markInternal(BlockPos.of(position));
        }
        return data;
    }

    private boolean contains(BlockPos position) {
        BitSet bits = sections.get(sectionKey(position));
        return bits != null && bits.get(localIndex(position));
    }

    private boolean markInternal(BlockPos position) {
        BitSet bits = sections.computeIfAbsent(sectionKey(position), ignored -> new BitSet(4096));
        int index = localIndex(position);
        boolean changed = !bits.get(index);
        bits.set(index);
        return changed;
    }

    private boolean consumeInternal(BlockPos position) {
        long section = sectionKey(position);
        BitSet bits = sections.get(section);
        if (bits == null) {
            return false;
        }
        int index = localIndex(position);
        if (!bits.get(index)) {
            return false;
        }
        bits.clear(index);
        if (bits.isEmpty()) {
            sections.remove(section);
        }
        return true;
    }

    private static long sectionKey(BlockPos position) {
        return SectionPos.asLong(
                SectionPos.blockToSectionCoord(position.getX()),
                SectionPos.blockToSectionCoord(position.getY()),
                SectionPos.blockToSectionCoord(position.getZ()));
    }

    private static int localIndex(BlockPos position) {
        return (position.getY() & 15) << 8 | (position.getZ() & 15) << 4 | position.getX() & 15;
    }

    public static PlacedJobBlockSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PlacedJobBlockSavedData::new, PlacedJobBlockSavedData::load, null),
                "moveearth_jobs_placed_blocks");
    }
}
