package com.ruskserver.moveearth_addtional.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.ruskserver.moveearth_addtional.s2.provenance.ActionContext;
import com.ruskserver.moveearth_addtional.s2.provenance.WorldMutationGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerLevel.class)
public abstract class WorldMutationTickMixin {
    @WrapMethod(method = "tickNonPassenger")
    private void moveearth$entityOrigin(Entity entity, Operation<Void> original) {
        try (var scope = ActionContext.push(entity)) { original.call(entity); }
    }
    @WrapMethod(method = "tickPassenger")
    private void moveearth$passengerOrigin(Entity vehicle, Entity passenger, Operation<Void> original) {
        try (var scope = ActionContext.push(passenger)) { original.call(vehicle, passenger); }
    }
    @WrapMethod(method = "tickBlock")
    private void moveearth$scheduledMachineOrigin(BlockPos pos, Block block, Operation<Void> original) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (block instanceof DispenserBlock || block instanceof PistonBaseBlock
                || level.getBlockState(pos).hasBlockEntity()) {
            try (var scope = ActionContext.push(new WorldMutationGuard.BlockOrigin(level, pos.immutable()))) {
                original.call(pos, block);
            }
        } else original.call(pos, block);
    }
}
