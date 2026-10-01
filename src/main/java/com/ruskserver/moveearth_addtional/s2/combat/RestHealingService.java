package com.ruskserver.moveearth_addtional.s2.combat;

import com.ruskserver.moveearth_addtional.CompatEventHandler;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.ServerSchedule;
import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import com.ruskserver.moveearth_addtional.s2.siege.PrisonerService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Limited server-authoritative recovery near beds and lit normal campfires. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class RestHealingService {
    private static final float HEAL_STEP = 1.0F;
    private static final double MOVEMENT_EPSILON_SQR = 0.0025D;
    private static final int CAMPFIRE_SCAN_INTERVAL_TICKS = 20;
    private static final Map<UUID, RestState> STATES = new HashMap<>();

    private RestHealingService() { }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % 20L != 0L) return;
        if (!server.isDedicatedServer() || ServerSchedule.isOpenNow()) {
            RestHealingSavedData.get(server).advance(20L, S2TerritoryConfig.restHealingPeriodTicks());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        RestState state = STATES.computeIfAbsent(player.getUUID(), ignored -> new RestState(player));
        boolean moved = state.updatePosition(player);

        if (!canRecover(player)) {
            state.resetProgress();
            return;
        }

        boolean sleeping = player.isSleeping();
        boolean justWoke = state.wasSleeping && !sleeping;
        state.wasSleeping = sleeping;
        RestMode mode;
        if (sleeping) {
            player.getSleepingPos().ifPresent(pos -> state.recordBed(player.level().dimension(), pos));
            mode = RestMode.BED;
        } else if (state.canContinueBedRest(player, justWoke, moved,
                S2TerritoryConfig.bedRestStartTicks())) {
            mode = RestMode.BED;
        } else {
            if (moved || !player.onGround() || player.isPassenger()) {
                state.resetProgress();
                return;
            }
            // The scan is throttled per player and survives resetProgress(): a reset used to clear
            // the cache, so a player standing away from any campfire rescanned the cube every tick.
            int now = player.server.getTickCount();
            BlockPos here = player.blockPosition();
            if (state.campfireProbe.tryAcquire(now)) {
                state.nearCampfire = hasLitCampfire(player, S2TerritoryConfig.campfireRadius());
                state.campfireCheckedAt = here;
            }
            // A result from another block position is stale; wait for the next scan instead.
            if (!state.nearCampfire || !here.equals(state.campfireCheckedAt)) {
                state.resetProgress();
                return;
            }
            mode = RestMode.CAMPFIRE;
        }

        if (state.mode != mode) state.begin(mode);
        state.restTicks++;
        int startDelay = mode == RestMode.BED ? S2TerritoryConfig.bedRestStartTicks() : 0;
        int interval = mode == RestMode.BED
                ? S2TerritoryConfig.bedHealIntervalTicks()
                : S2TerritoryConfig.campfireHealIntervalTicks();
        if (state.restTicks > startDelay) state.healTicks++;

        RestHealingSavedData data = RestHealingSavedData.get(player.server);
        float maximum = S2TerritoryConfig.restHealingMaxHealth();
        float allowance = data.remaining(player.getUUID(), maximum);
        boolean hurt = player.getHealth() < player.getMaxHealth();
        if (hurt && player.tickCount % 20 == 0) showStatus(player, mode, allowance, maximum,
                Math.max(0, startDelay - state.restTicks));
        if (state.healTicks < interval) return;
        if (!hurt) {
            // Resting at full health still teaches the mechanic; the tutorial used to stall here.
            state.healTicks = 0;
            com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(player,
                    com.ruskserver.moveearth_addtional.advancement.ModCriteria.REST_HEALED);
            return;
        }
        if (allowance <= 0.0F) return;

        float amount = RestHealingPolicy.healAmount(player.getMaxHealth() - player.getHealth(), allowance, HEAL_STEP);
        state.healTicks = 0;
        if (amount <= 0.0F) return;
        amount = data.consume(player.getUUID(), amount, maximum);
        if (amount > 0.0F) {
            player.heal(amount);
            com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(player,
                    com.ruskserver.moveearth_addtional.advancement.ModCriteria.REST_HEALED);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) interrupt(player);
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer victim) interrupt(victim);
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) interrupt(attacker);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) STATES.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        STATES.clear();
    }

    private static boolean canRecover(ServerPlayer player) {
        return player.isAlive()
                && !player.isCreative()
                && !player.isSpectator()
                && !CombatTagService.isTagged(player)
                && !CompatEventHandler.isPlayerDown(player)
                && !PrisonerService.isMovementRestricted(player);
    }

    private static void interrupt(ServerPlayer player) {
        RestState state = STATES.get(player.getUUID());
        if (state != null) state.resetProgress();
    }

    /**
     * Same cube as before, but walked one chunk section at a time: a section whose palette
     * holds no campfire (nearly all of them) is skipped without reading a single block, so the
     * 33x33x33 cube of the maximum radius costs a few palette lookups.
     */
    private static boolean hasLitCampfire(ServerPlayer player, int radius) {
        Level level = player.level();
        BlockPos center = player.blockPosition();
        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - radius);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + radius);
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;
        if (minY > maxY) return false;
        for (int chunkX = SectionPos.blockToSectionCoord(minX); chunkX <= SectionPos.blockToSectionCoord(maxX); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(minZ); chunkZ <= SectionPos.blockToSectionCoord(maxZ); chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                for (int sectionY = SectionPos.blockToSectionCoord(minY);
                     sectionY <= SectionPos.blockToSectionCoord(maxY); sectionY++) {
                    int index = chunk.getSectionIndexFromSectionY(sectionY);
                    if (index < 0 || index >= chunk.getSectionsCount()) continue;
                    LevelChunkSection section = chunk.getSection(index);
                    if (section.hasOnlyAir() || !section.maybeHas(RestHealingService::isLitCampfire)) continue;
                    int x0 = Math.max(minX, chunkX << 4);
                    int x1 = Math.min(maxX, (chunkX << 4) + 15);
                    int y0 = Math.max(minY, sectionY << 4);
                    int y1 = Math.min(maxY, (sectionY << 4) + 15);
                    int z0 = Math.max(minZ, chunkZ << 4);
                    int z1 = Math.min(maxZ, (chunkZ << 4) + 15);
                    for (int y = y0; y <= y1; y++) {
                        for (int z = z0; z <= z1; z++) {
                            for (int x = x0; x <= x1; x++) {
                                if (isLitCampfire(section.getBlockState(x & 15, y & 15, z & 15))) return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean isLitCampfire(BlockState blockState) {
        return blockState.is(Blocks.CAMPFIRE)
                && blockState.hasProperty(BlockStateProperties.LIT)
                && blockState.getValue(BlockStateProperties.LIT);
    }

    private static void showStatus(ServerPlayer player, RestMode mode, float allowance, float maximum,
                                   int delayTicks) {
        if (delayTicks > 0) {
            player.displayClientMessage(Component.translatable("message.moveearth_addtional.rest.preparing",
                    (delayTicks + 19) / 20).withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        String sourceKey = mode == RestMode.BED
                ? "message.moveearth_addtional.rest.source.bed"
                : "message.moveearth_addtional.rest.source.campfire";
        player.displayClientMessage(Component.translatable("message.moveearth_addtional.rest.active",
                Component.translatable(sourceKey), formatHealth(allowance), formatHealth(maximum))
                .withStyle(allowance > 0.0F ? ChatFormatting.GREEN : ChatFormatting.GRAY), true);
    }

    private static String formatHealth(float health) {
        return health == Math.round(health) ? Integer.toString(Math.round(health))
                : String.format(java.util.Locale.ROOT, "%.1f", health);
    }

    private enum RestMode { NONE, BED, CAMPFIRE }

    private static final class RestState {
        private double x;
        private double y;
        private double z;
        private RestMode mode = RestMode.NONE;
        private int restTicks;
        private int healTicks;
        /** Not cleared by resetProgress(): the scan budget is per player, not per rest attempt. */
        private final RestCampfireProbe campfireProbe = new RestCampfireProbe(CAMPFIRE_SCAN_INTERVAL_TICKS);
        private boolean nearCampfire;
        private BlockPos campfireCheckedAt;
        private boolean wasSleeping;
        private boolean bedContinuation;
        private ResourceKey<Level> bedDimension;
        private BlockPos bedPosition;

        private RestState(ServerPlayer player) {
            x = player.getX();
            y = player.getY();
            z = player.getZ();
        }

        private boolean updatePosition(ServerPlayer player) {
            double dx = player.getX() - x;
            double dy = player.getY() - y;
            double dz = player.getZ() - z;
            x = player.getX();
            y = player.getY();
            z = player.getZ();
            return dx * dx + dy * dy + dz * dz > MOVEMENT_EPSILON_SQR;
        }

        private void begin(RestMode nextMode) {
            mode = nextMode;
            restTicks = 0;
            healTicks = 0;
        }

        private void recordBed(ResourceKey<Level> dimension, BlockPos position) {
            bedDimension = dimension;
            bedPosition = position.immutable();
        }

        private boolean canContinueBedRest(ServerPlayer player, boolean justWoke, boolean moved,
                                           int requiredRestTicks) {
            if (mode != RestMode.BED || bedPosition == null
                    || !player.level().dimension().equals(bedDimension)) return false;
            if (!player.level().getBlockState(bedPosition).is(BlockTags.BEDS)
                    || player.distanceToSqr(bedPosition.getX() + 0.5D, bedPosition.getY() + 0.5D,
                    bedPosition.getZ() + 0.5D) > 9.0D) return false;
            if (justWoke && restTicks >= requiredRestTicks) bedContinuation = true;
            return bedContinuation && (!moved || justWoke);
        }

        private void resetProgress() {
            mode = RestMode.NONE;
            restTicks = 0;
            healTicks = 0;
            bedContinuation = false;
            bedDimension = null;
            bedPosition = null;
        }
    }
}
