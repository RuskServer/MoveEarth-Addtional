package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.compat.create.MinecartContraptionRules;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import com.simibubi.create.content.contraptions.mounted.MinecartContraptionItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents freight vehicles from bypassing risk by becoming inventory items. */
@Mixin(value = MinecartContraptionItem.class, remap = false)
public abstract class MinecartContraptionItemMixin {
    @Inject(method = "wrenchCanBeUsedToPickUpMinecartContraptions", at = @At("HEAD"),
            cancellable = true, remap = false)
    private static void moveearth$disablePickup(PlayerInteractEvent.EntityInteract event,
                                                CallbackInfo callback) {
        var heldId = BuiltInRegistries.ITEM.getKey(event.getEntity().getItemInHand(event.getHand()).getItem());
        if (!("create".equals(heldId.getNamespace()) && "wrench".equals(heldId.getPath()))
                || !MinecartContraptionRules.isMinecartContraption(event.getTarget())) return;
        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
        if (!event.getLevel().isClientSide) {
            event.getEntity().sendSystemMessage(MoveEarthMessage.warning(Component.translatable(
                    "message.moveearth_addtional.minecart.itemization_disabled")));
        }
        callback.cancel();
    }

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true, remap = false)
    private void moveearth$disableLegacyDeployment(UseOnContext context,
                                                   CallbackInfoReturnable<InteractionResult> callback) {
        if (!context.getLevel().isClientSide && context.getPlayer() != null) {
            context.getPlayer().sendSystemMessage(MoveEarthMessage.warning(Component.translatable(
                    "message.moveearth_addtional.minecart.itemization_disabled")));
        }
        callback.setReturnValue(InteractionResult.FAIL);
    }
}
