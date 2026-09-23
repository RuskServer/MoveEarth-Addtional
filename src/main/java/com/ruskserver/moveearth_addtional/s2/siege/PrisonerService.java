package com.ruskserver.moveearth_addtional.s2.siege;

import com.ruskserver.moveearth_addtional.CompatEventHandler;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.ServerSchedule;
import com.ruskserver.moveearth_addtional.block.ModBlocks;
import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import com.ruskserver.moveearth_addtional.item.ModItems;
import com.ruskserver.moveearth_addtional.s2.combat.CombatTagSavedData;
import com.ruskserver.moveearth_addtional.s2.combat.CombatTagService;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import com.ruskserver.moveearth_addtional.network.C2S_PrisonerActionPacket;
import com.ruskserver.moveearth_addtional.network.S2C_PrisonerSnapshotPacket;
import com.ruskserver.moveearth_addtional.network.S2C_PrisonerActionResultPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Physical restraint, escort, imprisonment and release lifecycle. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class PrisonerService {
    private static final Map<UUID, RestraintAttempt> ATTEMPTS = new HashMap<>();
    private static final Map<UUID, Integer> LAST_PATH_WARNING = new HashMap<>();
    private static final Map<UUID, Integer> LAST_RESTRICTION_NOTICE = new HashMap<>();
    private static final double INTAKE_DISTANCE_SQR = 36.0D;
    private static final double JAIL_RADIUS_SQR = 100.0D;

    private PrisonerService() { }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onPlayerInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer actor)) return;
        UUID targetId = captiveId(event.getTarget());
        if (targetId == null || targetId.equals(actor.getUUID())) return;
        // PlayerRevive handles both hand events without checking the hand. Consume the offhand
        // event too, otherwise a single click with restraints also starts reviving the captive.
        if (actor.getMainHandItem().is(ModItems.RESTRAINTS.get())) {
            event.setCanceled(true);
            if (event.getHand() != InteractionHand.MAIN_HAND) return;
        } else if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        PrisonerSavedData prisoners = PrisonerSavedData.get(actor.server);
        PrisonerSavedData.Custody custody = prisoners.custody(targetId).orElse(null);

        if (custody != null && actor.isShiftKeyDown() && actor.getMainHandItem().isEmpty()
                && NationSavedData.get(actor.server).nationIdFor(actor.getUUID())
                .filter(custody.homeNation()::equals).isPresent()) {
            prisoners.releaseCustody(targetId);
            CombatTagService.releaseBody(actor.server, targetId, true);
            CombatTagSavedData.get(actor.server).consume(targetId);
            ATTEMPTS.values().removeIf(value -> value.targetId.equals(targetId));
            actor.sendSystemMessage(MoveEarthMessage.success(Component.translatable(
                    "message.moveearth_addtional.prisoner.rescued")));
            if (event.getTarget() instanceof ServerPlayer captive) captive.sendSystemMessage(
                    MoveEarthMessage.success(Component.translatable(
                            "message.moveearth_addtional.prisoner.rescued_captive",
                            actor.getGameProfile().getName())));
            com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(actor,
                    com.ruskserver.moveearth_addtional.advancement.ModCriteria.PRISONER_FREED);
            event.setCanceled(true);
            return;
        }

        if (!actor.getMainHandItem().is(ModItems.RESTRAINTS.get())) return;
        event.setCanceled(true);
        if (prisoners.prisoner(actor.getUUID()).isPresent() || prisoners.custody(actor.getUUID()).isPresent()) {
            sendFailure(actor, "message.moveearth_addtional.prisoner.captor_unavailable");
            return;
        }
        if (prisoners.custodyByCaptor(actor.getUUID()).isPresent()) {
            sendFailure(actor, "message.moveearth_addtional.prisoner.already_escorting");
            return;
        }
        if (prisoners.prisoner(targetId).isPresent() || custody != null) {
            sendFailure(actor, "message.moveearth_addtional.prisoner.already_held");
            return;
        }
        if (ATTEMPTS.values().stream().anyMatch(value -> value.targetId.equals(targetId))) {
            sendFailure(actor, "message.moveearth_addtional.prisoner.restraint_in_progress");
            return;
        }
        if (!isDowned(actor.server, event.getTarget(), targetId)) {
            sendFailure(actor, "message.moveearth_addtional.prisoner.target_not_downed");
            return;
        }
        NationSavedData nations = NationSavedData.get(actor.server);
        UUID actorHomeNation = nations.nationIdFor(actor.getUUID()).orElse(null);
        UUID targetNation = nations.nationIdFor(targetId).orElse(null);
        UUID actorNation = operationalNation(actor.server, actor.getUUID(), actorHomeNation);
        UUID targetConflictNation = operationalNation(actor.server, targetId, targetNation);
        if (actorNation == null) {
            sendFailure(actor, "message.moveearth_addtional.prisoner.captor_requires_nation");
            return;
        }
        if (actorNation.equals(targetConflictNation)) {
            sendFailure(actor, "message.moveearth_addtional.prisoner.target_friendly");
            return;
        }
        if (!hasCaptureConflict(actor.server, targetId, targetConflictNation, actorNation)) {
            sendFailure(actor, "message.moveearth_addtional.prisoner.no_capture_conflict");
            return;
        }
        UUID targetHome = targetNation == null ? targetId : targetNation;
        SiegeParticipationSavedData.Participation battle = SiegeParticipationSavedData.get(actor.server)
                .forPlayer(targetId).orElseGet(() -> SiegeParticipationSavedData.get(actor.server)
                        .forPlayer(actor.getUUID()).orElse(null));
        ATTEMPTS.put(actor.getUUID(), new RestraintAttempt(targetId, actor.server.getTickCount(),
                actorNation, targetHome, targetConflictNation == null ? targetHome : targetConflictNation,
                battle == null ? null : battle.siegeId(), battle == null ? null : battle.contractId()));
        actor.sendSystemMessage(MoveEarthMessage.info(Component.translatable(
                "message.moveearth_addtional.prisoner.restraining")));
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
            boolean restrictedMovement = player.isPassenger() || player.getAbilities().flying;
            player.stopRiding();
            if (player.getAbilities().flying) {
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
            if (restrictedMovement && player.server.getTickCount()
                    - LAST_RESTRICTION_NOTICE.getOrDefault(player.getUUID(), -100) >= 60) {
                LAST_RESTRICTION_NOTICE.put(player.getUUID(), player.server.getTickCount());
                player.sendSystemMessage(MoveEarthMessage.warning(Component.translatable(
                        "message.moveearth_addtional.prisoner.movement_restricted")));
            }
        }
        if (player.tickCount % 20 == 0) sendSnapshot(player, false, null);
    }

    private static void tickAttempt(ServerPlayer captor) {
        RestraintAttempt attempt = ATTEMPTS.get(captor.getUUID());
        if (attempt == null) return;
        Entity target = findCaptiveEntity(captor.server, attempt.targetId);
        String cancellationKey = restraintCancellationKey(captor, target, attempt);
        if (cancellationKey != null) {
            ATTEMPTS.remove(captor.getUUID());
            captor.sendSystemMessage(MoveEarthMessage.error(Component.translatable(cancellationKey)));
            if (target instanceof ServerPlayer captive) captive.sendSystemMessage(MoveEarthMessage.info(
                    Component.translatable("message.moveearth_addtional.prisoner.restraint_stopped_captive")));
            return;
        }
        int elapsed = captor.server.getTickCount() - attempt.startedTick;
        int required = S2TerritoryConfig.restraintTicks();
        int downedTicks = target instanceof ServerPlayer player
                ? CompatEventHandler.playerDownedTicks(player) : target.tickCount;
        int protectionRemaining = PrisonerRestraintPolicy.protectionRemaining(
                S2TerritoryConfig.captureProtectionTicks(), downedTicks);
        if (!PrisonerRestraintPolicy.canComplete(elapsed, required, protectionRemaining)) {
            Component status = protectionRemaining > 0
                    ? Component.translatable("message.moveearth_addtional.prisoner.status.protection_wait",
                            (protectionRemaining + 19) / 20)
                    : Component.translatable("message.moveearth_addtional.prisoner.status.restraining",
                            Math.min(100, elapsed * 100 / Math.max(1, required)));
            captor.displayClientMessage(status.copy().withStyle(ChatFormatting.GOLD), true);
            return;
        }
        PrisonerSavedData data = PrisonerSavedData.get(captor.server);
        if (data.beginCustody(attempt.targetId, attempt.captiveNation, attempt.captiveConflictNation,
                attempt.captorNation,
                captor.getUUID(), target.level().dimension().location(), target.blockPosition(),
                attempt.siegeId, attempt.contractId)
                != PrisonerSavedData.CustodyResult.RESTRAINED) {
            ATTEMPTS.remove(captor.getUUID());
            sendFailure(captor, "message.moveearth_addtional.prisoner.state_changed");
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
            if (target instanceof ServerPlayer captive) captive.sendSystemMessage(MoveEarthMessage.warning(
                    Component.translatable("message.moveearth_addtional.prisoner.escort_started_captive",
                            captor.getGameProfile().getName(), formatTicks(S2TerritoryConfig.captivityMaxTicks()))));
        }
        ATTEMPTS.remove(captor.getUUID());
    }

    private static void tickEscort(ServerPlayer captor, PrisonerSavedData.Custody custody,
                                   PrisonerSavedData data) {
        if (PrisonerVehicleTransportService.isLoaded(captor.server, custody.playerId())) return;
        Entity captive = findCaptiveEntity(captor.server, custody.playerId());
        String cancellationKey = escortCancellationKey(captor, captive, custody);
        if (cancellationKey != null) {
            cancelCustody(captor, captive, custody, data, cancellationKey);
            return;
        }
        captor.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, false, false, false));
        if (captor.distanceToSqr(captive) > 6.25D) {
            Vec3 destination = safeEscortPosition(captor, captive);
            if (destination != null) {
                captive.teleportTo(destination.x, destination.y, destination.z);
            } else if (captor.server.getTickCount() - LAST_PATH_WARNING.getOrDefault(captor.getUUID(), -100) >= 40) {
                LAST_PATH_WARNING.put(captor.getUUID(), captor.server.getTickCount());
                captor.sendSystemMessage(MoveEarthMessage.warning(Component.translatable(
                        "message.moveearth_addtional.prisoner.escort_path_blocked")));
            }
        }
        if (captor.tickCount % 20 == 0) {
            data.moveCustody(custody.playerId(), captive.level().dimension().location(), captive.blockPosition());
            if (captive instanceof ArmorStand) {
                CombatTagSavedData tags = CombatTagSavedData.get(captor.server);
                CombatTagSavedData.CombatState state = tags.state(custody.playerId()).orElse(null);
                if (state != null) tags.updateBody(custody.playerId(), captive.level().dimension().location(),
                        captive.blockPosition(), state.health(), state.downed(), state.killed());
            }
            captor.displayClientMessage(Component.translatable(
                            "message.moveearth_addtional.prisoner.status.escort_captor",
                            captiveName(captive), formatTicks(custody.remainingTicks()))
                    .withStyle(ChatFormatting.GOLD), true);
            if (captive instanceof ServerPlayer player) player.displayClientMessage(Component.translatable(
                    "message.moveearth_addtional.prisoner.status.escort_captive",
                    nationName(captor.server, custody.holdingNation()), captor.getGameProfile().getName(),
                    formatTicks(custody.remainingTicks())).withStyle(ChatFormatting.RED), true);
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
        if (player.tickCount % 20 == 0) player.displayClientMessage(Component.translatable(
                "message.moveearth_addtional.prisoner.status.imprisoned",
                nationName(player.server, prisoner.holdingNation()), formatTicks(prisoner.remainingTicks()))
                .withStyle(ChatFormatting.RED), true);
    }

    public static boolean tryImprisonAt(ServerPlayer captor, BlockPos intake) {
        PrisonerSavedData data = PrisonerSavedData.get(captor.server);
        PrisonerSavedData.Custody custody = data.custodyByCaptor(captor.getUUID()).orElse(null);
        if (custody == null) {
            sendFailure(captor, "message.moveearth_addtional.prisoner.intake.no_escort");
            return false;
        }
        UUID owner = TerritorySavedData.get(captor.server).controllingNation(captor.server,
                captor.level().dimension().location(), intake).orElse(null);
        if (owner == null) {
            sendFailure(captor, "message.moveearth_addtional.prisoner.intake.inactive_territory");
            return false;
        }
        if (!owner.equals(custody.holdingNation())) {
            sendFailure(captor, "message.moveearth_addtional.prisoner.intake.foreign_territory");
            return false;
        }
        if (!hasJailSpace(captor.serverLevel(), intake)) {
            sendFailure(captor, "message.moveearth_addtional.prisoner.intake.unsafe_space");
            return false;
        }
        Entity captive = findCaptiveEntity(captor.server, custody.playerId());
        if (captive == null) {
            sendFailure(captor, "message.moveearth_addtional.prisoner.intake.captive_unavailable");
            return false;
        }
        if (captive.level() != captor.level()) {
            sendFailure(captor, "message.moveearth_addtional.prisoner.intake.wrong_dimension");
            return false;
        }
        if (captive.distanceToSqr(intake.getCenter()) > INTAKE_DISTANCE_SQR) {
            captor.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                    "message.moveearth_addtional.prisoner.intake.too_far",
                    (int) Math.ceil(Math.sqrt(captive.distanceToSqr(intake.getCenter()))),
                    (int) Math.sqrt(INTAKE_DISTANCE_SQR))));
            PacketDistributor.sendToPlayer(captor, new S2C_PrisonerActionResultPacket(false,
                    "screen.moveearth_addtional.prisoner.action.too_far"));
            return false;
        }
        if (data.imprison(custody.playerId(), captor.level().dimension().location(), intake,
                System.currentTimeMillis()) != PrisonerSavedData.CaptureResult.CAPTURED) {
            sendFailure(captor, "message.moveearth_addtional.prisoner.state_changed");
            return false;
        }
        String name = captiveName(captive);
        if (captive instanceof ServerPlayer player) {
            teleport(player, new Destination(captor.serverLevel(), intake.above()));
            player.sendSystemMessage(MoveEarthMessage.warning(Component.translatable(
                    "message.moveearth_addtional.prisoner.imprisoned_captive", nationName(captor.server, owner),
                    formatTicks(custody.remainingTicks()))));
        }
        else captive.discard();
        CombatTagService.releaseBody(captor.server, custody.playerId(), false);
        captor.server.getPlayerList().broadcastSystemMessage(MoveEarthMessage.warning(Component.translatable(
                "message.moveearth_addtional.prisoner.captured", name,
                NationSavedData.get(captor.server).nation(owner).map(NationSavedData.Nation::name).orElse("?"))), false);
        PacketDistributor.sendToPlayer(captor, new S2C_PrisonerActionResultPacket(true,
                "screen.moveearth_addtional.prisoner.action.imprisoned"));
        com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(captor,
                com.ruskserver.moveearth_addtional.advancement.ModCriteria.PRISONER_IMPRISONED);
        return true;
    }

    public static void openIntakeScreen(ServerPlayer player, BlockPos intake) {
        sendSnapshot(player, true, intake);
    }

    public static void handleAction(ServerPlayer actor, C2S_PrisonerActionPacket.Action action,
                                    UUID targetId, BlockPos intake) {
        if (action == null) return;
        switch (action) {
            case IMPRISON -> {
                if (intake == null || actor.distanceToSqr(intake.getCenter()) > 64.0D
                        || !actor.level().getBlockState(intake).is(ModBlocks.PRISON_INTAKE.get())) {
                    sendFailure(actor, "message.moveearth_addtional.prisoner.intake.invalid_remote");
                } else {
                    tryImprisonAt(actor, intake);
                }
            }
            case RELEASE -> releaseBy(actor, targetId);
            case TRANSFER -> transferEscort(actor, targetId);
        }
        sendSnapshot(actor, true, intake);
    }

    public static void sendSnapshot(ServerPlayer viewer, boolean openScreen, BlockPos intakePos) {
        PrisonerSavedData data = PrisonerSavedData.get(viewer.server);
        NationSavedData nations = NationSavedData.get(viewer.server);
        PrisonerSavedData.Custody escorting = data.custodyByCaptor(viewer.getUUID()).orElse(null);
        PrisonerSavedData.Custody escorted = data.custody(viewer.getUUID()).orElse(null);
        PrisonerSavedData.Prisoner imprisoned = data.prisoner(viewer.getUUID()).orElse(null);
        int state = escorting != null ? 1 : escorted != null ? 2 : imprisoned != null ? 3 : 0;
        String counterpart = escorting != null ? playerName(viewer.server, escorting.playerId())
                : escorted != null ? playerName(viewer.server, escorted.captor()) : "";
        UUID holdingId = escorting != null ? escorting.holdingNation()
                : escorted != null ? escorted.holdingNation()
                : imprisoned != null ? imprisoned.holdingNation() : null;
        long remaining = escorting != null ? escorting.remainingTicks()
                : escorted != null ? escorted.remainingTicks()
                : imprisoned != null ? imprisoned.remainingTicks() : 0L;
        String holdingName = holdingId == null ? "" : nationName(viewer.server, holdingId);
        ResourceLocation jailDimension = imprisoned == null ? null : imprisoned.jailDimension();
        BlockPos jailPos = imprisoned == null ? null : imprisoned.jailPos();

        List<S2C_PrisonerSnapshotPacket.EntryView> entries = new java.util.ArrayList<>();
        List<S2C_PrisonerSnapshotPacket.CandidateView> candidates = new java.util.ArrayList<>();
        UUID viewerNation = nations.nationIdFor(viewer.getUUID()).orElse(null);
        boolean manager = viewer.hasPermissions(2) || nations.can(viewer.getUUID(), S2Permission.MANAGE_PRISONERS);
        if (openScreen && viewerNation != null) {
            for (PrisonerSavedData.Custody value : data.custodyRecords()) {
                if (!viewerNation.equals(value.holdingNation()) && !viewerNation.equals(value.homeNation())
                        && !viewerNation.equals(value.conflictNation())) continue;
                boolean heldByViewer = viewerNation.equals(value.holdingNation());
                entries.add(new S2C_PrisonerSnapshotPacket.EntryView(value.playerId(),
                        playerName(viewer.server, value.playerId()), 0, heldByViewer,
                        nationName(viewer.server, heldByViewer ? value.homeNation() : value.holdingNation()),
                        value.remainingTicks(), heldByViewer && (manager || value.captor().equals(viewer.getUUID()))));
            }
            for (PrisonerSavedData.Prisoner value : data.prisoners()) {
                if (!viewerNation.equals(value.holdingNation()) && !viewerNation.equals(value.homeNation())
                        && !viewerNation.equals(value.conflictNation())) continue;
                boolean heldByViewer = viewerNation.equals(value.holdingNation());
                entries.add(new S2C_PrisonerSnapshotPacket.EntryView(value.playerId(),
                        playerName(viewer.server, value.playerId()), 1, heldByViewer,
                        nationName(viewer.server, heldByViewer ? value.homeNation() : value.holdingNation()),
                        value.remainingTicks(), heldByViewer && manager));
            }
        }
        if (openScreen && escorting != null) {
            for (ServerPlayer candidate : viewer.server.getPlayerList().getPlayers()) {
                UUID candidateHome = nations.nationIdFor(candidate.getUUID()).orElse(null);
                boolean sameNation = escorting.holdingNation().equals(operationalNation(
                        viewer.server, candidate.getUUID(), candidateHome));
                if (!PrisonerTransferPolicy.allowed(true, true, candidate != viewer,
                        candidate.level() == viewer.level(), viewer.distanceToSqr(candidate), candidate.isAlive(),
                        candidate.isShiftKeyDown(), sameNation, isMovementRestricted(candidate),
                        data.custodyByCaptor(candidate.getUUID()).isPresent())) continue;
                candidates.add(new S2C_PrisonerSnapshotPacket.CandidateView(candidate.getUUID(),
                        candidate.getGameProfile().getName()));
            }
        }
        S2C_PrisonerSnapshotPacket.IntakeView intakeView = intakeView(
                viewer, intakePos, escorting, viewerNation);
        PacketDistributor.sendToPlayer(viewer, new S2C_PrisonerSnapshotPacket(openScreen, state,
                counterpart, holdingName, remaining, jailDimension, jailPos, intakePos,
                intakeView, entries, candidates));
    }

    private static S2C_PrisonerSnapshotPacket.IntakeView intakeView(ServerPlayer viewer, BlockPos intake,
                                                                     PrisonerSavedData.Custody custody,
                                                                     UUID viewerNation) {
        if (intake == null || viewer.distanceToSqr(intake.getCenter()) > 64.0D
                || !viewer.level().getBlockState(intake).is(ModBlocks.PRISON_INTAKE.get())) {
            return new S2C_PrisonerSnapshotPacket.IntakeView(false, "", false,
                    false, false, false, 0, false);
        }
        UUID owner = TerritorySavedData.get(viewer.server).controllingNation(viewer.server,
                viewer.level().dimension().location(), intake).orElse(null);
        boolean correctTerritory = PrisonIntakePolicy.isActiveHoldingTerritory(owner,
                custody == null ? null : custody.holdingNation(), viewerNation);
        boolean safe = hasJailSpace(viewer.serverLevel(), intake);
        Entity captive = custody == null ? null : findCaptiveEntity(viewer.server, custody.playerId());
        boolean sameDimension = captive != null && captive.level() == viewer.level();
        int distance = captive == null || !sameDimension ? 0
                : (int) Math.ceil(Math.sqrt(captive.distanceToSqr(intake.getCenter())));
        return new S2C_PrisonerSnapshotPacket.IntakeView(true,
                owner == null ? "" : nationName(viewer.server, owner), correctTerritory, safe,
                captive != null, sameDimension, distance,
                custody != null && correctTerritory && safe && sameDimension
                        && distance <= (int) Math.sqrt(INTAKE_DISTANCE_SQR));
    }

    private static void releaseBy(ServerPlayer actor, UUID targetId) {
        if (targetId == null) {
            sendFailure(actor, "message.moveearth_addtional.prisoner.release_denied");
            return;
        }
        PrisonerSavedData data = PrisonerSavedData.get(actor.server);
        NationSavedData nations = NationSavedData.get(actor.server);
        UUID actorNation = nations.nationIdFor(actor.getUUID()).orElse(null);
        PrisonerSavedData.Custody custody = data.custody(targetId).orElse(null);
        PrisonerSavedData.Prisoner prisoner = data.prisoner(targetId).orElse(null);
        UUID holding = custody != null ? custody.holdingNation() : prisoner != null ? prisoner.holdingNation() : null;
        boolean ownEscort = custody != null && custody.captor().equals(actor.getUUID());
        boolean manager = actor.hasPermissions(2) || nations.can(actor.getUUID(), S2Permission.MANAGE_PRISONERS);
        if (holding == null || (!ownEscort && (!manager || !holding.equals(actorNation)))) {
            sendFailure(actor, "message.moveearth_addtional.prisoner.release_denied");
            return;
        }
        UUID home = custody != null ? custody.homeNation() : prisoner.homeNation();
        if (custody != null) data.releaseCustodyToHome(targetId); else data.release(targetId);
        CombatTagService.releaseBody(actor.server, targetId, true);
        CombatTagSavedData.get(actor.server).consume(targetId);
        ServerPlayer captive = actor.server.getPlayerList().getPlayer(targetId);
        if (captive != null) {
            releaseDestination(actor.server, home).ifPresent(destination -> teleport(captive, destination));
            data.acknowledgeRelease(targetId);
            captive.sendSystemMessage(MoveEarthMessage.success(Component.translatable(
                    "message.moveearth_addtional.prisoner.voluntarily_released")));
            sendSnapshot(captive, false, null);
        }
        actor.sendSystemMessage(MoveEarthMessage.success(Component.translatable(
                "message.moveearth_addtional.prisoner.release_success", playerName(actor.server, targetId))));
        PacketDistributor.sendToPlayer(actor, new S2C_PrisonerActionResultPacket(true,
                "screen.moveearth_addtional.prisoner.action.released"));
    }

    private static void transferEscort(ServerPlayer actor, UUID newCaptorId) {
        PrisonerSavedData data = PrisonerSavedData.get(actor.server);
        PrisonerSavedData.Custody custody = data.custodyByCaptor(actor.getUUID()).orElse(null);
        ServerPlayer newCaptor = newCaptorId == null ? null : actor.server.getPlayerList().getPlayer(newCaptorId);
        NationSavedData nations = NationSavedData.get(actor.server);
        UUID candidateHome = newCaptorId == null ? null : nations.nationIdFor(newCaptorId).orElse(null);
        boolean sameNation = custody != null && newCaptorId != null && custody.holdingNation().equals(
                operationalNation(actor.server, newCaptorId, candidateHome));
        if (!PrisonerTransferPolicy.allowed(custody != null, newCaptor != null, newCaptor != actor,
                newCaptor != null && newCaptor.level() == actor.level(),
                newCaptor == null ? Double.MAX_VALUE : actor.distanceToSqr(newCaptor),
                newCaptor != null && newCaptor.isAlive(), newCaptor != null && newCaptor.isShiftKeyDown(),
                sameNation, newCaptor != null && isMovementRestricted(newCaptor),
                newCaptorId != null && data.custodyByCaptor(newCaptorId).isPresent())
                || !data.transferCustody(custody.playerId(), actor.getUUID(), newCaptorId)) {
            sendFailure(actor, "message.moveearth_addtional.prisoner.transfer_invalid");
            return;
        }
        actor.sendSystemMessage(MoveEarthMessage.success(Component.translatable(
                "message.moveearth_addtional.prisoner.transfer_success", newCaptor.getGameProfile().getName())));
        PacketDistributor.sendToPlayer(actor, new S2C_PrisonerActionResultPacket(true,
                "screen.moveearth_addtional.prisoner.action.transferred"));
        newCaptor.sendSystemMessage(MoveEarthMessage.warning(Component.translatable(
                "message.moveearth_addtional.prisoner.transfer_received",
                playerName(actor.server, custody.playerId()), formatTicks(custody.remainingTicks()))));
        ServerPlayer captive = actor.server.getPlayerList().getPlayer(custody.playerId());
        if (captive != null) captive.sendSystemMessage(MoveEarthMessage.info(Component.translatable(
                "message.moveearth_addtional.prisoner.transfer_captive", newCaptor.getGameProfile().getName())));
        sendSnapshot(newCaptor, false, null);
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
            CombatTagSavedData.get(server).consume(release.playerId());
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
            boolean conflictIsNation = NationSavedData.get(server).nation(window.conflictNation()).isPresent();
            boolean activeConflict = conflictIsNation
                    ? SiegeSavedData.get(server).hasConflictBetween(window.conflictNation(), window.holdingNation())
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
                && !player.level().isClientSide()) {
            openIntakeScreen(player, event.getPos());
            event.setCanceled(true);
            return;
        }
        if (isCaptive(player)) event.setCanceled(true);
    }

    public static boolean isRestrained(ServerPlayer player) {
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
        PrisonerSavedData.Prisoner prisoner = data.prisoner(player.getUUID()).orElse(null);
        if (prisoner != null) {
            jailDestination(player.server, prisoner).ifPresent(destination -> teleport(player, destination));
            player.sendSystemMessage(MoveEarthMessage.warning(Component.translatable(
                    "message.moveearth_addtional.prisoner.imprisoned_captive",
                    nationName(player.server, prisoner.holdingNation()), formatTicks(prisoner.remainingTicks()))));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PrisonerSavedData data = PrisonerSavedData.get(player.server);
        PrisonerSavedData.Custody escorted = data.custodyByCaptor(player.getUUID()).orElse(null);
        if (escorted != null) {
            data.releaseCustody(escorted.playerId());
            CombatTagService.releaseBody(player.server, escorted.playerId(), true);
            CombatTagSavedData.get(player.server).consume(escorted.playerId());
            ServerPlayer captive = player.server.getPlayerList().getPlayer(escorted.playerId());
            if (captive != null) captive.sendSystemMessage(MoveEarthMessage.info(Component.translatable(
                    "message.moveearth_addtional.prisoner.escort_released_logout")));
        }
        ATTEMPTS.remove(player.getUUID());
    }

    public static boolean canReturnAll(MinecraftServer server, UUID firstNation, UUID secondNation) {
        NationSavedData nations = NationSavedData.get(server);
        PrisonerSavedData data = PrisonerSavedData.get(server);
        boolean heldReady = data.between(firstNation, secondNation).stream()
                .allMatch(prisoner -> nations.nation(prisoner.homeNation()).isPresent()
                        && nations.nation(prisoner.holdingNation()).isPresent()
                        && releaseDestination(server, prisoner.homeNation()).isPresent());
        return heldReady && data.custodyRecords().stream()
                .filter(custody -> sameConflictPair(custody, firstNation, secondNation))
                .allMatch(custody -> releaseDestination(server, custody.homeNation()).isPresent());
    }

    public static int returnAll(MinecraftServer server, UUID firstNation, UUID secondNation) {
        ATTEMPTS.values().removeIf(attempt -> PrisonerPairPolicy.matches(
                attempt.captiveConflictNation, attempt.captorNation, firstNation, secondNation));
        PrisonerSavedData data = PrisonerSavedData.get(server);
        List<PrisonerSavedData.Prisoner> released = data.releaseBetween(firstNation, secondNation);
        int custodyReleased = 0;
        for (PrisonerSavedData.Prisoner prisoner : released) {
            ServerPlayer online = server.getPlayerList().getPlayer(prisoner.playerId());
            if (online != null) {
                releaseDestination(server, prisoner.homeNation()).ifPresent(destination -> teleport(online, destination));
                data.acknowledgeRelease(prisoner.playerId());
            }
        }
        for (PrisonerSavedData.Custody custody : data.custodyRecords()) {
            if (sameConflictPair(custody, firstNation, secondNation)) {
                data.releaseCustodyToHome(custody.playerId());
                CombatTagService.releaseBody(server, custody.playerId(), true);
                CombatTagSavedData.get(server).consume(custody.playerId());
                ServerPlayer online = server.getPlayerList().getPlayer(custody.playerId());
                if (online != null) {
                    releaseDestination(server, custody.homeNation()).ifPresent(destination -> teleport(online, destination));
                    data.acknowledgeRelease(custody.playerId());
                    online.sendSystemMessage(MoveEarthMessage.success(Component.translatable(
                            "message.moveearth_addtional.prisoner.released")));
                }
                custodyReleased++;
            }
        }
        return released.size() + custodyReleased;
    }

    private static boolean sameConflictPair(PrisonerSavedData.Custody custody,
                                            UUID firstNation, UUID secondNation) {
        return (custody.conflictNation().equals(firstNation) && custody.holdingNation().equals(secondNation))
                || (custody.conflictNation().equals(secondNation) && custody.holdingNation().equals(firstNation));
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

    private static String restraintCancellationKey(ServerPlayer captor, Entity target,
                                                   RestraintAttempt attempt) {
        if (target == null) return "message.moveearth_addtional.prisoner.restraint_cancelled.target_missing";
        if (!captor.getMainHandItem().is(ModItems.RESTRAINTS.get())) {
            return "message.moveearth_addtional.prisoner.restraint_cancelled.tool_changed";
        }
        if (captor.distanceToSqr(target) > 16.0D) {
            return "message.moveearth_addtional.prisoner.restraint_cancelled.too_far";
        }
        if (!isDowned(captor.server, target, attempt.targetId)) {
            return "message.moveearth_addtional.prisoner.restraint_cancelled.target_recovered";
        }
        // The Siege was checked when restraint started. Its timer can roll over while the
        // captor is physically securing the downed player; that must not erase this attempt.
        return null;
    }

    private static String escortCancellationKey(ServerPlayer captor, Entity captive,
                                                PrisonerSavedData.Custody custody) {
        if (captive == null) return "message.moveearth_addtional.prisoner.escort_cancelled.captive_missing";
        if (captive.level() != captor.level()) {
            return "message.moveearth_addtional.prisoner.escort_cancelled.dimension";
        }
        if (captor.distanceToSqr(captive) > Math.pow(S2TerritoryConfig.escortMaxDistance(), 2.0D)) {
            return "message.moveearth_addtional.prisoner.escort_cancelled.too_far";
        }
        if (!captor.isAlive() || CompatEventHandler.isPlayerDown(captor)) {
            return "message.moveearth_addtional.prisoner.escort_cancelled.captor_incapacitated";
        }
        // Custody survives a Siege timer rollover. Peace/settlement explicitly returns captives.
        return null;
    }

    private static void cancelCustody(ServerPlayer captor, Entity captive,
                                      PrisonerSavedData.Custody custody, PrisonerSavedData data,
                                      String reasonKey) {
        data.releaseCustody(custody.playerId());
        CombatTagService.releaseBody(captor.server, custody.playerId(), true);
        CombatTagSavedData.get(captor.server).consume(custody.playerId());
        captor.sendSystemMessage(MoveEarthMessage.warning(Component.translatable(reasonKey)));
        if (captive instanceof ServerPlayer player) player.sendSystemMessage(MoveEarthMessage.info(
                Component.translatable("message.moveearth_addtional.prisoner.escort_released_captive")));
    }

    private static void sendFailure(ServerPlayer player, String translationKey) {
        player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(translationKey)));
        PacketDistributor.sendToPlayer(player, new S2C_PrisonerActionResultPacket(false, translationKey));
    }

    private static String nationName(MinecraftServer server, UUID nationId) {
        return NationSavedData.get(server).nation(nationId).map(NationSavedData.Nation::name)
                .orElseGet(() -> playerName(server, nationId));
    }

    private static UUID operationalNation(MinecraftServer server, UUID playerId, UUID fallback) {
        return SiegeParticipationSavedData.get(server).forPlayer(playerId)
                .map(SiegeParticipationSavedData.Participation::combatNation).orElse(fallback);
    }

    private static String playerName(MinecraftServer server, UUID playerId) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) return online.getGameProfile().getName();
        for (NationSavedData.Nation nation : NationSavedData.get(server).nations().values()) {
            NationSavedData.Member member = nation.members().get(playerId);
            if (member != null && !member.lastKnownName().isBlank()) return member.lastKnownName();
        }
        return playerId.toString().substring(0, 8);
    }

    private static Vec3 safeEscortPosition(ServerPlayer captor, Entity captive) {
        ServerLevel level = captor.serverLevel();
        double yaw = Math.toRadians(captor.getYRot());
        double backX = -Math.sin(yaw);
        double backZ = Math.cos(yaw);
        double sideX = Math.cos(yaw);
        double sideZ = Math.sin(yaw);
        double[][] offsets = {
                {backX * 1.5D, 0.0D, backZ * 1.5D},
                {backX * 1.5D + sideX, 0.0D, backZ * 1.5D + sideZ},
                {backX * 1.5D - sideX, 0.0D, backZ * 1.5D - sideZ},
                {backX * 1.5D, 1.0D, backZ * 1.5D},
                {backX * 1.5D, -1.0D, backZ * 1.5D}
        };
        for (double[] offset : offsets) {
            Vec3 candidate = new Vec3(captor.getX() + offset[0], captor.getY() + offset[1],
                    captor.getZ() + offset[2]);
            BlockPos feet = BlockPos.containing(candidate);
            if (!level.hasChunkAt(feet) || !level.getFluidState(feet).isEmpty()) continue;
            AABB moved = captive.getBoundingBox().move(candidate.x - captive.getX(),
                    candidate.y - captive.getY(), candidate.z - captive.getZ());
            if (level.noCollision(captive, moved)) return candidate;
        }
        return null;
    }

    private static UUID captiveId(Entity entity) {
        if (entity instanceof ServerPlayer player) return player.getUUID();
        return entity instanceof ArmorStand body && body.getPersistentData().hasUUID(CombatTagService.BODY_OWNER)
                ? body.getPersistentData().getUUID(CombatTagService.BODY_OWNER) : null;
    }

    public static Entity findCustodyEntity(MinecraftServer server, UUID playerId) {
        return findCaptiveEntity(server, playerId);
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

    private record RestraintAttempt(UUID targetId, int startedTick, UUID captorNation,
                                    UUID captiveNation, UUID captiveConflictNation,
                                    UUID siegeId, UUID contractId) { }
    private record Destination(ServerLevel level, BlockPos pos) { }
}
