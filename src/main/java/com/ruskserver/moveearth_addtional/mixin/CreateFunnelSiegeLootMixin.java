package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.s2.nation.NationStorageEvents;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Conservative adjacency guard for actor-less funnel extraction during a loot boundary. */
@Mixin(value = FunnelBlockEntity.class, remap = false)
public abstract class CreateFunnelSiegeLootMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void moveearth$blockContestedAutomation(CallbackInfo ci) {
        net.minecraft.world.level.block.entity.BlockEntity self =
                (net.minecraft.world.level.block.entity.BlockEntity) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel level)) return;
        for (Direction direction : Direction.values()) {
            BlockPos source = self.getBlockPos().relative(direction);
            if (level.getBlockState(source).is(NationStorageEvents.STORAGE_BLOCKS)
                    && NationStorageEvents.automationRestricted(level, source)) {
                ci.cancel();
                return;
            }
        }
    }
}
