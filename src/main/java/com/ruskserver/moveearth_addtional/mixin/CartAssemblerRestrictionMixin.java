package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.compat.create.MinecartContraptionRules;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.mounted.CartAssemblerBlockEntity;
import com.simibubi.create.content.contraptions.mounted.MountedContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies MoveEarth freight rules before Create removes or restores any world blocks. */
@Mixin(value = CartAssemblerBlockEntity.class, remap = false)
public abstract class CartAssemblerRestrictionMixin {
    @Redirect(method = "assemble", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/contraptions/mounted/MountedContraption;assemble(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z"),
            remap = false)
    private boolean moveearth$validateBeforeRemoval(MountedContraption contraption, Level level,
                                                     BlockPos pos, Level ignoredLevel,
                                                     BlockPos ignoredPos, AbstractMinecart cart)
            throws AssemblyException {
        boolean assembled = contraption.assemble(level, pos);
        if (!assembled || !(level instanceof ServerLevel server)) return assembled;
        return MinecartContraptionRules.validateAssembly(server, pos, contraption, cart);
    }

    @Inject(method = "disassemble", at = @At("HEAD"), cancellable = true, remap = false)
    private void moveearth$denyForeignDisassembly(Level level, BlockPos pos, AbstractMinecart cart,
                                                   CallbackInfo callback) {
        if (level instanceof ServerLevel server
                && !MinecartContraptionRules.canDisassemble(server, pos, cart)) callback.cancel();
    }
}
