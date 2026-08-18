package com.ruskserver.moveearth_addtional.block.entity;

import com.ruskserver.moveearth_addtional.territory.data.TerritoryCoreSavedData;
import com.ruskserver.moveearth_addtional.territory.domain.TerritoryCore;
import com.ruskserver.moveearth_addtional.territory.domain.TerritoryOwnerId;
import com.ruskserver.moveearth_addtional.territory.domain.TerritoryPosition;
import com.ruskserver.moveearth_addtional.territory.create.TerritoryCreateConfig;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public final class TerritoryCoreBlockEntity extends KineticBlockEntity {
    private UUID coreId = UUID.randomUUID();
    private UUID ownerUUID;
    private String ownerName = "";
    private boolean active = true;

    public TerritoryCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TERRITORY_CORE.get(), pos, state);
    }

    public UUID getCoreId() {
        return coreId;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public float calculateStressApplied() {
        float impact = TerritoryCreateConfig.DIRECT_CORE_STRESS_IMPACT.get().floatValue();
        lastStressApplied = impact;
        return impact;
    }

    public void setOwner(UUID ownerUUID, String ownerName) {
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName == null ? "" : ownerName;
        setChanged();
    }

    public void setActive(boolean active) {
        this.active = active;
        setChanged();
        if (level instanceof ServerLevel serverLevel && ownerUUID != null) {
            TerritoryCoreSavedData.get(serverLevel.getServer()).register(toDomain(serverLevel));
        }
    }

    public TerritoryCore toDomain(ServerLevel level) {
        if (ownerUUID == null) {
            throw new IllegalStateException("An unclaimed territory core has no domain representation");
        }
        return new TerritoryCore(
                coreId,
                TerritoryOwnerId.of(ownerUUID),
                new TerritoryPosition(
                        level.dimension().location().toString(),
                        worldPosition.getX() + 0.5D,
                        worldPosition.getY() + 0.5D,
                        worldPosition.getZ() + 0.5D
                ),
                active
        );
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel && ownerUUID != null) {
            TerritoryCoreSavedData.get(serverLevel.getServer()).register(toDomain(serverLevel));
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.hasUUID("CoreId")) {
            coreId = tag.getUUID("CoreId");
        }
        if (tag.hasUUID("OwnerUUID")) {
            ownerUUID = tag.getUUID("OwnerUUID");
        }
        ownerName = tag.getString("OwnerName");
        if (tag.contains("Active")) {
            active = tag.getBoolean("Active");
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putUUID("CoreId", coreId);
        if (ownerUUID != null) {
            tag.putUUID("OwnerUUID", ownerUUID);
        }
        tag.putString("OwnerName", ownerName);
        tag.putBoolean("Active", active);
    }
}
