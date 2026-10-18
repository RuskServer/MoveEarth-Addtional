package com.ruskserver.moveearth_addtional.s2.territory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The last wall-layer count of each capital ({@link CapitalWallService}), kept across restarts. */
public final class CapitalWallSavedData extends SavedData {
    /** Weak points kept per capital: the outermost barriers on the thinnest way in. */
    static final int MAX_WEAK_POINTS = 4;

    private final Map<UUID, Wall> walls = new HashMap<>();

    /**
     * {@code layers} is exact unless {@code atLeast}; {@code weakPoints} run from the outside in.
     * {@code checkedAtMillis} is wall-clock time of the count.
     */
    public record Wall(int layers, boolean atLeast, List<BlockPos> weakPoints, long checkedAtMillis) {
        public Wall {
            layers = Math.max(0, layers);
            weakPoints = weakPoints == null ? List.of()
                    : List.copyOf(weakPoints.subList(0, Math.min(MAX_WEAK_POINTS, weakPoints.size())));
        }

        /** Below {@code target} layers, so the nation should be warned. */
        public boolean below(int target) {
            return !atLeast && layers < target;
        }
    }

    public Optional<Wall> get(UUID coreId) {
        return Optional.ofNullable(walls.get(coreId));
    }

    /** Stores a new count and returns the one it replaced. */
    Optional<Wall> put(UUID coreId, Wall wall) {
        Wall previous = walls.put(coreId, wall);
        setDirty();
        return Optional.ofNullable(previous);
    }

    void remove(UUID coreId) {
        if (walls.remove(coreId) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var entry : walls.entrySet()) {
            CompoundTag wall = new CompoundTag();
            wall.putUUID("Core", entry.getKey());
            wall.putInt("Layers", entry.getValue().layers());
            wall.putBoolean("AtLeast", entry.getValue().atLeast());
            wall.putLong("CheckedAt", entry.getValue().checkedAtMillis());
            wall.putLongArray("Weak", entry.getValue().weakPoints().stream().mapToLong(BlockPos::asLong).toArray());
            list.add(wall);
        }
        tag.put("Walls", list);
        return tag;
    }

    public static CapitalWallSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        CapitalWallSavedData data = new CapitalWallSavedData();
        for (Tag entry : tag.getList("Walls", Tag.TAG_COMPOUND)) {
            CompoundTag wall = (CompoundTag) entry;
            if (!wall.hasUUID("Core")) continue;
            List<BlockPos> weak = new ArrayList<>();
            for (long pos : wall.getLongArray("Weak")) weak.add(BlockPos.of(pos));
            data.walls.put(wall.getUUID("Core"), new Wall(wall.getInt("Layers"), wall.getBoolean("AtLeast"),
                    weak, wall.getLong("CheckedAt")));
        }
        return data;
    }

    public static CapitalWallSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CapitalWallSavedData::new, CapitalWallSavedData::load, null),
                "moveearth_capital_walls");
    }
}
