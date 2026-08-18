package com.ruskserver.moveearth_addtional.territory.data;

import com.ruskserver.moveearth_addtional.territory.domain.TerritoryCore;
import com.ruskserver.moveearth_addtional.territory.domain.TerritoryOwnerId;
import com.ruskserver.moveearth_addtional.territory.domain.TerritoryPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.UUID;

public final class TerritoryCoreSavedData extends SavedData {
    private static final String DATA_NAME = "moveearth_territory_cores";
    private final TerritoryCoreRegistry registry = new TerritoryCoreRegistry();

    public void register(TerritoryCore core) {
        if (registry.register(core)) {
            setDirty();
        }
    }

    public boolean unregister(UUID coreId) {
        if (registry.unregister(coreId)) {
            setDirty();
            return true;
        }
        return false;
    }

    public boolean unregisterAt(String dimensionId, BlockPos pos) {
        if (registry.unregisterAt(dimensionId, pos.getX(), pos.getY(), pos.getZ())) {
            setDirty();
            return true;
        }
        return false;
    }

    public Collection<TerritoryCore> allCores() {
        return registry.allCores();
    }

    public Collection<TerritoryCore> coresIn(String dimensionId) {
        return registry.coresIn(dimensionId);
    }

    public int size() {
        return registry.size();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (TerritoryCore core : registry.allCores()) {
            CompoundTag coreTag = new CompoundTag();
            coreTag.putUUID("CoreId", core.id());
            coreTag.putUUID("OwnerUUID", core.ownerId().value());
            coreTag.putString("Dimension", core.position().dimensionId());
            coreTag.putLong("Position", blockPos(core.position()).asLong());
            coreTag.putBoolean("Active", core.active());
            list.add(coreTag);
        }
        tag.put("Cores", list);
        return tag;
    }

    public static TerritoryCoreSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TerritoryCoreSavedData data = new TerritoryCoreSavedData();
        ListTag list = tag.getList("Cores", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag coreTag = list.getCompound(index);
            if (!coreTag.hasUUID("CoreId") || !coreTag.hasUUID("OwnerUUID")) {
                continue;
            }
            String dimensionId = coreTag.getString("Dimension");
            if (dimensionId.isBlank()) {
                continue;
            }
            BlockPos pos = BlockPos.of(coreTag.getLong("Position"));
            TerritoryCore core = new TerritoryCore(
                    coreTag.getUUID("CoreId"),
                    TerritoryOwnerId.of(coreTag.getUUID("OwnerUUID")),
                    new TerritoryPosition(dimensionId,
                            pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D),
                    !coreTag.contains("Active") || coreTag.getBoolean("Active")
            );
            data.registry.register(core);
        }
        return data;
    }

    public static TerritoryCoreSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        TerritoryCoreSavedData::new,
                        TerritoryCoreSavedData::load,
                        null
                ),
                DATA_NAME
        );
    }

    private static BlockPos blockPos(TerritoryPosition position) {
        return BlockPos.containing(position.x(), position.y(), position.z());
    }
}
