package com.ruskserver.moveearth_addtional.mixin.client.compat;

import com.ruskserver.moveearth_addtional.client.pvp.PvpClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Plasmo Voice draws its own icon and volume label over every player, through walls unless they sneak, apart
 * from the name tag the PvP match hides. Over an enemy that is a marker on them wherever they are, so during a
 * match it is left off enemies; teammates keep theirs. Plasmo Voice is optional: without it this does nothing.
 */
@Pseudo
@Mixin(targets = "su.plo.voice.client.render.voice.EntityIconStateExtractor", remap = false)
public abstract class PlasmoVoiceEnemyIconMixin {
    @Inject(method = "extract", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void moveearth$hideEnemyIconsInPvp(LivingEntity entity, CallbackInfoReturnable<Object> cir) {
        if (!(entity instanceof Player player) || !PvpClientState.isMatchActive()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && player.getUUID().equals(minecraft.player.getUUID())) return;
        if (!PvpClientState.isAlly(player.getUUID())) cir.setReturnValue(null);
    }
}
