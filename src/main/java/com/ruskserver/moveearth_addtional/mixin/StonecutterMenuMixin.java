package com.ruskserver.moveearth_addtional.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Integrated and adapted from the standalone compatibility patch
 * {@code moveearth_patch_unti-1.0-SNAPSHOT.jar}.
 * The original artifact did not provide an author name; provenance details are
 * retained in {@code META-INF/NOTICE-moveearth_patch_unti.txt}.
 */
@Mixin(StonecutterMenu.class)
public abstract class StonecutterMenuMixin {
    @Shadow
    private List<RecipeHolder<StonecutterRecipe>> recipes;

    @Inject(method = "setupRecipeList", at = @At("HEAD"))
    private void moveearthAdditional$resetRecipeList(Container container, ItemStack input, CallbackInfo callbackInfo) {
        // Some mods replace StonecutterMenu's recipe list with an immutable list.
        // Vanilla immediately clears the previous list here, which then throws and
        // crashes the server. Replacing it preserves the intended clear semantics.
        this.recipes = new ArrayList<>();
    }
}
