package com.ruskserver.moveearth_addtional.mixin.paintings;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.compat.paintings.PaintingEditGuard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Immersive Paintings: an edit of a painting the player may not change ({@link PaintingEditGuard}) is dropped.
 * The click that opens the screen is refused already; this covers an edit sent without it. NeoForge runs the
 * mod's payload handlers on the server thread, so the land can be read here.
 */
@Pseudo
@Mixin(targets = "net.conczin.immersive_paintings.network.payload.c2s.PaintingEditPayload", remap = false)
public abstract class PaintingEditGuardMixin {
    @Shadow
    public abstract int entityId();

    @Inject(method = "handle(Lnet/minecraft/world/entity/player/Player;"
            + "Lnet/conczin/immersive_paintings/network/payload/ImmersivePayload$Runner;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void moveearth$guardEdit(Player player, @Coerce Object runner, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer) || !serverPlayer.server.isSameThread()) return;
        Entity painting = serverPlayer.level().getEntity(entityId());
        if (painting == null || PaintingEditGuard.mayEdit(serverPlayer, painting)) return;
        Moveearth_addtional.LOGGER.warn("{} tried to edit painting {} at {} without the land's leave",
                serverPlayer.getGameProfile().getName(), painting.getUUID(), painting.blockPosition().toShortString());
        ci.cancel();
    }
}
