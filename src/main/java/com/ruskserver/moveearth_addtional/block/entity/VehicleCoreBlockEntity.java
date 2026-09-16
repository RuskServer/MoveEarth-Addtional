package com.ruskserver.moveearth_addtional.block.entity;

import com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public final class VehicleCoreBlockEntity extends BlockEntity {
    private UUID vehicleId;
    private UUID nationId;
    private UUID placedBy;

    public VehicleCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VEHICLE_CORE.get(), pos, state);
    }

    public void bind(VehicleSavedData.VehicleRecord record) {
        vehicleId = record.id();
        nationId = record.nationId();
        placedBy = record.placedBy();
        setChanged();
    }

    public UUID vehicleId() { return vehicleId; }
    public UUID nationId() { return nationId; }
    public UUID placedBy() { return placedBy; }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        vehicleId = tag.hasUUID("VehicleId") ? tag.getUUID("VehicleId") : null;
        nationId = tag.hasUUID("Nation") ? tag.getUUID("Nation") : null;
        placedBy = tag.hasUUID("PlacedBy") ? tag.getUUID("PlacedBy") : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (vehicleId != null) tag.putUUID("VehicleId", vehicleId);
        if (nationId != null) tag.putUUID("Nation", nationId);
        if (placedBy != null) tag.putUUID("PlacedBy", placedBy);
    }
}
