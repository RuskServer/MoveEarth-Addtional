package com.ruskserver.moveearth_addtional.mixin.paintings;

import com.ruskserver.moveearth_addtional.compat.paintings.PaintingUploadGate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.image.BufferedImage;

/**
 * Immersive Paintings: only a nation's citizens may upload, a few a day ({@link PaintingUploadGate}).
 *
 * <p>The static {@code handle(Player, BufferedImage, Painting)} is where every upload is registered, from the
 * painting screen and from the Xerca easel alike, after the mod's own checks. Refusing there sends the mod's own
 * error payload, so the painting screen shows the reason in its usual place.
 */
@Pseudo
@Mixin(targets = "net.conczin.immersive_paintings.network.payload.c2s.PaintingRegisterPayload", remap = false)
public abstract class PaintingUploadLimitMixin {
    private static final String HANDLE = "handle(Lnet/minecraft/world/entity/player/Player;Ljava/awt/image/BufferedImage;"
            + "Lnet/conczin/immersive_paintings/Painting;)Lnet/minecraft/resources/ResourceLocation;";

    @Shadow
    private static void paintingRegisterError(Player player, String error, ResourceLocation identifier) {
        throw new AssertionError();
    }

    @Inject(method = HANDLE, at = @At("HEAD"), cancellable = true, remap = false)
    private static void moveearth$gateUpload(Player player, BufferedImage image, @Coerce Object painting,
                                             CallbackInfoReturnable<ResourceLocation> cir) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        String refusal = PaintingUploadGate.refusal(serverPlayer);
        if (refusal == null) return;
        paintingRegisterError(player, refusal, null);
        cir.setReturnValue(null);
    }

    @Inject(method = HANDLE, at = @At("RETURN"), remap = false)
    private static void moveearth$countUpload(Player player, BufferedImage image, @Coerce Object painting,
                                              CallbackInfoReturnable<ResourceLocation> cir) {
        if (cir.getReturnValue() != null && player instanceof ServerPlayer serverPlayer) {
            PaintingUploadGate.uploaded(serverPlayer);
        }
    }
}
