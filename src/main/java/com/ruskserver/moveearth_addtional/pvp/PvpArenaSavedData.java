package com.ruskserver.moveearth_addtional.pvp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class PvpArenaSavedData extends SavedData {
    private static final BlockPos DEFAULT_RED = new BlockPos(-20, 80, 0);
    private static final BlockPos DEFAULT_BLUE = new BlockPos(20, 80, 0);
    private static final BlockPos DEFAULT_HILL_MIN = new BlockPos(-4, 76, -4);
    private static final BlockPos DEFAULT_HILL_MAX = new BlockPos(4, 90, 4);

    private BlockPos redSpawn = DEFAULT_RED;
    private BlockPos blueSpawn = DEFAULT_BLUE;
    private BlockPos hillMin = DEFAULT_HILL_MIN;
    private BlockPos hillMax = DEFAULT_HILL_MAX;
    private boolean redSpawnSet;
    private boolean blueSpawnSet;
    private boolean hillMinSet;
    private boolean hillMaxSet;
    private boolean hosting;

    public BlockPos redSpawn() { return redSpawn; }
    public BlockPos blueSpawn() { return blueSpawn; }
    public BlockPos hillMin() { return hillMin; }
    public BlockPos hillMax() { return hillMax; }
    public boolean hosting() { return hosting; }
    public boolean configured() { return redSpawnSet && blueSpawnSet && hillMinSet && hillMaxSet; }

    public void setRedSpawn(BlockPos pos) { redSpawn = pos.immutable(); redSpawnSet = true; setDirty(); }
    public void setBlueSpawn(BlockPos pos) { blueSpawn = pos.immutable(); blueSpawnSet = true; setDirty(); }
    public void setHillMin(BlockPos pos) { hillMin = pos.immutable(); hillMinSet = true; setDirty(); }
    public void setHillMax(BlockPos pos) { hillMax = pos.immutable(); hillMaxSet = true; setDirty(); }
    public void setHosting(boolean hosting) { this.hosting = hosting; setDirty(); }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("RedSpawn", redSpawn.asLong());
        tag.putLong("BlueSpawn", blueSpawn.asLong());
        tag.putLong("HillMin", hillMin.asLong());
        tag.putLong("HillMax", hillMax.asLong());
        tag.putBoolean("RedSpawnSet", redSpawnSet);
        tag.putBoolean("BlueSpawnSet", blueSpawnSet);
        tag.putBoolean("HillMinSet", hillMinSet);
        tag.putBoolean("HillMaxSet", hillMaxSet);
        tag.putBoolean("Hosting", hosting);
        return tag;
    }

    public static PvpArenaSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PvpArenaSavedData data = new PvpArenaSavedData();
        if (tag.contains("RedSpawn")) data.redSpawn = BlockPos.of(tag.getLong("RedSpawn"));
        if (tag.contains("BlueSpawn")) data.blueSpawn = BlockPos.of(tag.getLong("BlueSpawn"));
        if (tag.contains("HillMin")) data.hillMin = BlockPos.of(tag.getLong("HillMin"));
        if (tag.contains("HillMax")) data.hillMax = BlockPos.of(tag.getLong("HillMax"));
        data.redSpawnSet = tag.contains("RedSpawnSet") ? tag.getBoolean("RedSpawnSet") : !data.redSpawn.equals(DEFAULT_RED);
        data.blueSpawnSet = tag.contains("BlueSpawnSet") ? tag.getBoolean("BlueSpawnSet") : !data.blueSpawn.equals(DEFAULT_BLUE);
        data.hillMinSet = tag.contains("HillMinSet") ? tag.getBoolean("HillMinSet") : !data.hillMin.equals(DEFAULT_HILL_MIN);
        data.hillMaxSet = tag.contains("HillMaxSet") ? tag.getBoolean("HillMaxSet") : !data.hillMax.equals(DEFAULT_HILL_MAX);
        data.hosting = tag.getBoolean("Hosting");
        return data;
    }

    public static PvpArenaSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PvpArenaSavedData::new, PvpArenaSavedData::load, null),
                "moveearth_pvp_arena");
    }
}
