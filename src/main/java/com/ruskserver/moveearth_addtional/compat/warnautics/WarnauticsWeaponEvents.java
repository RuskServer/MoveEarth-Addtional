package com.ruskserver.moveearth_addtional.compat.warnautics;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server policy for disabled cruise missiles and attributable Warnautics aerial bombs. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class WarnauticsWeaponEvents {
    static final String ATTRIBUTION_NATION = "moveearth_warnautics_nation";
    static final String ATTRIBUTION_ACTOR = "moveearth_warnautics_actor";
    private static final String MOD_ID = "cbc_more_content";
    private static final String CRUISE_MISSILE = "cruise_missile";
    private static final ResourceLocation CRUISE_RECIPE =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, CRUISE_MISSILE);
    private static final Set<String> AERIAL_BOMBS = Set.of(
            "small_bomb", "sea_bomb", "medium_bomb", "large_bomb", "moab");

    private WarnauticsWeaponEvents() { }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        removeCruiseMissileRecipe(event.getServer().getRecipeManager());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            removeCruiseMissileRecipe(event.getPlayerList().getServer().getRecipeManager());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCruiseMissilePlaced(BlockEvent.EntityPlaceEvent event) {
        if (!isRegistryPath(event.getPlacedBlock(), CRUISE_MISSILE)) return;
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player) notifyCruiseDisabled(player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBombPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || event instanceof BlockEvent.EntityMultiPlaceEvent
                || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)) return;
        String path = blockPath(event.getPlacedBlock());
        if (!AERIAL_BOMBS.contains(path)) return;
        UUID nationId = NationSavedData.get(level.getServer()).nationIdFor(player.getUUID()).orElse(null);
        if (nationId != null) {
            WarnauticsBombSavedData.get(level).put(
                    event.getPos(), path, player.getUUID(), nationId,
                    WarnauticsSableBombCompat.subLevelId(level, event.getPos()), level.getGameTime());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBombBroken(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !AERIAL_BOMBS.contains(blockPath(event.getState()))) return;
        WarnauticsBombSavedData.get(level).remove(event.getPos());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!isRegistryPath(event.getItemStack(), CRUISE_MISSILE)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        if (event.getEntity() instanceof ServerPlayer player) notifyCruiseDisabled(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        boolean cruiseItem = isRegistryPath(event.getItemStack(), CRUISE_MISSILE);
        boolean cruiseBlock = isRegistryPath(event.getLevel().getBlockState(event.getPos()), CRUISE_MISSILE);
        if (!cruiseItem && !cruiseBlock) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        if (event.getEntity() instanceof ServerPlayer player) notifyCruiseDisabled(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity entity = event.getEntity();
        String path = entityPath(entity);
        if (CRUISE_MISSILE.equals(path)) {
            event.setCanceled(true);
            entity.discard();
            Moveearth_addtional.LOGGER.warn("Blocked a disabled Create Warnautics cruise missile entity at {}",
                    entity.blockPosition());
            return;
        }
        if (!AERIAL_BOMBS.contains(path)) return;
        CompoundTag persistent = entity.getPersistentData();
        if (persistent.hasUUID(ATTRIBUTION_NATION)) return;
        WarnauticsBombSavedData.Placement placement = WarnauticsBombSavedData.get(level)
                .claimNearest(level, entity.position(), path, level.getGameTime());
        if (placement == null) return;
        persistent.putUUID(ATTRIBUTION_NATION, placement.nationId());
        persistent.putUUID(ATTRIBUTION_ACTOR, placement.placerId());
    }

    static boolean isBombBlock(BlockState state, String expectedPath) {
        return expectedPath.equals(blockPath(state));
    }

    static String blockPath(BlockState state) {
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && MOD_ID.equals(id.getNamespace()) ? id.getPath() : "";
    }

    static String entityPath(Entity entity) {
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id != null && MOD_ID.equals(id.getNamespace()) ? id.getPath() : "";
    }

    private static boolean isRegistryPath(BlockState state, String path) {
        return path.equals(blockPath(state));
    }

    private static boolean isRegistryPath(ItemStack stack, String path) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && MOD_ID.equals(id.getNamespace()) && path.equals(id.getPath());
    }

    private static void removeCruiseMissileRecipe(RecipeManager recipeManager) {
        Map<ResourceLocation, RecipeHolder<?>> recipes = new LinkedHashMap<>();
        boolean removed = false;
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            if (CRUISE_RECIPE.equals(holder.id())) {
                removed = true;
            } else {
                recipes.put(holder.id(), holder);
            }
        }
        if (removed) {
            recipeManager.replaceRecipes(recipes.values());
            Moveearth_addtional.LOGGER.info("Disabled Create Warnautics cruise missile recipe");
        }
    }

    private static void notifyCruiseDisabled(ServerPlayer player) {
        player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                "message.moveearth_addtional.warnautics.cruise_disabled")));
    }
}
