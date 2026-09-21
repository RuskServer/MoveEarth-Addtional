package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.s2.reinforcement.MachineBreaking;
import com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops a stationary drill or saw from eating a reinforced wall.
 *
 * <p>The base class of everything Create breaks blocks with, so drills, saws
 * and anything a mod builds on them are all covered by one answer.
 *
 * <p>The position comes from {@code getBreakingPos} rather than the {@code
 * breakingPos} field: the field is only set once breaking is already under way,
 * while this question is asked before that, and reading it would have let the
 * first block through.
 */
@Mixin(value = BlockBreakingKineticBlockEntity.class, remap = false)
public abstract class CreateBlockBreakerMixin {

    @Shadow protected abstract BlockPos getBreakingPos();

    @Inject(method = "canBreak", at = @At("HEAD"), cancellable = true, remap = false)
    private void moveearth$refuseReinforced(BlockState state, float hardness,
                                            CallbackInfoReturnable<Boolean> callback) {
        BlockEntity self = (BlockEntity) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos target = getBreakingPos();
        if (target != null && !MachineBreaking.mayBreak(level, target)) {
            callback.setReturnValue(false);
        }
    }
}
