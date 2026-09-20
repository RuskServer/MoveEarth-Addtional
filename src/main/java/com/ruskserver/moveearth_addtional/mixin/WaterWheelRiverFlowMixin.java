package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.compat.create.WaterWheelBalance;
import com.ruskserver.moveearth_addtional.compat.create.WaterWheelCapacityState;
import com.ruskserver.moveearth_addtional.compat.create.WaterWheelOffsetAccess;
import com.ruskserver.moveearth_addtional.terrain.RiverCurrent;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Lets a water wheel read the current of one of our rivers.
 *
 * <p>Create turns its wheel from {@code FluidState#getFlow}, which is the
 * gradient Minecraft's own fluid leaves behind as it settles. Our channels
 * never settle: they are placed as still source blocks at a height the
 * simulation decided, deliberately, because a river whose level steps down in
 * whole blocks would otherwise spread into the ground beside it every time it
 * dropped. So the water is there, it visibly runs downhill, and the flow the
 * wheel asks for is zero.
 *
 * <p>The tile has the answer -- see {@link RiverCurrent} -- so it is handed
 * over here, and only where the wheel would otherwise have found nothing. Water
 * that really is flowing, from a player's channel or a waterfall, keeps its own
 * direction; this never argues with it.
 */
@Mixin(value = WaterWheelBlockEntity.class, remap = false)
public abstract class WaterWheelRiverFlowMixin implements WaterWheelOffsetAccess {
    @Shadow protected abstract Set<BlockPos> getOffsetsToCheck();

    @Override
    public Set<BlockPos> moveearth$getOffsetsToCheck() {
        return getOffsetsToCheck();
    }

    @Inject(method = "getFlowVectorAtPosition", at = @At("RETURN"), cancellable = true)
    private void moveearth$riverCurrent(BlockPos pos, CallbackInfoReturnable<Vec3> callback) {
        Vec3 found = callback.getReturnValue();
        if (found != null && found.lengthSqr() > 1.0E-6D) {
            return;
        }
        Level level = ((BlockEntity) (Object) this).getLevel();
        if (level == null) {
            return;
        }
        RiverCurrent current = WaterWheelBalance.riverCurrentAt(level, pos);
        if (!current.present()) {
            return;
        }
        callback.setReturnValue(new Vec3(current.x(), 0.0, current.z()));
    }

    @Inject(method = "determineAndApplyFlowScore", at = @At("RETURN"))
    private void moveearth$refreshBalancedCapacity(CallbackInfo callback) {
        WaterWheelBlockEntity wheel = (WaterWheelBlockEntity) (Object) this;
        WaterWheelCapacityState capacity = (WaterWheelCapacityState) (Object) wheel;
        capacity.moveearth$invalidateWaterWheelMultiplier();
        Level level = ((BlockEntity) (Object) this).getLevel();
        if (level == null || level.isClientSide || wheel.flowScore == 0) return;
        capacity.moveearth$refreshWaterWheelCapacity();
    }
}
