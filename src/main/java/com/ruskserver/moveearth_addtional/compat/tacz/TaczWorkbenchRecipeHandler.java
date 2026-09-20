package com.ruskserver.moveearth_addtional.compat.tacz;

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
import java.util.Set;
import java.util.stream.Collectors;

/** Replaces TaCZ's inexpensive workbench recipes with industrialized MoveEarth recipes. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class TaczWorkbenchRecipeHandler {
    private TaczWorkbenchRecipeHandler() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        replaceWorkbenchRecipes(event.getServer().getRecipeManager());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            replaceWorkbenchRecipes(event.getPlayerList().getServer().getRecipeManager());
        }
    }

    private static void replaceWorkbenchRecipes(RecipeManager recipeManager) {
        Set<String> loadedReplacementPaths = recipeManager.getRecipes().stream()
                .map(RecipeHolder::id)
                .filter(id -> Moveearth_addtional.MODID.equals(id.getNamespace()))
                .map(ResourceLocation::getPath)
                .collect(Collectors.toUnmodifiableSet());

        Map<ResourceLocation, RecipeHolder<?>> recipes = new LinkedHashMap<>();
        int removed = 0;
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            ResourceLocation id = holder.id();
            if (TaczWorkbenchRecipePolicy.isLegacyRecipe(id.getNamespace(), id.getPath())
                    && TaczWorkbenchRecipePolicy.isReplacementRecipe(
                    id.getPath(), Moveearth_addtional.MODID, id.getPath())
                    && loadedReplacementPaths.contains(id.getPath())) {
                removed++;
                continue;
            }
            recipes.put(id, holder);
        }
        if (removed == 0) {
            return;
        }
        recipeManager.replaceRecipes(recipes.values());
        Moveearth_addtional.LOGGER.info(
                "Replaced {} TaCZ workbench recipes with MoveEarth industrial recipes", removed);
    }
}
