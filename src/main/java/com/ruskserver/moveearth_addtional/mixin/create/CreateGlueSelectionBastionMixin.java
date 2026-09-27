package com.ruskserver.moveearth_addtional.mixin.create;

import com.ruskserver.moveearth_addtional.s2.territory.BastionCreateProtection;
import com.simibubi.create.content.contraptions.glue.SuperGlueSelectionPacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Block Create's direct glue packet before it consumes an item or spawns glue in enemy land. */
@Mixin(value = SuperGlueSelectionPacket.class, remap = false)
public abstract class CreateGlueSelectionBastionMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void moveearth$checkBastion(ServerPlayer player, CallbackInfo callback) {
        SuperGlueSelectionPacket packet = (SuperGlueSelectionPacket) (Object) this;
        if (BastionCreateProtection.denyGlueSelection(player, packet.from(), packet.to())) callback.cancel();
    }
}
