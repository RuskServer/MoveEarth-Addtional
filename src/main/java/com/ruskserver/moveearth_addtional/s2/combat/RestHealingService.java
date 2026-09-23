package com.ruskserver.moveearth_addtional.s2.combat;

import com.ruskserver.moveearth_addtional.CompatEventHandler;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.ServerSchedule;
import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import com.ruskserver.moveearth_addtional.s2.siege.PrisonerService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
            if (player.tickCount % 20 == 0 || !state.campfireChecked) {
                state.nearCampfire = hasLitCampfire(player, S2TerritoryConfig.campfireRadius());
                state.campfireChecked = true;
            }
            if (!state.nearCampfire) {
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
        if (player.tickCount % 20 == 0) showStatus(player, mode, allowance, maximum,
                Math.max(0, startDelay - state.restTicks));
        if (allowance <= 0.0F || state.healTicks < interval) return;

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
                && player.getHealth() < player.getMaxHealth()
                && !CombatTagService.isTagged(player)
                && !CompatEventHandler.isPlayerDown(player)
                && !PrisonerService.isMovementRestricted(player);
    }

    private static void interrupt(ServerPlayer player) {
        RestState state = STATES.get(player.getUUID());
        if (state != null) state.resetProgress();
    }

    private static boolean hasLitCampfire(ServerPlayer player, int radius) {
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            var blockState = player.level().getBlockState(pos);
            if (blockState.is(Blocks.CAMPFIRE)
                    && blockState.hasProperty(BlockStateProperties.LIT)
                    && blockState.getValue(BlockStateProperties.LIT)) return true;
        }
        return false;
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
        private boolean campfireChecked;
        private boolean nearCampfire;
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
            campfireChecked = false;
            nearCampfire = false;
            bedContinuation = false;
            bedDimension = null;
            bedPosition = null;
        }
    }
}
