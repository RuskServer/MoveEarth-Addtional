package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.jobs.GunSmithJobService;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Attributes successful TaCZ gunsmith-table crafts to the player. */
@Mixin(targets = "com.tacz.guns.inventory.GunSmithTableMenu", remap = false)
public abstract class GunSmithTableMenuMixin {
    @Inject(method = "lambda$doCraft$3", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"), remap = false)
    private void moveearth$onCraft(net.minecraft.world.entity.player.Player player, GunSmithTableRecipe recipe,
                                   net.neoforged.neoforge.items.IItemHandler inventory, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ItemStack result = recipe.getResultItem(serverPlayer.registryAccess());
        GunSmithJobService.awardCraft(serverPlayer, result);
    }
}
