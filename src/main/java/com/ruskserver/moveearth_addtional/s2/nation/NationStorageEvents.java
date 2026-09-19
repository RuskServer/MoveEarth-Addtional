package com.ruskserver.moveearth_addtional.s2.nation;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Keeps persistent storage inside the owning nation's effective territory. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class NationStorageEvents {
    public static final TagKey<Block> STORAGE_BLOCKS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "nation_storage_blocks"));
    public static final TagKey<Item> STORAGE_ITEMS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "nation_storage_items"));
    public static final TagKey<EntityType<?>> STORAGE_ENTITIES = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "nation_storage_entity_types"));
    private static final Map<UUID, Long> LAST_NOTICE = new HashMap<>();
    private static final Map<UUID, Boolean> OPEN_ENEMY_STORAGE = new HashMap<>();

    private NationStorageEvents() { }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getLevel().getBlockState(event.getPos()).is(STORAGE_BLOCKS)
                && !event.getItemStack().is(STORAGE_ITEMS)) return;
        if (canUse(player, event.getPos())) {
            if (!canPlace(player, event.getPos())) OPEN_ENEMY_STORAGE.put(player.getUUID(), true);
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        notify(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !event.getItemStack().is(STORAGE_ITEMS)
                || canPlace(player, player.blockPosition())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        notify(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !event.getTarget().getType().is(STORAGE_ENTITIES)
                || canUse(player, event.getTarget().blockPosition())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        notify(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseEntitySpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !event.getTarget().getType().is(STORAGE_ENTITIES)
                || canUse(player, event.getTarget().blockPosition())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        notify(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !event.getPlacedBlock().is(STORAGE_BLOCKS)
                ) return;
        if (!canPlace(player, event.getPos())) {
            event.setCanceled(true);
            notify(player);
            return;
        }
        UUID nation = NationSavedData.get(player.server).nationIdFor(player.getUUID()).orElse(null);
        try {
            var vehicle = com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology
                    .at(player.serverLevel(), event.getPos()).orElse(null);
            if (vehicle != null) nation = vehicle.vehicle().nationId();
        } catch (RuntimeException | LinkageError ignored) { }
        NationStorageOwnershipSavedData.get(player.server).put(player.level().dimension().location(),
                event.getPos(), nation);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !event.getState().is(STORAGE_BLOCKS)) return;
        if (!canUse(player, event.getPos())) {
            event.setCanceled(true);
            notify(player);
            return;
        }
        NationStorageOwnershipSavedData.get(player.server).remove(player.level().dimension().location(), event.getPos());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_NOTICE.clear();
        OPEN_ENEMY_STORAGE.clear();
    }

    /** Also closes an already-open storage menu immediately after a leave, kick or disband. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerTick(ServerTickEvent.Post event) {
        if ((event.getServer().getTickCount() & 3) != 0) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            boolean stillMember = NationSavedData.get(player.server).nationIdFor(player.getUUID()).isPresent();
            if (player.containerMenu == player.inventoryMenu || player.hasPermissions(2)) {
                OPEN_ENEMY_STORAGE.remove(player.getUUID());
                continue;
            }
            ResourceLocation id;
            try {
                id = BuiltInRegistries.MENU.getKey(player.containerMenu.getType());
            } catch (RuntimeException ignored) {
                continue;
            }
            if (id != null && NationStoragePolicy.isRestrictedMenuId(id.getNamespace(), id.getPath())) {
                boolean enemySession = OPEN_ENEMY_STORAGE.containsKey(player.getUUID());
                if (!stillMember || enemySession && !canUse(player, player.blockPosition())) {
                    player.closeContainer();
                    OPEN_ENEMY_STORAGE.remove(player.getUUID());
                    notify(player);
                }
            }
        }
    }

    public static boolean canUse(ServerPlayer player, BlockPos pos) {
        if (player.hasPermissions(2)) return true;
        UUID nationId = NationSavedData.get(player.server).nationIdFor(player.getUUID()).orElse(null);
        UUID explicitOwner = NationStorageOwnershipSavedData.get(player.server)
                .owner(player.level().dimension().location(), pos);
        if (explicitOwner != null) {
            var loot = com.ruskserver.moveearth_addtional.s2.siege.SiegeLootService.access(player, pos);
            if (explicitOwner.equals(nationId) && (canPlace(player, pos)
                    || explicitOwner.equals(loot.formerOwner()))) return true;
            if (com.ruskserver.moveearth_addtional.s2.vehicle.VehicleLootSavedData.get(player.server)
                    .canLoot(player, player.serverLevel(), pos)) return true;
            return loot.allowed();
        }
        var loot = com.ruskserver.moveearth_addtional.s2.siege.SiegeLootService.access(player, pos);
        if (loot.formerOwner() != null) return loot.allowed() || loot.formerOwner().equals(nationId);
        return canPlace(player, pos)
                || com.ruskserver.moveearth_addtional.s2.vehicle.VehicleLootSavedData.get(player.server)
                .canLoot(player, player.serverLevel(), pos);
    }

    public static boolean automationRestricted(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        if (com.ruskserver.moveearth_addtional.s2.siege.SiegeLootService.isLootRestrictedPosition(
                level.getServer(), level.dimension().location(), pos)) return true;
        try {
            var vehicle = com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology
                    .at(level, pos).orElse(null);
            return vehicle != null && vehicle.vehicle().health() <= 0;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean canPlace(ServerPlayer player, BlockPos pos) {
        UUID nationId = NationSavedData.get(player.server).nationIdFor(player.getUUID()).orElse(null);
        if (player.hasPermissions(2)) return true;
        try {
            var vehicle = com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology
                    .at(player.serverLevel(), pos).orElse(null);
            if (vehicle != null) return vehicle.vehicle().nationId().equals(nationId);
        } catch (LinkageError unavailable) {
            // Optional Sable is absent; ordinary territorial storage rules still apply.
        }
        boolean ownTerritory = nationId != null && TerritorySavedData.get(player.server).allowsStorage(
                player.server, nationId, player.level().dimension().location(), pos);
        return NationStoragePolicy.canUseStorage(nationId != null, ownTerritory, player.hasPermissions(2));
    }

    private static void notify(ServerPlayer player) {
        com.ruskserver.moveearth_addtional.s2.technology.NationTechnologySavedData.get(player.server)
                .recordAction(player, "storage_rules_viewed");
        long now = player.level().getGameTime();
        long previous = LAST_NOTICE.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
        if (now - previous < 20L) return;
        LAST_NOTICE.put(player.getUUID(), now);
        boolean member = NationSavedData.get(player.server).nationIdFor(player.getUUID()).isPresent();
        player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(member
                ? "message.moveearth_addtional.nation_storage.requires_territory"
                : "message.moveearth_addtional.nation_storage.requires_nation")));
    }
}
