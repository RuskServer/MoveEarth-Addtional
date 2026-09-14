package com.ruskserver.moveearth_addtional.s2.siege;

import com.ruskserver.moveearth_addtional.CompatEventHandler;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.ServerSchedule;
import com.ruskserver.moveearth_addtional.block.ModBlocks;
import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import com.ruskserver.moveearth_addtional.item.ModItems;
import com.ruskserver.moveearth_addtional.s2.combat.CombatTagSavedData;
import com.ruskserver.moveearth_addtional.s2.combat.CombatTagService;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Physical restraint, escort, imprisonment and release lifecycle. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class PrisonerService {
    private static final Map<UUID, RestraintAttempt> ATTEMPTS = new HashMap<>();
    private static final double INTAKE_DISTANCE_SQR = 36.0D;
    private static final double JAIL_RADIUS_SQR = 100.0D;

    private PrisonerService() { }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onPlayerInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer actor) || event.getHand() != InteractionHand.MAIN_HAND) return;
        UUID targetId = captiveId(event.getTarget());
        if (targetId == null || targetId.equals(actor.getUUID())) return;
        PrisonerSavedData prisoners = PrisonerSavedData.get(actor.server);
        PrisonerSavedData.Custody custody = prisoners.custody(targetId).orElse(null);

        if (custody != null && actor.isShiftKeyDown() && actor.getMainHandItem().isEmpty()
                && NationSavedData.get(actor.server).nationIdFor(actor.getUUID())
                .filter(custody.homeNation()::equals).isPresent()) {
            prisoners.releaseCustody(targetId);
            CombatTagService.releaseBody(actor.server, targetId, true);
            ATTEMPTS.values().removeIf(value -> value.targetId.equals(targetId));
            actor.sendSystemMessage(MoveEarthMessage.success(Component.translatable(
                    "message.moveearth_addtional.prisoner.rescued")));
            event.setCanceled(true);
            return;
        }

        if (!actor.getMainHandItem().is(ModItems.RESTRAINTS.get()) || !isDowned(actor.server, event.getTarget(), targetId)) return;
        if (prisoners.prisoner(actor.getUUID()).isPresent() || prisoners.custody(actor.getUUID()).isPresent()
                || prisoners.prisoner(targetId).isPresent() || custody != null
                || prisoners.custodyByCaptor(actor.getUUID()).isPresent()) {
            actor.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                    "message.moveearth_addtional.prisoner.already_held")));
            event.setCanceled(true);
            return;
        }
        NationSavedData nations = NationSavedData.get(actor.server);
        UUID actorNation = nations.nationIdFor(actor.getUUID()).orElse(null);
        UUID targetNation = nations.nationIdFor(targetId).orElse(null);
        if (actorNation == null || actorNation.equals(targetNation)
                || !hasCaptureConflict(actor.server, targetId, targetNation, actorNation)) return;
        UUID targetHome = targetNation == null ? targetId : targetNation;
        int downedTicks = event.getTarget() instanceof ServerPlayer player
                ? CompatEventHandler.playerDownedTicks(player) : event.getTarget().tickCount;
        if (downedTicks >= 0 && downedTicks < S2TerritoryConfig.captureProtectionTicks()) {
            actor.sendSystemMessage(MoveEarthMessage.warning(Component.translatable(
                    "message.moveearth_addtional.prisoner.capture_protected")));
            event.setCanceled(true);
            return;
        }
        ATTEMPTS.put(actor.getUUID(), new RestraintAttempt(targetId, actor.server.getTickCount(),
                actorNation, targetHome));
        actor.sendSystemMessage(MoveEarthMessage.info(Component.translatable(
                "message.moveearth_addtional.prisoner.restraining")));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        tickAttempt(player);
        PrisonerSavedData data = PrisonerSavedData.get(player.server);
        PrisonerSavedData.Custody custody = data.custodyByCaptor(player.getUUID()).orElse(null);
        if (custody != null) tickEscort(player, custody, data);
        PrisonerSavedData.Prisoner prisoner = data.prisoner(player.getUUID()).orElse(null);
        if (prisoner != null) tickPrisoner(player, data, prisoner);
        if (data.custody(player.getUUID()).isPresent() || prisoner != null) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1, false, false, false));
        }
        if (isMovementRestricted(player)) {
            player.stopRiding();
            if (player.getAbilities().flying) {
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
        }
    }

    private static void tickAttempt(ServerPlayer captor) {
        RestraintAttempt attempt = ATTEMPTS.get(captor.getUUID());
        if (attempt == null) return;
        Entity target = findCaptiveEntity(captor.server, attempt.targetId);
        if (target == null || !captor.getMainHandItem().is(ModItems.RESTRAINTS.get())
                || captor.distanceToSqr(target) > 16.0D || !isDowned(captor.server, target, attempt.targetId)
                || !hasCaptureConflict(captor.server, attempt.targetId,
                NationSavedData.get(captor.server).nationIdFor(attempt.targetId).orElse(null),
                attempt.captorNation)) {
            ATTEMPTS.remove(captor.getUUID());
            captor.displayClientMessage(Component.translatable(
                    "message.moveearth_addtional.prisoner.restraint_cancelled").withStyle(ChatFormatting.RED), true);
            return;
        }
        int elapsed = captor.server.getTickCount() - attempt.startedTick;
        int required = S2TerritoryConfig.restraintTicks();
        if (elapsed < required) {
            captor.displayClientMessage(Component.literal("拘束中 " + Math.min(100, elapsed * 100 / required) + "%")
                    .withStyle(ChatFormatting.GOLD), true);
            return;
        }
        PrisonerSavedData data = PrisonerSavedData.get(captor.server);
        if (data.beginCustody(attempt.targetId, attempt.captiveNation, attempt.captorNation,
                captor.getUUID(), target.level().dimension().location(), target.blockPosition())
                != PrisonerSavedData.CustodyResult.RESTRAINED) {
            ATTEMPTS.remove(captor.getUUID());
            return;
        }
        if (target instanceof ServerPlayer captive && !CompatEventHandler.revivePlayer(captive)) {
            data.releaseCustody(attempt.targetId);
            captor.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                    "message.moveearth_addtional.prisoner.revive_failed")));
        } else {
            CombatTagSavedData.get(captor.server).tag(attempt.targetId, captor.getUUID(),
                    S2TerritoryConfig.captivityMaxTicks());
            captor.getMainHandItem().hurtAndBreak(1, captor, EquipmentSlot.MAINHAND);
            captor.server.getPlayerList().broadcastSystemMessage(MoveEarthMessage.warning(Component.translatable(
                    "message.moveearth_addtional.prisoner.escorting", captiveName(target))), false);
        }
        ATTEMPTS.remove(captor.getUUID());
    }

    private static void tickEscort(ServerPlayer captor, PrisonerSavedData.Custody custody,
                                   PrisonerSavedData data) {
        Entity captive = findCaptiveEntity(captor.server, custody.playerId());
        if (captive == null || captive.level() != captor.level()
                || captor.distanceToSqr(captive) > Math.pow(S2TerritoryConfig.escortMaxDistance(), 2.0D)
                || !captor.isAlive() || CompatEventHandler.isPlayerDown(captor)
                || !hasCaptureConflict(captor.server, custody.playerId(),
                NationSavedData.get(captor.server).nationIdFor(custody.playerId()).orElse(null),
                custody.holdingNation())) {
            data.releaseCustody(custody.playerId());
            CombatTagService.releaseBody(captor.server, custody.playerId(), true);
            return;
        }
        captor.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, false, false, false));
        if (captor.distanceToSqr(captive) > 6.25D) {
            double x = captor.getX() - Math.sin(Math.toRadians(captor.getYRot())) * 1.5D;
            double z = captor.getZ() + Math.cos(Math.toRadians(captor.getYRot())) * 1.5D;
            captive.teleportTo(x, captor.getY(), z);
        }
        if (captor.tickCount % 20 == 0) {
            data.moveCustody(custody.playerId(), captive.level().dimension().location(), captive.blockPosition());
            if (captive instanceof ArmorStand) {
                CombatTagSavedData tags = CombatTagSavedData.get(captor.server);
                CombatTagSavedData.CombatState state = tags.state(custody.playerId()).orElse(null);
                if (state != null) tags.updateBody(custody.playerId(), captive.level().dimension().location(),
                        captive.blockPosition(), state.health(), state.downed(), state.killed());
            }
            captor.displayClientMessage(Component.literal("護送中: " + captiveName(captive)
                            + "・残り " + formatTicks(custody.remainingTicks()))
                    .withStyle(ChatFormatting.GOLD), true);
            if (captive instanceof ServerPlayer player) player.displayClientMessage(Component.literal(
                    "拘束中: " + captor.getGameProfile().getName() + "・残り "
                            + formatTicks(custody.remainingTicks())).withStyle(ChatFormatting.RED), true);
        }
    }

    private static void tickPrisoner(ServerPlayer player, PrisonerSavedData data,
                                     PrisonerSavedData.Prisoner prisoner) {
        Destination jail = jailDestination(player.server, prisoner).orElse(null);
        if (jail == null) {
            releaseOrphan(player, data, prisoner);
            return;
        }
        if (player.level() != jail.level || player.distanceToSqr(jail.pos.getCenter()) > JAIL_RADIUS_SQR) teleport(player, jail);
        if (player.tickCount % 20 == 0) player.displayClientMessage(Component.literal(
                "捕虜・残り " + formatTicks(prisoner.remainingTicks())).withStyle(ChatFormatting.RED), true);
    }

    public static boolean tryImprisonAt(ServerPlayer captor, BlockPos intake) {
        PrisonerSavedData data = PrisonerSavedData.get(captor.server);
        PrisonerSavedData.Custody custody = data.custodyByCaptor(captor.getUUID()).orElse(null);
        if (custody == null) return false;
        UUID owner = TerritorySavedData.get(captor.server).controllingNation(captor.server,
                captor.level().dimension().location(), intake).orElse(null);
        if (owner == null || !owner.equals(custody.holdingNation())) {
            captor.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                    "message.moveearth_addtional.prisoner.invalid_intake")));
            return false;
        }
        if (!hasJailSpace(captor.serverLevel(), intake)) {
            captor.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                    "message.moveearth_addtional.prisoner.invalid_intake")));
            return false;
        }
        Entity captive = findCaptiveEntity(captor.server, custody.playerId());
        if (captive == null || captive.level() != captor.level()
                || captive.distanceToSqr(intake.getCenter()) > INTAKE_DISTANCE_SQR) return false;
        if (data.imprison(custody.playerId(), captor.level().dimension().location(), intake,
                System.currentTimeMillis()) != PrisonerSavedData.CaptureResult.CAPTURED) return false;
        String name = captiveName(captive);
        if (captive instanceof ServerPlayer player) teleport(player, new Destination(captor.serverLevel(), intake.above()));
        else captive.discard();
        CombatTagService.releaseBody(captor.server, custody.playerId(), false);
        captor.server.getPlayerList().broadcastSystemMessage(MoveEarthMessage.warning(Component.translatable(
                "message.moveearth_addtional.prisoner.captured", name,
                NationSavedData.get(captor.server).nation(owner).map(NationSavedData.Nation::name).orElse("?"))), false);
        return true;
    }

    public static void onPrisonIntakeRemoved(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        PrisonerSavedData data = PrisonerSavedData.get(serverLevel.getServer());
        for (PrisonerSavedData.Prisoner prisoner : data.prisoners()) {
            if (level.dimension().location().equals(prisoner.jailDimension()) && pos.equals(prisoner.jailPos())) {
                data.release(prisoner.playerId());
                ServerPlayer online = serverLevel.getServer().getPlayerList().getPlayer(prisoner.playerId());
                if (online != null) {
                    releaseDestination(serverLevel.getServer(), prisoner.homeNation())
                            .ifPresent(destination -> teleport(online, destination));
                    data.acknowledgeRelease(prisoner.playerId());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % 20L != 13L) return;
        if (server.isDedicatedServer() && !ServerSchedule.isOpenNow()) return;
        PrisonerSavedData data = PrisonerSavedData.get(server);
        for (PrisonerSavedData.TimedRelease release : data.advanceCaptivity(20L)) {
            CombatTagService.releaseBody(server, release.playerId(), true);
            ServerPlayer player = server.getPlayerList().getPlayer(release.playerId());
            if (player != null) {
                releaseDestination(server, release.homeNation()).ifPresent(dest -> teleport(player, dest));
                data.acknowledgeRelease(release.playerId());
                player.sendSystemMessage(MoveEarthMessage.success(Component.translatable(
                        "message.moveearth_addtional.prisoner.released")));
            }
        }
        for (PrisonerSavedData.CaptivityWindow window : data.captivityWindows()) {
            if (data.custody(window.playerId()).isPresent() || data.prisoner(window.playerId()).isPresent()) continue;
            boolean homeIsNation = NationSavedData.get(server).nation(window.homeNation()).isPresent();
            boolean activeConflict = homeIsNation
                    ? SiegeSavedData.get(server).hasConflictBetween(window.homeNation(), window.holdingNation())
                    : SiegeSavedData.get(server).hasIndividualConflictBetween(
                    window.playerId(), window.holdingNation());
            if (!activeConflict) data.clearCaptivityWindow(window.playerId());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && isRestrained(player)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isRestrained(player)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onOutgoingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && isMovementRestricted(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRestrictedItemUse(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && isCaptive(player)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRestrictedBlockUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().getBlockState(event.getPos()).is(ModBlocks.PRISON_INTAKE.get())
                && PrisonerSavedData.get(player.server).custodyByCaptor(player.getUUID()).isPresent()) {
            if (!player.level().isClientSide()) tryImprisonAt(player, event.getPos());
            event.setCanceled(true);
            return;
        }
        if (isCaptive(player)) event.setCanceled(true);
    }

    private static boolean isRestrained(ServerPlayer player) {
        PrisonerSavedData data = PrisonerSavedData.get(player.server);
        return data.prisoner(player.getUUID()).isPresent() || data.custody(player.getUUID()).isPresent();
    }

    private static boolean isCaptive(ServerPlayer player) {
        PrisonerSavedData data = PrisonerSavedData.get(player.server);
        return data.prisoner(player.getUUID()).isPresent() || data.custody(player.getUUID()).isPresent();
    }

    public static boolean isMovementRestricted(ServerPlayer player) {
        PrisonerSavedData data = PrisonerSavedData.get(player.server);
        return data.prisoner(player.getUUID()).isPresent()
                || data.custody(player.getUUID()).isPresent()
                || data.custodyByCaptor(player.getUUID()).isPresent();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PrisonerSavedData data = PrisonerSavedData.get(player.server);
        UUID homeNation = data.pendingReleaseHome(player.getUUID()).orElse(null);
        if (homeNation != null) {
            releaseDestination(player.server, homeNation).ifPresent(destination -> teleport(player, destination));
            data.acknowledgeRelease(player.getUUID());
            player.sendSystemMessage(MoveEarthMessage.success(Component.translatable(
                    "message.moveearth_addtional.prisoner.released")));
            return;
        }
        data.prisoner(player.getUUID()).flatMap(prisoner -> jailDestination(player.server, prisoner))
                .ifPresent(destination -> teleport(player, destination));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PrisonerSavedData data = PrisonerSavedData.get(player.server);
        PrisonerSavedData.Custody escorted = data.custodyByCaptor(player.getUUID()).orElse(null);
        if (escorted != null) {
            data.releaseCustody(escorted.playerId());
            CombatTagService.releaseBody(player.server, escorted.playerId(), true);
        }
        ATTEMPTS.remove(player.getUUID());
    }

    public static boolean canReturnAll(MinecraftServer server, UUID firstNation, UUID secondNation) {
        NationSavedData nations = NationSavedData.get(server);
        return PrisonerSavedData.get(server).between(firstNation, secondNation).stream()
                .allMatch(prisoner -> nations.nation(prisoner.homeNation()).isPresent()
                        && nations.nation(prisoner.holdingNation()).isPresent()
                        && releaseDestination(server, prisoner.homeNation()).isPresent());
    }

    public static int returnAll(MinecraftServer server, UUID firstNation, UUID secondNation) {
        PrisonerSavedData data = PrisonerSavedData.get(server);
        List<PrisonerSavedData.Prisoner> released = data.releaseBetween(firstNation, secondNation);
        for (PrisonerSavedData.Prisoner prisoner : released) {
            ServerPlayer online = server.getPlayerList().getPlayer(prisoner.playerId());
            if (online != null) {
                releaseDestination(server, prisoner.homeNation()).ifPresent(destination -> teleport(online, destination));
                data.acknowledgeRelease(prisoner.playerId());
            }
        }
        for (PrisonerSavedData.Custody custody : data.custodyRecords()) {
            if ((custody.homeNation().equals(firstNation) && custody.holdingNation().equals(secondNation))
                    || (custody.homeNation().equals(secondNation) && custody.holdingNation().equals(firstNation))) {
                data.releaseCustody(custody.playerId());
                CombatTagService.releaseBody(server, custody.playerId(), true);
            }
        }
        return released.size();
    }

    private static void releaseOrphan(ServerPlayer player, PrisonerSavedData data,
                                      PrisonerSavedData.Prisoner prisoner) {
        data.release(player.getUUID());
        data.acknowledgeRelease(player.getUUID());
        releaseDestination(player.server, prisoner.homeNation()).ifPresent(destination -> teleport(player, destination));
        player.sendSystemMessage(MoveEarthMessage.warning(Component.translatable(
                "message.moveearth_addtional.prisoner.holding_lost")));
    }

    private static boolean isDowned(MinecraftServer server, Entity entity, UUID playerId) {
        if (entity instanceof ServerPlayer player) return CompatEventHandler.isPlayerDown(player);
        return entity instanceof ArmorStand && CombatTagSavedData.get(server).state(playerId)
                .map(CombatTagSavedData.CombatState::downed).orElse(false);
    }

    private static boolean hasCaptureConflict(MinecraftServer server, UUID targetPlayer,
                                              UUID targetNation, UUID holdingNation) {
        SiegeSavedData sieges = SiegeSavedData.get(server);
        return targetNation == null
                ? sieges.hasIndividualConflictBetween(targetPlayer, holdingNation)
                : sieges.hasConflictBetween(targetNation, holdingNation);
    }

    private static UUID captiveId(Entity entity) {
        if (entity instanceof ServerPlayer player) return player.getUUID();
        return entity instanceof ArmorStand body && body.getPersistentData().hasUUID(CombatTagService.BODY_OWNER)
                ? body.getPersistentData().getUUID(CombatTagService.BODY_OWNER) : null;
    }

    private static Entity findCaptiveEntity(MinecraftServer server, UUID playerId) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) return online;
        UUID bodyId = CombatTagSavedData.get(server).state(playerId)
                .map(CombatTagSavedData.CombatState::bodyEntity).orElse(null);
        if (bodyId == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(bodyId);
            if (entity != null) return entity;
        }
        return null;
    }

    private static Optional<Destination> jailDestination(MinecraftServer server, PrisonerSavedData.Prisoner prisoner) {
        if (prisoner.jailDimension() == null || prisoner.jailPos() == null) return Optional.empty();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, prisoner.jailDimension()));
        if (level == null || !level.getBlockState(prisoner.jailPos()).is(ModBlocks.PRISON_INTAKE.get())) return Optional.empty();
        BlockPos feet = prisoner.jailPos().above();
        if (!hasJailSpace(level, prisoner.jailPos())) return Optional.empty();
        UUID owner = TerritorySavedData.get(server).controllingNation(server, prisoner.jailDimension(), prisoner.jailPos())
                .orElse(null);
        return prisoner.holdingNation().equals(owner) ? Optional.of(new Destination(level, feet))
                : Optional.empty();
    }

    private static Optional<Destination> releaseDestination(MinecraftServer server, UUID nationId) {
        Optional<Destination> capital = TerritorySavedData.get(server).cores().stream().filter(core -> core.nationId().equals(nationId)
                        && core.type() == TerritorySavedData.CoreType.CAPITAL)
                .findFirst().flatMap(core -> {
                    ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, core.dimension()));
                    if (level == null) return Optional.empty();
                    int x = core.pos().getX(), z = core.pos().getZ();
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
                    return Optional.of(new Destination(level, new BlockPos(x, y, z)));
                });
        if (capital.isPresent()) return capital;
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                spawn.getX(), spawn.getZ()) + 1;
        return Optional.of(new Destination(overworld, new BlockPos(spawn.getX(), y, spawn.getZ())));
    }

    private static boolean hasJailSpace(ServerLevel level, BlockPos intake) {
        BlockPos feet = intake.above();
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    private static void teleport(ServerPlayer player, Destination destination) {
        player.teleportTo(destination.level, destination.pos.getX() + 0.5D,
                destination.pos.getY(), destination.pos.getZ() + 0.5D, player.getYRot(), player.getXRot());
    }

    private static String captiveName(Entity target) {
        UUID id = captiveId(target);
        return target instanceof ServerPlayer player ? player.getGameProfile().getName()
                : id == null ? "?" : id.toString().substring(0, 8);
    }

    private static String formatTicks(long ticks) {
        long seconds = Math.max(0L, (ticks + 19L) / 20L);
        return "%d:%02d:%02d".formatted(seconds / 3600L, seconds / 60L % 60L, seconds % 60L);
    }

    private record RestraintAttempt(UUID targetId, int startedTick, UUID captorNation, UUID captiveNation) { }
    private record Destination(ServerLevel level, BlockPos pos) { }
}
