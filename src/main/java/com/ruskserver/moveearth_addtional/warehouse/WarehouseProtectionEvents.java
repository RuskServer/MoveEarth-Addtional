package com.ruskserver.moveearth_addtional.warehouse;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

/** Protects registered warehouses without requiring WorldEdit after the initial paste. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class WarehouseProtectionEvents {
    private WarehouseProtectionEvents() { }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || event.getEntity() instanceof ServerPlayer player && player.hasPermissions(2)) return;
        WarehouseSites sites = WarehouseSites.get(level.getServer());
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            if (multi.getReplacedBlockSnapshots().stream().noneMatch(snapshot ->
                    sites.protects(level.dimension().location(), snapshot.getPos()))) return;
        } else if (!sites.protects(level.dimension().location(), event.getPos())) return;
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player) notifyDenied(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || event.getPlayer() instanceof ServerPlayer player && player.hasPermissions(2)
                || !WarehouseSites.get(level.getServer()).protects(level.dimension().location(), event.getPos())) {
            return;
        }
        event.setCanceled(true);
        if (event.getPlayer() instanceof ServerPlayer player) notifyDenied(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.hasPermissions(2)) return;
        WarehouseSites sites = WarehouseSites.get(player.server);
        var dimension = player.serverLevel().dimension().location();
        boolean inside = sites.protects(dimension, event.getPos());
        boolean bucketIntoSite = event.getItemStack().getItem() instanceof BucketItem
                && sites.protects(dimension, event.getPos().relative(event.getFace()));
        if (!inside && !bucketIntoSite) return;
        if (!bucketIntoSite && WarehouseEncounterService.openLoot(player, event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        // Only the building's own mechanical controls are public. Storage and the 64-block
        // buffer stay locked; a bucket must never be allowed through the control exception.
        var block = player.serverLevel().getBlockState(event.getPos()).getBlock();
        if (!bucketIntoSite && sites.insideStructure(dimension, event.getPos())
                && (block instanceof DoorBlock || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock || block instanceof ButtonBlock
                || block instanceof LeverBlock)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        notifyDenied(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        WarehouseSites sites = WarehouseSites.get(level.getServer());
        event.getAffectedBlocks().removeIf(pos -> sites.protects(level.dimension().location(), pos));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFluidChange(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && WarehouseSites.get(level.getServer()).protects(level.dimension().location(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        PistonStructureResolver movement = event.getStructureHelper();
        if (movement == null || !movement.resolve()) return;
        WarehouseSites sites = WarehouseSites.get(level.getServer());
        for (BlockPos pos : movement.getToPush()) {
            if (sites.protects(level.dimension().location(), pos)
                    || sites.protects(level.dimension().location(), pos.relative(movement.getPushDirection()))) {
                event.setCanceled(true);
                return;
            }
        }
        if (movement.getToDestroy().stream().anyMatch(pos ->
                sites.protects(level.dimension().location(), pos))) event.setCanceled(true);
    }

    private static void notifyDenied(ServerPlayer player) {
        player.displayClientMessage(MoveEarthMessage.warning("倉庫拠点の保護区域では改変できません"), true);
    }
}
