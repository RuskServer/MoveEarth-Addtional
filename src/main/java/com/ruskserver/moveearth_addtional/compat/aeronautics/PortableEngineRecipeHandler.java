package com.ruskserver.moveearth_addtional.compat.aeronautics;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/** Replaces Simulated's ordinary crafting recipe with MoveEarth's mechanical one. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class PortableEngineRecipeHandler {
    private PortableEngineRecipeHandler() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        removeLegacyRecipe(event.getServer().getRecipeManager());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            removeLegacyRecipe(event.getPlayerList().getServer().getRecipeManager());
        }
    }

    private static void removeLegacyRecipe(RecipeManager recipeManager) {
        boolean replacementLoaded = recipeManager.getRecipes().stream()
                .map(RecipeHolder::id)
                .anyMatch(PortableEngineRecipeHandler::isReplacementRecipe);
        if (!replacementLoaded) {
            Moveearth_addtional.LOGGER.warn(
                    "MoveEarth portable-engine mechanical recipe was not loaded; preserving Simulated's original recipe");
            return;
        }

        Map<ResourceLocation, RecipeHolder<?>> recipes = new LinkedHashMap<>();
        boolean removed = false;
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            if (isLegacyRecipe(holder.id())) {
                removed = true;
            } else {
                recipes.put(holder.id(), holder);
            }
        }
        if (!removed) {
            return;
        }
        recipeManager.replaceRecipes(recipes.values());
        Moveearth_addtional.LOGGER.info(
                "Replaced Create: Simulated portable-engine recipe with MoveEarth mechanical crafting");
    }

    static boolean isLegacyRecipe(ResourceLocation id) {
        return PortableEngineRecipePolicy.isLegacyRecipe(id.getNamespace(), id.getPath());
    }

    static boolean isReplacementRecipe(ResourceLocation id) {
        return PortableEngineRecipePolicy.isReplacementRecipe(id.getNamespace(), id.getPath());
    }
}
