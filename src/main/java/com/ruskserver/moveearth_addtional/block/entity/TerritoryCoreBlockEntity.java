package com.ruskserver.moveearth_addtional.block.entity;

import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public final class TerritoryCoreBlockEntity extends BlockEntity {
    private UUID coreId;
    private UUID nationId;
    private UUID placedBy;
    private int radius = 1;

    public TerritoryCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TERRITORY_CORE.get(), pos, state);
    }

    private TerritorySavedData.CoreType coreType = TerritorySavedData.CoreType.OUTPOST;
    private TerritorySavedData.CoreState coreState = TerritorySavedData.CoreState.CONFIGURING;

    public void bind(TerritorySavedData.CoreRecord record) {
        this.coreId = record.id();
        this.nationId = record.nationId();
        this.placedBy = record.placedBy();
        this.radius = record.radius();
        this.coreType = record.type();
        this.coreState = record.state();
        setChanged();
    }

    public UUID coreId() { return coreId; }
    public UUID nationId() { return nationId; }
    public UUID placedBy() { return placedBy; }
    public int radius() { return radius; }
    public TerritorySavedData.CoreType coreType() { return coreType; }
    public TerritorySavedData.CoreState coreState() { return coreState; }

    public void setRadius(int radius) {
        this.radius = Math.max(0, Math.min(4, radius));
        setChanged();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        coreId = tag.hasUUID("CoreId") ? tag.getUUID("CoreId") : null;
        nationId = tag.hasUUID("Nation") ? tag.getUUID("Nation") : null;
        placedBy = tag.hasUUID("PlacedBy") ? tag.getUUID("PlacedBy") : null;
        radius = Math.max(0, Math.min(4, tag.getInt("Radius")));
        try { coreType = TerritorySavedData.CoreType.valueOf(tag.getString("CoreType")); }
        catch (IllegalArgumentException ignored) { coreType = TerritorySavedData.CoreType.OUTPOST; }
        try { coreState = TerritorySavedData.CoreState.valueOf(tag.getString("CoreState")); }
        catch (IllegalArgumentException ignored) { coreState = TerritorySavedData.CoreState.CONFIGURING; }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (coreId != null) tag.putUUID("CoreId", coreId);
        if (nationId != null) tag.putUUID("Nation", nationId);
        if (placedBy != null) tag.putUUID("PlacedBy", placedBy);
        tag.putInt("Radius", radius);
        tag.putString("CoreType", coreType.name());
        tag.putString("CoreState", coreState.name());
    }
}
