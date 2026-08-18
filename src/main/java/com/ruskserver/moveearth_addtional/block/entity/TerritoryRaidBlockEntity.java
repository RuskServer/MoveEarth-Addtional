package com.ruskserver.moveearth_addtional.block.entity;

import com.ruskserver.moveearth_addtional.territory.raid.TerritoryRaidConfig;
import com.ruskserver.moveearth_addtional.territory.raid.TerritoryRaidService;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public final class TerritoryRaidBlockEntity extends KineticBlockEntity {
    private UUID emitterId = UUID.randomUUID();
    private UUID ownerUUID;
    private String ownerName = "";
    private boolean armed = true;
    private TerritoryRaidService.State runtimeState = TerritoryRaidService.State.INACTIVE;
    private double validStress;
    private double suppressionStrength;

    public TerritoryRaidBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TERRITORY_RAID.get(), pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (level instanceof ServerLevel serverLevel
                && (serverLevel.getGameTime() + worldPosition.asLong())
                % TerritoryRaidConfig.REFRESH_INTERVAL.get() == 0L) {
            refreshRaidState();
        }
    }

    @Override
    public float calculateStressApplied() {
        float impact = TerritoryRaidConfig.STRESS_IMPACT.get().floatValue();
        lastStressApplied = impact;
        return impact;
    }

    public void refreshRaidState() {
        runtimeState = TerritoryRaidService.refresh(this);
        if (runtimeState != TerritoryRaidService.State.ACTIVE) {
            setRuntimePower(0.0D, 0.0D);
        }
    }

    public UUID getEmitterId() {
        return emitterId;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public boolean isArmed() {
        return armed;
    }

    public TerritoryRaidService.State getRuntimeState() {
        return runtimeState;
    }

    public double getValidStress() {
        return validStress;
    }

    public double getSuppressionStrength() {
        return suppressionStrength;
    }

    public void setOwner(UUID ownerUUID, String ownerName) {
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName == null ? "" : ownerName;
        setChanged();
    }

    public void setArmed(boolean armed) {
        this.armed = armed;
        setChanged();
        refreshRaidState();
    }

    public void setRuntimePower(double validStress, double suppressionStrength) {
        this.validStress = validStress;
        this.suppressionStrength = suppressionStrength;
    }

    @Override
    public void remove() {
        TerritoryRaidService.remove(this);
        super.remove();
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.hasUUID("EmitterId")) {
            emitterId = tag.getUUID("EmitterId");
        }
        if (tag.hasUUID("OwnerUUID")) {
            ownerUUID = tag.getUUID("OwnerUUID");
        }
        ownerName = tag.getString("OwnerName");
        if (tag.contains("Armed")) {
            armed = tag.getBoolean("Armed");
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putUUID("EmitterId", emitterId);
        if (ownerUUID != null) {
            tag.putUUID("OwnerUUID", ownerUUID);
        }
        tag.putString("OwnerName", ownerName);
        tag.putBoolean("Armed", armed);
    }
}
