package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.compat.create.WaterWheelBalance;
import com.ruskserver.moveearth_addtional.compat.create.WaterWheelCapacityState;
import com.ruskserver.moveearth_addtional.compat.create.WaterWheelOffsetAccess;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = KineticBlockEntity.class, remap = false)
public abstract class WaterWheelCapacityMixin implements WaterWheelCapacityState {
    @Shadow protected float lastCapacityProvided;
    @Shadow public abstract boolean hasNetwork();
    @Shadow public abstract KineticNetwork getOrCreateNetwork();
    @Shadow public abstract float calculateAddedStressCapacity();

    @Unique private double moveearth$waterWheelMultiplier = Double.NaN;
    @Unique private long moveearth$waterWheelMultiplierValidUntil = Long.MIN_VALUE;

    @Inject(method = "calculateAddedStressCapacity", at = @At("RETURN"), cancellable = true)
    private void moveearth$balanceWaterWheelCapacity(CallbackInfoReturnable<Float> callback) {
        if (!((Object) this instanceof WaterWheelBlockEntity wheel)) return;
        var level = ((BlockEntity) wheel).getLevel();
        long gameTime = level == null ? 0L : level.getGameTime();
        if (Double.isNaN(moveearth$waterWheelMultiplier)
                || gameTime >= moveearth$waterWheelMultiplierValidUntil) {
            moveearth$waterWheelMultiplier = WaterWheelBalance.capacityMultiplier(
                    wheel, ((WaterWheelOffsetAccess) (Object) wheel).moveearth$getOffsetsToCheck());
            moveearth$waterWheelMultiplierValidUntil = gameTime + 60L;
        }
        float adjusted = (float) (callback.getReturnValueF() * moveearth$waterWheelMultiplier);
        lastCapacityProvided = adjusted;
        callback.setReturnValue(adjusted);
    }

    @Override
    public double moveearth$getWaterWheelMultiplier() {
        return moveearth$waterWheelMultiplier;
    }

    @Override
    public long moveearth$getWaterWheelMultiplierValidUntil() {
        return moveearth$waterWheelMultiplierValidUntil;
    }

    @Override
    public void moveearth$setWaterWheelMultiplier(double multiplier, long validUntil) {
        moveearth$waterWheelMultiplier = multiplier;
        moveearth$waterWheelMultiplierValidUntil = validUntil;
    }

    @Override
    public void moveearth$invalidateWaterWheelMultiplier() {
        moveearth$waterWheelMultiplier = Double.NaN;
        moveearth$waterWheelMultiplierValidUntil = Long.MIN_VALUE;
    }

    @Override
    public void moveearth$refreshWaterWheelCapacity() {
        if (!hasNetwork()) return;
        KineticBlockEntity self = (KineticBlockEntity) (Object) this;
        getOrCreateNetwork().updateCapacityFor(self, calculateAddedStressCapacity());
    }
}
