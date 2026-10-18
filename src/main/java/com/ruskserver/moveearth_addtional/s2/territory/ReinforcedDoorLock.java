package com.ruskserver.moveearth_addtional.s2.territory;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementEntry;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * A reinforced door, trapdoor or fence gate in a nation's land opens only for those Bastion lets build there.
 * The territory closure already counts a reinforced door as wall even when open, so without this an enemy
 * could walk through what the core's exposure check treats as solid. The lock goes with the reinforcement:
 * once it is broken, switched off for unpaid upkeep, or disabled by a siege, anyone can open the door again.
 * Redstone still works, so a button left outside opens it for anyone.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ReinforcedDoorLock {
    private ReinforcedDoorLock() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        boolean holding = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
        if (!ReinforcedDoorLockPolicy.opensBlock(lockable(state), player.isSecondaryUseActive(), holding)) return;
        BlockPos otherHalf = otherHalf(state, pos);
        ReinforcementSavedData reinforcements = ReinforcementSavedData.get(level);
        SiegeSavedData sieges = SiegeSavedData.get(level.getServer());
        if (!ReinforcedDoorLockPolicy.locked(reinforced(level, reinforcements, sieges, pos),
                otherHalf != null && reinforced(level, reinforcements, sieges, otherHalf))) return;
        if (!BastionService.isRestricted(player, level, pos)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        // The client opens the door before the server answers; the clicked block is resent anyway, the other half is not.
        if (otherHalf != null) player.connection.send(new ClientboundBlockUpdatePacket(level, otherHalf));
        if (event.getHand() == InteractionHand.MAIN_HAND) BastionService.deny(player, BastionService.Action.DOOR);
    }

    /** What a hand opens: iron doors and trapdoors take redstone, so a click on one uses the held item. */
    private static boolean lockable(BlockState state) {
        if (state.getBlock() instanceof DoorBlock door) return door.type().canOpenByHand();
        if (state.getBlock() instanceof TrapDoorBlock) return !state.is(net.minecraft.world.level.block.Blocks.IRON_TRAPDOOR);
        return state.getBlock() instanceof FenceGateBlock;
    }

    private static BlockPos otherHalf(BlockState state, BlockPos pos) {
        if (!(state.getBlock() instanceof DoorBlock)) return null;
        return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
    }

    private static boolean reinforced(ServerLevel level, ReinforcementSavedData data, SiegeSavedData sieges,
                                      BlockPos pos) {
        if (sieges.isReinforcementDisabled(level.dimension().location(), pos)) return false;
        ReinforcementEntry entry = data.get(pos).orElse(null);
        return entry != null && entry.enabled() && entry.durability() > 0;
    }
}
