package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.jobs.GunSmithJobService;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Attributes successful TaCZ gunsmith-table crafts to the player. */
@Mixin(targets = "com.tacz.guns.inventory.GunSmithTableMenu", remap = false)
public abstract class GunSmithTableMenuMixin {
    @Inject(method = "doCraft", at = @At("HEAD"), remap = false)
    private void moveearth$onCraft(ResourceLocation recipeId, net.minecraft.world.entity.player.Player player,
                                   CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        RecipeHolder<?> holder = serverPlayer.level().getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null || !(holder.value() instanceof GunSmithTableRecipe recipe)) return;
        IItemHandler inventory = serverPlayer.getCapability(Capabilities.ItemHandler.ENTITY, null);
        if (inventory == null || !hasInputs(inventory, recipe.getInputs())) return;
        ItemStack result = recipe.getResultItem(serverPlayer.registryAccess());
        GunSmithJobService.awardCraft(serverPlayer, result);
    }

    private static boolean hasInputs(IItemHandler inventory, java.util.List<GunSmithTableIngredient> inputs) {
        int[] remaining = new int[inventory.getSlots()];
        for (int slot = 0; slot < remaining.length; slot++) remaining[slot] = inventory.getStackInSlot(slot).getCount();
        for (GunSmithTableIngredient input : inputs) {
            int needed = input.getCount();
            for (int slot = 0; slot < remaining.length && needed > 0; slot++) {
                if (remaining[slot] > 0 && input.getIngredient().test(inventory.getStackInSlot(slot))) {
                    int used = Math.min(remaining[slot], needed);
                    remaining[slot] -= used;
                    needed -= used;
                }
            }
            if (needed > 0) return false;
        }
        return true;
    }
}
