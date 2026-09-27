package com.ruskserver.moveearth_addtional.mixin.create;

import com.ruskserver.moveearth_addtional.compat.create.BoilerBurnerFuelView;
import com.ruskserver.moveearth_addtional.compat.create.SteamBoilerFuel;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scales a boiler burner's fuel use by its engines' load ({@link SteamBoilerFuel}).
 *
 * <p>Create's tick takes exactly one burn tick per game tick. Rather than
 * replace that, the difference between the target rate and one is accumulated
 * here and settled in whole ticks before Create's decrement runs, so the
 * burner's fuel-type and heat-level handling stay Create's own.
 *
 * <p>The rate travels to clients in the block entity's update packet only
 * (never saved), for the boiler's goggle readout.
 */
@Mixin(value = BlazeBurnerBlockEntity.class, remap = false)
public abstract class BlazeBurnerBoilerFuelMixin implements BoilerBurnerFuelView {
    @Unique private static final int RATE_REFRESH_TICKS = 20;
    /** Synced rates are rounded to 5% steps so small load wobbles send no packets. */
    @Unique private static final double SYNC_STEPS = 20.0D;
    @Unique private static final String SYNC_KEY = "MoveEarthBurnRate";

    @Shadow protected int remainingBurnTime;
    @Shadow public boolean isCreative;

    @Unique private double moveearth$burnRate = 1.0D;
    @Unique private double moveearth$syncedRate = 1.0D;
    @Unique private double moveearth$burnDebt;
    @Unique private int moveearth$rateRefresh;

    @Inject(method = "tick", at = @At("HEAD"))
    private void moveearth$scaleBoilerFuel(CallbackInfo callback) {
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide() || isCreative || remainingBurnTime <= 0) return;
        if (--moveearth$rateRefresh <= 0) {
            moveearth$rateRefresh = RATE_REFRESH_TICKS;
            moveearth$burnRate = SteamBoilerFuel.burnRate(level, self.getBlockPos());
            double synced = Math.round(moveearth$burnRate * SYNC_STEPS) / SYNC_STEPS;
            if (synced != moveearth$syncedRate) {
                moveearth$syncedRate = synced;
                ((SmartBlockEntity) (Object) this).sendData();
            }
        }
        moveearth$burnDebt += moveearth$burnRate - 1.0D;
        int settled = (int) moveearth$burnDebt;
        if (settled == 0) return;
        moveearth$burnDebt -= settled;
        remainingBurnTime = Math.max(0, remainingBurnTime - settled);
    }

    @Inject(method = "write", at = @At("HEAD"))
    private void moveearth$writeBurnRate(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket,
                                         CallbackInfo callback) {
        if (clientPacket && moveearth$syncedRate != 1.0D) tag.putDouble(SYNC_KEY, moveearth$syncedRate);
    }

    @Inject(method = "read", at = @At("HEAD"))
    private void moveearth$readBurnRate(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket,
                                        CallbackInfo callback) {
        if (clientPacket) moveearth$syncedRate = tag.contains(SYNC_KEY) ? tag.getDouble(SYNC_KEY) : 1.0D;
    }

    @Override
    public double moveearth$getBurnRate() {
        return moveearth$syncedRate;
    }
}
