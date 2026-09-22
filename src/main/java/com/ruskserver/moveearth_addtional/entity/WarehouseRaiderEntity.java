package com.ruskserver.moveearth_addtional.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

/** Warehouse-only TaCZ raider. Reuses the airship firing AI, never its loot or raid accounting. */
public final class WarehouseRaiderEntity extends AirshipRaiderEntity {
    private static final String ANCHOR_X = "MoveEarthWarehouseAnchorX";
    private static final String ANCHOR_Y = "MoveEarthWarehouseAnchorY";
    private static final String ANCHOR_Z = "MoveEarthWarehouseAnchorZ";

    public WarehouseRaiderEntity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    public void setWarehouseAnchor(BlockPos anchor) {
        getPersistentData().putInt(ANCHOR_X, anchor.getX());
        getPersistentData().putInt(ANCHOR_Y, anchor.getY());
        getPersistentData().putInt(ANCHOR_Z, anchor.getZ());
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() || tickCount % 20 != 0
                || !getPersistentData().contains(ANCHOR_X)) return;
        BlockPos anchor = new BlockPos(getPersistentData().getInt(ANCHOR_X),
                getPersistentData().getInt(ANCHOR_Y), getPersistentData().getInt(ANCHOR_Z));
        if (distanceToSqr(anchor.getCenter()) > 48.0D * 48.0D) {
            setTarget(null);
            getNavigation().stop();
            teleportTo(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D);
        }
    }

    @Override
    protected void dropEquipment() { }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) { }
}
