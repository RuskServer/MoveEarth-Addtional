package com.ruskserver.moveearth_addtional.mixin.sentry;

import com.ruskserver.moveearth_addtional.compat.sentry.SentryTurretConfig;
import com.ruskserver.moveearth_addtional.compat.sentry.SentryTurretGuard;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import com.tacz.guns.api.item.IGun;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Refuses a gun a turret may not fire when a player tries to mount it, instead of
 * letting it sit in a turret that never shoots. This packet is the only way a
 * gun gets into a turret; {@link SentryTurretGuard} still checks every shot.
 */
@Pseudo
@Mixin(targets = "euphy.upo.sentrymechanicalarm.network.SentryInteractPacket", remap = false)
public abstract class SentryGunInsertMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private static void moveearth$refuseGun(@Coerce Object packet, ServerPlayer player, CallbackInfo ci) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof IGun) || SentryTurretGuard.allowed(held)) return;
        player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                "message.moveearth_addtional.sentry.gun_refused",
                trimmed(SentryTurretConfig.maxGunDamage()))));
        ci.cancel();
    }

    private static String trimmed(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
