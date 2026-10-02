package com.ruskserver.moveearth_addtional.block.entity;

import com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
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

    /**
     * Keeps the vehicle record on this block wherever it ends up. Sable assembly
     * moves the record itself before this runs (onLoad comes on a later block
     * entity tick), so the record already matches and nothing happens. A Create
     * bearing, gantry or anything else that carries the block and sets it down
     * elsewhere leaves the record on the old spot; damage and explosions then no
     * longer find the core, and a single blast removes it as an ordinary block.
     * Loading at a new spot moves the record here, unless the old spot still
     * holds this same core (a copy), and binds it to a Sable body it landed on.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (!(level instanceof ServerLevel serverLevel) || vehicleId == null) return;
        VehicleSavedData vehicles = VehicleSavedData.get(serverLevel.getServer());
        VehicleSavedData.VehicleRecord record = vehicles.vehicle(vehicleId).orElse(null);
        if (record == null || (record.dimension().equals(serverLevel.dimension().location())
                && record.corePos().equals(worldPosition))) return;
        if (stillAtRecordedSpot(serverLevel, record)) return;
        dev.ryanhcode.sable.sublevel.ServerSubLevel body = dev.ryanhcode.sable.Sable.HELPER
                .getContaining(serverLevel, worldPosition) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel subLevel
                && !subLevel.isRemoved() ? subLevel : null;
        VehicleSavedData.VehicleRecord moved = vehicles.move(vehicleId, serverLevel.dimension().location(),
                worldPosition, body == null ? null : body.getUniqueId());
        if (moved == null) return;
        bind(moved);
        // Without the id on the body, the craft's armour never counts as this vehicle's.
        if (body != null) com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology
                .bindIfUnbound(serverLevel, body, vehicleId);
    }

    private boolean stillAtRecordedSpot(ServerLevel here, VehicleSavedData.VehicleRecord record) {
        ServerLevel recorded = here.getServer().getLevel(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        record.dimension()));
        return recorded != null && recorded.isLoaded(record.corePos())
                && recorded.getBlockEntity(record.corePos()) instanceof VehicleCoreBlockEntity other
                && other != this && vehicleId.equals(other.vehicleId);
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
