package com.ruskserver.moveearth_addtional.jobs;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.pvp.PvpMatchManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Server-only action detection for job rewards. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class JobEvents {
    private JobEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = event.getPlacedBlock();
        if (JobDefinitions.INSTANCE.rewardsBlock(state)) {
            PlacedJobBlockSavedData.get(level).mark(event.getPos().immutable());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)
                || player.isCreative() || player.isSpectator()
                || level.dimension().equals(PvpMatchManager.ARENA)) {
            return;
        }

        BlockState state = event.getState();
        if (!JobDefinitions.INSTANCE.rewardsBlock(state)) {
            return;
        }
        if (PlacedJobBlockSavedData.get(level).consume(event.getPos())) {
            return;
        }
        if (state.requiresCorrectToolForDrops() && !player.hasCorrectToolForDrops(state)) {
            return;
        }

        for (JobDefinition definition : JobDefinitions.INSTANCE.all()) {
            int reward = definition.blockBreakXp(state);
            if (reward > 0) {
                JobService.INSTANCE.awardBlockBreak(player, definition, reward);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPistonMove(PistonEvent.Pre event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver != null && resolver.resolve()) {
            PlacedJobBlockSavedData.get(level).moveAll(
                    resolver.getToPush(), resolver.getToDestroy(), resolver.getPushDirection());
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        PlacedJobBlockSavedData data = PlacedJobBlockSavedData.get(level);
        for (var position : event.getAffectedBlocks()) {
            data.consume(position);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        JobService.INSTANCE.clearTransientState();
    }
}
