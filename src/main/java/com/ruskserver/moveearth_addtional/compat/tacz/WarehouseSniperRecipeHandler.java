package com.ruskserver.moveearth_addtional.compat.tacz;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.item.ModItems;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Adds one real TaCZ workbench input for loaded sniper-category guns. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class WarehouseSniperRecipeHandler {
    private WarehouseSniperRecipeHandler() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        modify(event.getServer().getRecipeManager());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) modify(event.getPlayerList().getServer().getRecipeManager());
    }

    private static void modify(RecipeManager manager) {
        Map<ResourceLocation, RecipeHolder<?>> changed = new LinkedHashMap<>();
        int gated = 0;
        int unknown = 0;
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            RecipeHolder<?> output = holder;
            if (holder.value() instanceof GunSmithTableRecipe recipe) {
                ItemStack result = recipe.getOutput();
                IGun gun = result == null || result.isEmpty() ? null : IGun.getIGunOrNull(result);
                ResourceLocation gunId = gun == null ? null : gun.getGunId(result);
                if (gunId != null) {
                    var index = TimelessAPI.getCommonGunIndex(gunId).orElse(null);
                    if (index == null || index.getType() == null) {
                        unknown++;
                    } else if (WarehouseSniperRecipePolicy.requiresAssembly(index.getType())) {
                        if (recipe.getInputs() == null) {
                            unknown++;
                            changed.put(holder.id(), holder);
                            continue;
                        }
                        boolean alreadyGated = recipe.getInputs().stream().anyMatch(input ->
                                input.getIngredient().test(new ItemStack(ModItems.PRECISION_FIRING_ASSEMBLY.get())));
                        if (!alreadyGated) {
                            var inputs = new ArrayList<>(recipe.getInputs());
                            inputs.add(new GunSmithTableIngredient(
                                    Ingredient.of(ModItems.PRECISION_FIRING_ASSEMBLY.get()), 1));
                            output = new RecipeHolder<>(holder.id(),
                                    new GunSmithTableRecipe(recipe.getResult(), inputs));
                            gated++;
                        }
                    }
                }
            }
            changed.put(output.id(), output);
        }
        if (gated > 0) manager.replaceRecipes(changed.values());
        Moveearth_addtional.LOGGER.info("Warehouse sniper gate: changed={} unresolvedGunRecipes={}", gated, unknown);
    }
}
