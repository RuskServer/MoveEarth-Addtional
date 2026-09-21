package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.s2.reinforcement.MachineBreaking;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The same refusal for a drill carried on a moving contraption.
 *
 * <p>Separate from the stationary one because Create keeps the two apart: a
 * block entity breaks while bolted down, a movement behaviour breaks while
 * being carried past. A drill mounted on a ship is the second, and it is the
 * one that reaches a wall it was never driven to.
 */
@Mixin(value = BlockBreakingMovementBehaviour.class, remap = false)
public abstract class CreateMovingBlockBreakerMixin {

    @Inject(method = "canBreak", at = @At("HEAD"), cancellable = true, remap = false)
    private void moveearth$refuseReinforced(Level level, BlockPos pos, BlockState state,
                                            CallbackInfoReturnable<Boolean> callback) {
        if (level instanceof ServerLevel server && !MachineBreaking.mayBreak(server, pos)) {
            callback.setReturnValue(false);
        }
    }
}
