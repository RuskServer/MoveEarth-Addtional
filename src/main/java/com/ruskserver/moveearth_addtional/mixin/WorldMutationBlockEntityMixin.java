package com.ruskserver.moveearth_addtional.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.ruskserver.moveearth_addtional.s2.provenance.ActionContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class WorldMutationBlockEntityMixin {
    @Shadow @Final private BlockEntity blockEntity;
    @WrapMethod(method = "tick")
    private void moveearth$machineOrigin(Operation<Void> original) {
        try (var scope = ActionContext.push(blockEntity)) { original.call(); }
    }
}
