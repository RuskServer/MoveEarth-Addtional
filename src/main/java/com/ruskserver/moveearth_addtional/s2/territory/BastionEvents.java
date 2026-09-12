package com.ruskserver.moveearth_addtional.s2.territory;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.fluids.FluidUtil;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class BastionEvents {
    private BastionEvents() {
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;

        boolean restricted;
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multiPlace) {
            restricted = multiPlace.getReplacedBlockSnapshots().stream()
                    .anyMatch(snapshot -> BastionService.isRestricted(player, level, snapshot.getPos()));
        } else {
            restricted = BastionService.isRestricted(player, level, event.getPos());
        }
        if (!restricted) return;
        event.setCanceled(true);
        BastionService.deny(player, BastionService.Action.BLOCK_PLACE);
    }

    @SubscribeEvent
    public static void onUseItemOnBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        ItemStack held = event.getItemStack();
        BastionService.Action action;
        BlockPos target;

        if (held.getItem() instanceof BucketItem) {
            var fluid = FluidUtil.getFluidContained(held).filter(contents -> !contents.isEmpty()).orElse(null);
            if (fluid == null) return;
            BlockPos clicked = event.getPos();
            BlockState state = level.getBlockState(clicked);
            target = state.getBlock() instanceof LiquidBlockContainer container
                    && container.canPlaceLiquid(player, level, clicked, state, fluid.getFluid())
                    ? clicked : clicked.relative(event.getFace());
            action = BastionService.Action.FLUID_PLACE;
        } else if (held.getItem() instanceof BoatItem || held.getItem() instanceof MinecartItem) {
            target = event.getPos();
            action = BastionService.Action.VEHICLE_PLACE;
        } else {
            return;
        }

        if (!BastionService.isRestricted(player, level, target)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        BastionService.deny(player, action);
    }

    @SubscribeEvent
    public static void onEnderPearl(EntityTeleportEvent.EnderPearl event) {
        var player = event.getPlayer();
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        BlockPos target = BlockPos.containing(event.getTarget());
        if (!BastionService.isRestricted(player, level, target)) return;
        event.setCanceled(true);
        BastionService.deny(player, BastionService.Action.ENDER_PEARL);
    }

    @SubscribeEvent
    public static void onDismount(EntityMountEvent event) {
        if (!event.isDismounting()
                || !(event.getEntityMounting() instanceof net.minecraft.server.level.ServerPlayer player)
                || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        if (!BastionService.isRestricted(player, level, player.blockPosition())) return;
        BastionPlayerSavedData.get(player.server).requireReturn(player.getUUID());
        if (!player.isAlive() || player.isRemoved() || event.getEntityBeingMounted() == null
                || event.getEntityBeingMounted().isRemoved()) return;
        event.setCanceled(true);
        BastionService.deny(player, BastionService.Action.DISMOUNT);
    }

    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (event.isMounting()
                && event.getEntityMounting() instanceof net.minecraft.server.level.ServerPlayer player) {
            BastionPlayerRecovery.markMountedInRestrictedTerritory(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            BastionPlayerRecovery.tick(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            BastionPlayerRecovery.tick(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            BastionPlayerRecovery.tick(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player && player.isPassenger()) {
            BastionPlayerRecovery.markMountedInRestrictedTerritory(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        BastionService.clear();
    }
}
