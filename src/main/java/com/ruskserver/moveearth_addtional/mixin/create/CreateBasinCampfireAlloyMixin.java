package com.ruskserver.moveearth_addtional.mixin.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.ruskserver.moveearth_addtional.compat.create.CampfireAlloyPolicy;
import com.ruskserver.moveearth_addtional.compat.create.CampfireAlloying;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A lit campfire under a basin satisfies "heated" for campfire alloys
 * ({@link CampfireAlloying}). Create's own verdict is kept for everything else,
 * so other heated recipes, superheated recipes and boilers are unchanged.
 */
@Mixin(value = BasinRecipe.class, remap = false)
public abstract class CreateBasinCampfireAlloyMixin {

    @WrapOperation(method = "apply(Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;Lnet/minecraft/world/item/crafting/Recipe;Z)Z",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/processing/recipe/HeatCondition;testBlazeBurner(Lcom/simibubi/create/content/processing/burner/BlazeBurnerBlock$HeatLevel;)Z"))
    private static boolean moveearth$campfireAlloy(HeatCondition condition, BlazeBurnerBlock.HeatLevel heat,
                                                   Operation<Boolean> original,
                                                   @Local(argsOnly = true) BasinBlockEntity basin,
                                                   @Local(argsOnly = true) Recipe<?> recipe) {
        boolean createAllows = original.call(condition, heat);
        if (createAllows || condition != HeatCondition.HEATED) return createAllows;
        return CampfireAlloyPolicy.heatMet(true, false,
                CampfireAlloying.campfireLitUnder(basin), CampfireAlloying.makesCampfireAlloy(basin, recipe));
    }
}
