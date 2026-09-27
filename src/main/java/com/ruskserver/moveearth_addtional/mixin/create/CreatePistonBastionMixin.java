package com.ruskserver.moveearth_addtional.mixin.create;

import com.ruskserver.moveearth_addtional.s2.territory.BastionCreateProtection;
import com.simibubi.create.content.contraptions.piston.PistonContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reject captured or projected blocks before the mechanical piston removes world blocks. */
@Mixin(value = PistonContraption.class, remap = false)
public abstract class CreatePistonBastionMixin {
    @Shadow protected int extensionLength;
    @Shadow protected int initialExtensionProgress;
    @Shadow protected Direction orientation;

    @Inject(method = "assemble", at = @At("RETURN"), cancellable = true)
    private void moveearth$checkBastion(Level level, BlockPos actuator,
                                        CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValue() || !(level instanceof ServerLevel serverLevel)) return;
        PistonContraption contraption = (PistonContraption) (Object) this;
        if (!BastionCreateProtection.canAssemblePiston(serverLevel, actuator, contraption.anchor,
                orientation, initialExtensionProgress, extensionLength, contraption.getBlocks().keySet())) {
            callback.setReturnValue(false);
        }
    }
}
