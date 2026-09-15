package com.ruskserver.moveearth_addtional.handler;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/** Server-authoritative ban on Ender Chest access, placement and crafting. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class EnderChestRestrictionHandler {
    private EnderChestRestrictionHandler() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        removeRecipe(event.getServer().getRecipeManager());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            removeRecipe(event.getPlayerList().getServer().getRecipeManager());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || (!isRestricted(event.getLevel().getBlockState(event.getPos()))
                && !isRestricted(event.getItemStack()))) return;
        deny(event, player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemUse(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !isRestricted(event.getItemStack())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        notifyDisabled(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!isRestricted(event.getPlacedBlock())) return;
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player) notifyDisabled(player);
    }

    static boolean isRestricted(BlockState state) {
        return state.is(Blocks.ENDER_CHEST);
    }

    static boolean isRestricted(ItemStack stack) {
        return stack.is(Items.ENDER_CHEST);
    }

    static boolean isRestrictedRecipe(ResourceLocation id) {
        return EnderChestRestrictionPolicy.isRestrictedId(id.getNamespace(), id.getPath());
    }

    private static void deny(PlayerInteractEvent.RightClickBlock event, ServerPlayer player) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        notifyDisabled(player);
    }

    private static void removeRecipe(RecipeManager recipeManager) {
        Map<ResourceLocation, RecipeHolder<?>> recipes = new LinkedHashMap<>();
        boolean removed = false;
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            if (isRestrictedRecipe(holder.id())) {
                removed = true;
            } else {
                recipes.put(holder.id(), holder);
            }
        }
        if (!removed) return;
        recipeManager.replaceRecipes(recipes.values());
        Moveearth_addtional.LOGGER.info("[MoveEarth] Disabled the Ender Chest crafting recipe");
    }

    private static void notifyDisabled(ServerPlayer player) {
        boolean notify = !player.getCooldowns().isOnCooldown(Items.ENDER_CHEST);
        player.getCooldowns().addCooldown(Items.ENDER_CHEST, 20);
        if (notify) {
            player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                    "message.moveearth_addtional.ender_chest_disabled")));
        }
    }
}
