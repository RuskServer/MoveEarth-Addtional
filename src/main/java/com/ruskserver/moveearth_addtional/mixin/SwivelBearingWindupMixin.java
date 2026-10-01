package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.compat.aeronautics.JointWindupLimit;
import com.ruskserver.moveearth_addtional.config.AeronauticsSwivelConfig;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintHandle;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops a Create: Simulated 1.3 swivel bearing from storing force while its
 * plate is held back; see {@link JointWindupLimit}.
 */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity", remap = false)
public abstract class SwivelBearingWindupMixin {
    private static final ResourceLocation MOVEEARTH$PLATE =
            ResourceLocation.fromNamespaceAndPath("simulated", "swivel_bearing_link_block");

    @Shadow private double lastTargetAngleDegrees;
    @Shadow private double targetAngleDegrees;
    @Shadow private double sequencedAngleLimit;
    @Shadow private RotaryConstraintHandle handle;

    @Shadow public abstract boolean isAssembled();
    @Shadow public abstract BlockPos getPlatePos();
    @Shadow private SubLevel getAttachedSubLevel() { throw new AssertionError(); }
    @Shadow private boolean isLocking() { throw new AssertionError(); }
    @Shadow private void setTargetAngleFromCurrentOrientation(BlockState plateState, SubLevel attached) {
        throw new AssertionError();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void moveearth$limitWindup(CallbackInfo ci) {
        double previous = lastTargetAngleDegrees;
        double proposed = targetAngleDegrees;
        if (proposed == previous || !AeronauticsSwivelConfig.jointWindupLimitEnabled()) return;
        Level level = ((BlockEntity) (Object) this).getLevel();
        if (level == null || level.isClientSide || handle == null || !isAssembled() || !isLocking()) return;
        SubLevel attached = getAttachedSubLevel();
        BlockPos platePos = getPlatePos();
        if (attached == null || platePos == null) return;
        BlockState plateState = level.getBlockState(platePos);
        if (!MOVEEARTH$PLATE.equals(BuiltInRegistries.BLOCK.getKey(plateState.getBlock()))) return;

        // Simulated measures the plate's angle only inside this method, which writes it
        // to both target fields; read it there and put the fields back.
        setTargetAngleFromCurrentOrientation(plateState, attached);
        double current = targetAngleDegrees;
        lastTargetAngleDegrees = previous;
        targetAngleDegrees = proposed;

        double limited = JointWindupLimit.limitWrappedAdvance(previous, proposed, current,
                AeronauticsSwivelConfig.jointMaxLeadDegrees());
        if (limited == proposed) return;
        targetAngleDegrees = limited;
        // A sequenced turn was charged for the whole step; give back what was held.
        if (sequencedAngleLimit >= 0.0) sequencedAngleLimit += Math.abs(JointWindupLimit.wrap(proposed - limited));
    }
}
