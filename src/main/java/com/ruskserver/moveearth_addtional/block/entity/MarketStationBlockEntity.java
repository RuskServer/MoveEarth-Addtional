package com.ruskserver.moveearth_addtional.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/** Stable physical identity; virtual order inventory will be keyed by this ID. */
public final class MarketStationBlockEntity extends BlockEntity {
    private UUID stationId;
    private UUID nationId;

    public MarketStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MARKET_STATION.get(), pos, state);
    }

    public void bind(UUID stationId, UUID nationId) {
        this.stationId = stationId;
        this.nationId = nationId;
        setChanged();
    }

    public UUID stationId() { return stationId; }
    public UUID nationId() { return nationId; }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stationId = tag.hasUUID("StationId") ? tag.getUUID("StationId") : null;
        nationId = tag.hasUUID("Nation") ? tag.getUUID("Nation") : null;
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (stationId != null) tag.putUUID("StationId", stationId);
        if (nationId != null) tag.putUUID("Nation", nationId);
    }
}
