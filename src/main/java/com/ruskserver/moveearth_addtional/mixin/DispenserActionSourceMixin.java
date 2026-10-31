package com.ruskserver.moveearth_addtional.mixin;

import static com.ruskserver.moveearth_addtional.analytics.provenance.MutationRejectionAudit.rejected;

import com.ruskserver.moveearth_addtional.s2.provenance.WorldMutationGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reject before item consumption when no one answers for an automatic placement. */
@Mixin(DispenserBlock.class)
public abstract class DispenserActionSourceMixin {
    @Inject(method = "dispenseFrom", at = @At("HEAD"), cancellable = true)
    private void moveearth$sourceBeforeConsumption(ServerLevel level, BlockState state, BlockPos pos, CallbackInfo callback) {
        if (!WorldMutationGuard.fromBlock(level, pos).allowed()) {
            rejected("dispenser", level, pos);
            callback.cancel();
        }
    }
}
