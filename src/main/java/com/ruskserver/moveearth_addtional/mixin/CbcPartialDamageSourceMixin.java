package com.ruskserver.moveearth_addtional.mixin;

import static com.ruskserver.moveearth_addtional.analytics.provenance.MutationRejectionAudit.rejected;

import com.ruskserver.moveearth_addtional.s2.provenance.ActionContext;
import com.ruskserver.moveearth_addtional.s2.provenance.WorldMutationGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.function.BiConsumer;

/** Deny unknown damage before it accumulates a persistent crack/progress, not merely at removal. */
@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.base.PartialBlockDamageManager", remap = false)
public abstract class CbcPartialDamageSourceMixin {
    @Inject(method = "damageBlock(Lnet/minecraft/core/BlockPos;FLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Ljava/util/function/BiConsumer;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void moveearth$requirePartialDamageOrigin(BlockPos pos, float damage, BlockState state, Level level,
                                                       BiConsumer<Level, BlockPos> destroy,
                                                       CallbackInfoReturnable<Boolean> callback) {
        if (level instanceof ServerLevel && !WorldMutationGuard.source(ActionContext.current()).allowed()) {
            rejected("cbc.partial_damage", level, pos);
            callback.setReturnValue(false);
        }
    }
}
