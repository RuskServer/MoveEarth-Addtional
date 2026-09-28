package com.ruskserver.moveearth_addtional.compat.jei;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.compat.viewer.HiddenIngredientPolicy;
import com.ruskserver.moveearth_addtional.item.ModItems;
import com.ruskserver.moveearth_addtional.warehouse.WarehouseEncounterState;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Optional JEI adapter for MoveEarth's warehouse reward category, and for
 * hiding items the server has disabled ({@link HiddenIngredientPolicy}).
 */
@JeiPlugin
public final class MoveEarthJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
            Moveearth_addtional.MODID, "warehouse");

    @Override public ResourceLocation getPluginUid() { return UID; }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new WarehouseRewardCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // The drops themselves live in the warehouse category, which draws them
        // under the guard captain; this tab carries only the rules a picture of
        // the loot cannot state.
        registration.addRecipes(WarehouseRewardCategory.TYPE,
                List.of(WarehouseRewardCategory.display()));
        registration.addIngredientInfo(ModItems.PRECISION_FIRING_ASSEMBLY.get(),
                Component.literal("倉庫拠点の警備隊長を倒し、建物内の輸送コンテナから回収"),
                Component.literal("戦利品は共有され、先に回収した側が獲得"),
                Component.literal("再稼働までサーバー開放時間 "
                        + (WarehouseEncounterState.COOLDOWN_TICKS / 20 / 60 / 60) + "時間"));
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        IIngredientManager ingredients = runtime.getIngredientManager();
        List<ItemStack> hidden = ingredients.getAllItemStacks().stream()
                .filter(stack -> {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    return HiddenIngredientPolicy.isHidden(id.getNamespace(), id.getPath());
                })
                .toList();
        if (!hidden.isEmpty()) ingredients.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hidden);
    }
}
