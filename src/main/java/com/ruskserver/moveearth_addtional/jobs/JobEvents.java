package com.ruskserver.moveearth_addtional.jobs;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.pvp.PvpMatchManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Server-only action detection for job rewards. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class JobEvents {
    private static final String NO_HUNTER_XP_TAG = "moveearth_jobs_no_hunter_xp";

    private JobEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = event.getPlacedBlock();
        if (JobDefinitions.INSTANCE.tracksPlacement(state)) {
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
            double reward = definition.blockBreakXp(state);
            if (reward > 0) {
                JobService.INSTANCE.awardBlockBreak(player, definition, reward);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityKilled(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || player.isCreative() || player.isSpectator()
                || player.level().dimension().equals(PvpMatchManager.ARENA)
                || event.getEntity().getPersistentData().getBoolean(NO_HUNTER_XP_TAG)) {
            return;
        }
        for (JobDefinition definition : JobDefinitions.INSTANCE.all()) {
            double reward = definition.entityKillXp(event.getEntity().getType());
            if (reward > 0) {
                JobService.INSTANCE.awardAction(player, definition, reward);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAnimalBred(BabyEntitySpawnEvent event) {
        if (event.isCanceled() || event.getChild() == null
                || !(event.getCausedByPlayer() instanceof ServerPlayer player)
                || player.isCreative() || player.isSpectator()
                || player.level().dimension().equals(PvpMatchManager.ARENA)) {
            return;
        }
        for (JobDefinition definition : JobDefinitions.INSTANCE.all()) {
            double reward = definition.entityBreedXp(event.getParentA().getType());
            if (reward > 0) {
                JobService.INSTANCE.awardAction(player, definition, reward);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getCrafting().isEmpty()
                || player.isCreative() || player.isSpectator()
                || player.level().dimension().equals(PvpMatchManager.ARENA)) {
            return;
        }
        for (JobDefinition definition : JobDefinitions.INSTANCE.all()) {
            double reward = definition.itemCraftXp(event.getCrafting());
            if (reward > 0) {
                // Reward per completed crafting action, not per output stack size. This avoids recipes
                // producing many items at once multiplying XP unexpectedly.
                JobService.INSTANCE.awardAction(player, definition, reward);
            }
        }
    }

    @SubscribeEvent
    public static void onMobSpawned(FinalizeSpawnEvent event) {
        MobSpawnType spawnType = event.getSpawnType();
        if (MobSpawnType.isSpawner(spawnType) || spawnType == MobSpawnType.SPAWN_EGG
                || spawnType == MobSpawnType.COMMAND || spawnType == MobSpawnType.DISPENSER) {
            event.getEntity().getPersistentData().putBoolean(NO_HUNTER_XP_TAG, true);
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
