package com.ruskserver.moveearth_addtional.compat.mekanism;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Applies MoveEarth's curated Mekanism recipe surface on start and datapack reload. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class MekanismRecipeHandler {
    private MekanismRecipeHandler() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        applyPolicy(event.getServer().getRecipeManager());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            applyPolicy(event.getPlayerList().getServer().getRecipeManager());
        }
    }

    private static void applyPolicy(RecipeManager recipeManager) {
        if (!ModList.get().isLoaded(MekanismRecipePolicy.CORE_NAMESPACE)
                && !ModList.get().isLoaded(MekanismRecipePolicy.GENERATORS_NAMESPACE)) {
            return;
        }

        Map<ResourceLocation, RecipeHolder<?>> retained = new LinkedHashMap<>();
        Map<MekanismRecipePolicy.RemovalCategory, Integer> removed =
                new EnumMap<>(MekanismRecipePolicy.RemovalCategory.class);
        Set<String> loadedReplacementPaths = recipeManager.getRecipes().stream()
                .map(RecipeHolder::id)
                .filter(id -> MekanismProgressionRecipePolicy.MOVE_EARTH_NAMESPACE.equals(id.getNamespace()))
                .map(ResourceLocation::getPath)
                .filter(MekanismProgressionRecipePolicy.replacementPaths()::contains)
                .collect(Collectors.toUnmodifiableSet());
        int replacedProgressionRecipes = 0;

        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            ResourceLocation id = holder.id();
            MekanismRecipePolicy.RemovalCategory category = MekanismRecipePolicy.classify(
                    id.getNamespace(), id.getPath());
            if (category == MekanismRecipePolicy.RemovalCategory.NONE) {
                if (MekanismProgressionRecipePolicy.isLegacyRecipe(id.getNamespace(), id.getPath())
                        && MekanismProgressionRecipePolicy.hasLoadedReplacement(
                        id.getPath(), loadedReplacementPaths)) {
                    replacedProgressionRecipes++;
                } else {
                    retained.put(id, holder);
                }
            } else {
                removed.merge(category, 1, Integer::sum);
            }
        }

        if (removed.isEmpty() && replacedProgressionRecipes == 0) {
            Moveearth_addtional.LOGGER.warn(
                    "Mekanism integration is active, but no curated recipes were found to remove; "
                            + "verify the pinned Mekanism recipe ids");
            return;
        }

        recipeManager.replaceRecipes(retained.values());
        Moveearth_addtional.LOGGER.info(
                "Applied MoveEarth Mekanism recipe policy: removed={}, progressionReplacements={}",
                removed, replacedProgressionRecipes);
    }
}
