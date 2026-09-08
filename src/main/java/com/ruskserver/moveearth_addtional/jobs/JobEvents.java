package com.ruskserver.moveearth_addtional.jobs;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.pvp.PvpMatchManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-only action detection for job rewards. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class JobEvents {
    private static final String NO_HUNTER_XP_TAG = "moveearth_jobs_no_hunter_xp";
    private static final long HARVEST_VERIFICATION_TICKS = 2L;
    private static final Map<PendingHarvestKey, PendingHarvest> PENDING_HARVESTS = new HashMap<>();

    private JobEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JobProgressSavedData data = JobProgressSavedData.get(player.getServer());
            data.rememberName(player.getUUID(), player.getGameProfile().getName());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        JobProgressBossBar.remove(playerId);
        PENDING_HARVESTS.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        JobProgressBossBar.tick();
        GunDisassemblyAttribution.purge(event.getServer());
        verifyPendingHarvests(event.getServer());
    }

    /**
     * Some crop mods harvest a mature crop by right-clicking it and reset the same block to a younger
     * age instead of breaking it. Record the mature state before interaction, then award only after the
     * server has actually changed that same block to a non-rewarding state. This also prevents an empty
     * or cancelled interaction from granting XP.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockRightClicked(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)
                || player.isCreative() || player.isSpectator()
                || level.dimension().equals(PvpMatchManager.ARENA)) {
            return;
        }

        BlockState state = level.getBlockState(event.getPos());
        List<PendingJobReward> rewards = JobDefinitions.INSTANCE.all().stream()
                .map(definition -> new PendingJobReward(definition, definition.blockBreakXp(state)))
                .filter(reward -> reward.xp() > 0)
                .toList();
        if (rewards.isEmpty()) {
            return;
        }

        PendingHarvestKey key = new PendingHarvestKey(player.getUUID(), level.dimension(), event.getPos().immutable());
        PENDING_HARVESTS.put(key, new PendingHarvest(state.getBlock(), level.getGameTime(), rewards));
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
        GunDisassemblyAttribution.clear();
        JobProgressBossBar.clear();
        PENDING_HARVESTS.clear();
    }

    private static void verifyPendingHarvests(MinecraftServer server) {
        Iterator<Map.Entry<PendingHarvestKey, PendingHarvest>> iterator = PENDING_HARVESTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<PendingHarvestKey, PendingHarvest> entry = iterator.next();
            PendingHarvestKey key = entry.getKey();
            PendingHarvest pending = entry.getValue();
            ServerLevel level = server.getLevel(key.dimension());
            ServerPlayer player = server.getPlayerList().getPlayer(key.playerId());
            if (level == null || player == null || player.isCreative() || player.isSpectator()
                    || level.dimension().equals(PvpMatchManager.ARENA)) {
                iterator.remove();
                continue;
            }

            BlockState current = level.getBlockState(key.pos());
            if (current.getBlock() != pending.block()) {
                iterator.remove();
                continue;
            }

            boolean harvested = pending.rewards().stream()
                    .noneMatch(reward -> reward.definition().blockBreakXp(current) > 0);
            if (harvested) {
                for (PendingJobReward reward : pending.rewards()) {
                    JobService.INSTANCE.awardAction(player, reward.definition(), reward.xp());
                }
                iterator.remove();
            } else if (level.getGameTime() - pending.gameTime() >= HARVEST_VERIFICATION_TICKS) {
                iterator.remove();
            }
        }
    }

    private record PendingHarvestKey(UUID playerId, ResourceKey<Level> dimension, BlockPos pos) {
    }

    private record PendingHarvest(Block block, long gameTime, List<PendingJobReward> rewards) {
    }

    private record PendingJobReward(JobDefinition definition, double xp) {
    }
}
