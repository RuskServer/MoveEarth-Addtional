package com.ruskserver.moveearth_addtional.mixin;

import static com.ruskserver.moveearth_addtional.analytics.provenance.MutationRejectionAudit.rejected;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.ruskserver.moveearth_addtional.s2.provenance.ActionContext;
import com.ruskserver.moveearth_addtional.s2.provenance.WorldMutationGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** Carries the base's owner into moving piston block entities before blocks are removed. */
@Mixin(PistonBaseBlock.class)
public abstract class PistonActionSourceMixin {
    @WrapMethod(method = "triggerEvent")
    private boolean moveearth$pistonOrigin(BlockState state, Level level, BlockPos pos, int id, int param,
                                           Operation<Boolean> original) {
        if (!(level instanceof ServerLevel server)) return original.call(state, level, pos, id, param);
        var source = WorldMutationGuard.fromBlock(server, pos);
        if (!source.allowed()) {
            rejected("piston.trigger", level, pos);
            return false;
        }
        try (var scope = ActionContext.push(source)) { return original.call(state, level, pos, id, param); }
    }
}
