package com.ruskserver.moveearth_addtional.s2.siege;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.ServerSchedule;
import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.ruskserver.moveearth_addtional.s2.territory.TerritoryCoreHealthService;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementService;
import com.ruskserver.moveearth_addtional.s2.recovery.RecoveryService;
import com.ruskserver.moveearth_addtional.s2.dispatch.DispatchContractService;
import com.ruskserver.moveearth_addtional.s2.dispatch.SiegeAttributionService;
import com.ruskserver.moveearth_addtional.s2.dispatch.DispatchContractSavedData;

/** Resolves destructive actions to nation/core Siege state. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SiegeService {
    private static final int MAX_RECENT_LOGS = 65_536;
    private static final Map<LogKey, Long> RECENT_LOGS = new HashMap<>();

    private SiegeService() { }

    public static SiegeSavedData.AttemptResult recordAttack(ServerPlayer attacker, ServerLevel level,
                                                             BlockPos target, boolean effectiveDamage) {
        if (attacker == null) return new SiegeSavedData.AttemptResult(SiegeSavedData.AttemptStatus.IGNORED, null);
        NationSavedData nations = NationSavedData.get(level.getServer());
        UUID attackerNation = SiegeAttributionService.nationForTarget(attacker, level, target);
        return recordAttack(new AttackAttribution(attackerNation, attacker.getUUID(), "player"),
                level, target, effectiveDamage);
    }

    public static SiegeSavedData.AttemptResult recordAttack(AttackAttribution attribution, ServerLevel level,
                                                             BlockPos target, boolean effectiveDamage) {
        if (attribution == null || (attribution.nationId() == null && attribution.actorId() == null)) {
            return new SiegeSavedData.AttemptResult(SiegeSavedData.AttemptStatus.IGNORED, null);
        }
        NationSavedData nations = NationSavedData.get(level.getServer());
        TerritorySavedData.CoreRecord core = TerritorySavedData.get(level.getServer())
                .controllingCore(level.getServer(), level.dimension().location(), target).orElse(null);
        UUID attackerNation = resolvedNation(attribution, level, target, core);
        if (attribution.frozen() && attribution.contractId() != null && attackerNation == null) {
            return new SiegeSavedData.AttemptResult(SiegeSavedData.AttemptStatus.IGNORED, null);
        }
        SiegeSavedData siegeData = SiegeSavedData.get(level.getServer());
        boolean continuesIndividual = core != null && siegeData.hasActiveIndividualAttack(
                attribution.actorId(), core.id());
        SiegeAttackerPolicy.Identity identity = SiegeAttackerPolicy.resolve(
                attackerNation, attribution.actorId(), continuesIndividual);
        boolean individualAttacker = identity != null && identity.individual();
        UUID attackerId = identity == null ? null : identity.id();
        if (attackerId == null || core == null || siegeData.isCoreFallen(core.id())
                || (!individualAttacker && (attackerNation.equals(core.nationId())
                || nations.isAllied(attackerNation, core.nationId())))
                || formerNationBinding(level.getServer(), attribution, attackerNation, core.nationId()) != null) {
            return new SiegeSavedData.AttemptResult(SiegeSavedData.AttemptStatus.IGNORED, null);
        }
        if (!individualAttacker) {
            var recovery = com.ruskserver.moveearth_addtional.s2.recovery.NationRecoverySavedData
                    .get(level.getServer()).eligibleForNation(attackerNation,
                            com.ruskserver.moveearth_addtional.s2.time.OpenTimeService.now(level.getServer())).orElse(null);
            if (recovery != null && !com.ruskserver.moveearth_addtional.s2.recovery.NationRecoverySavedData
                    .get(level.getServer()).protectionWaived(recovery.id())) {
                return new SiegeSavedData.AttemptResult(
                        SiegeSavedData.AttemptStatus.RECOVERY_PROTECTED, null);
            }
        }

        long gameTick = level.getServer().overworld().getGameTime();
        UUID logActor = attribution.actorId() == null ? attackerId : attribution.actorId();
        LogKey logKey = new LogKey(logActor, level.dimension().location().toString(), target.asLong());
        boolean shouldLog = gameTick >= RECENT_LOGS.getOrDefault(logKey, Long.MIN_VALUE)
                + S2TerritoryConfig.siegeDuplicateLogTicks();
        if (shouldLog) {
            RECENT_LOGS.put(logKey, gameTick);
            Moveearth_addtional.LOGGER.info(
                    "Siege attempt: actor={} source={} attackerNation={} defenderNation={} target={} effective={}",
                    attribution.actorId(), attribution.source(), attackerNation, core.nationId(), target, effectiveDamage);
        }
        boolean offlineDefenseAllowed = OfflineDefenseService.baseDivisor(level, core) > 1;
        OfflineDefenseDaySavedData.get(level.getServer())
                .observe(core.id(), OfflineDefenseDaySavedData.today(), offlineDefenseAllowed);
        SiegeSavedData.AttemptResult result = siegeData.registerAttempt(
                attackerId, individualAttacker, core, effectiveDamage, offlineDefenseAllowed);
        if (result.siege() != null) {
            RecoveryService.recordBattleWalls(level, result.siege(), core);
            DispatchContractService.bindEligible(level.getServer(), result.siege());
            ServerPlayer participant = attribution.actorId() == null ? null
                    : level.getServer().getPlayerList().getPlayer(attribution.actorId());
            if (participant != null) {
                com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(participant,
                        com.ruskserver.moveearth_addtional.advancement.ModCriteria.SIEGE_PARTICIPATED);
                if (com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology
                        .at(level, participant.blockPosition()).isPresent()) {
                    com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(participant,
                            com.ruskserver.moveearth_addtional.advancement.ModCriteria.MOBILE_FORCE_PARTICIPATED);
                }
            }
        }
        notifyTransition(level.getServer(), nations, result);
        if (effectiveDamage && core.health() == 0 && result.siege() != null) {
            SiegeSavedData.FallenResult fallen = siegeData.markFallen(result.siege(), core);
            if (fallen.created()) {
                TerritorySavedData.get(level.getServer()).markCoreFallen(core.id(), false)
                        .ifPresent(updated -> TerritoryCoreHealthService.syncCore(level.getServer(), updated));
                broadcastFall(level.getServer(), nations, fallen.fallen());
            }
        }
        return result;
    }

    public static ServerPlayer attributablePlayer(Entity source) {
        if (source instanceof ServerPlayer player) return player;
        Entity owner = source instanceof Projectile projectile ? projectile.getOwner()
                : source instanceof PrimedTnt tnt ? tnt.getOwner() : null;
        return owner instanceof ServerPlayer player ? player : null;
    }

    public static boolean peaceTruceBlocks(ServerPlayer attacker, ServerLevel level, BlockPos target) {
        if (attacker == null) return false;
        NationSavedData nations = NationSavedData.get(level.getServer());
        UUID attackerNation = SiegeAttributionService.nationForTarget(attacker, level, target);
        return peaceTruceBlocks(new AttackAttribution(attackerNation, attacker.getUUID(), "player"), level, target);
    }

    /**
     * The gate every damage path checks first. Besides a peace truce between the attacker's nation
     * and the defender, it blocks a player whose membership cooldown still binds them to a former
     * nation that has peace or an alliance with the defender, and an attacker whose failed Siege
     * locked them out of this core.
     */
    public static boolean peaceTruceBlocks(AttackAttribution attribution, ServerLevel level, BlockPos target) {
        if (attribution == null || attribution.nationId() == null && attribution.actorId() == null) return false;
        MinecraftServer server = level.getServer();
        TerritorySavedData.CoreRecord core = TerritorySavedData.get(server)
                .controllingCore(server, level.dimension().location(), target).orElse(null);
        if (core == null) return false;
        UUID attackerNation = resolvedNation(attribution, level, target, core);
        UUID defenderNation = core.nationId();
        SiegeSavedData siegeData = SiegeSavedData.get(server);
        if (attackerNation != null && !attackerNation.equals(defenderNation)
                && siegeData.isPeaceTruceActive(attackerNation, defenderNation)) return true;
        UUID formerNation = formerNationBinding(server, attribution, attackerNation, defenderNation);
        if (formerNation != null) {
            notifyRefusal(server, attribution.actorId(), RefusalKind.FORMER_NATION, Component.translatable(
                    "message.moveearth_addtional.siege.former_nation_bound",
                    NationSavedData.get(server).nation(formerNation).map(NationSavedData.Nation::name).orElse("?"),
                    com.ruskserver.moveearth_addtional.s2.nation.MembershipCooldownService.duration(
                            NationSavedData.get(server).formerNationBindingRemaining(attribution.actorId(),
                                    com.ruskserver.moveearth_addtional.s2.time.OpenTimeService.now(server)))));
            return true;
        }
        // Same identity recordAttack would register, so the lock matches the Siege that failed.
        if (attribution.frozen() && attribution.contractId() != null && attackerNation == null) return false;
        SiegeAttackerPolicy.Identity identity = SiegeAttackerPolicy.resolve(attackerNation, attribution.actorId(),
                siegeData.hasActiveIndividualAttack(attribution.actorId(), core.id()));
        if (identity == null || (!identity.individual() && identity.id().equals(defenderNation))) return false;
        long locked = siegeData.failedAttackCooldownTicks(identity.id(), identity.individual(), core.id());
        if (locked > 0L) {
            notifyRefusal(server, attribution.actorId(), RefusalKind.FAILED_COOLDOWN, Component.translatable(
                    "message.moveearth_addtional.siege.failed_cooldown",
                    com.ruskserver.moveearth_addtional.s2.nation.MembershipCooldownPolicy.remainingMinutes(locked)));
            return true;
        }
        return false;
    }

    /**
     * The former nation that still binds a player in their membership cooldown, when that nation has
     * peace or an alliance with the defender; null when nothing binds the attack.
     */
    private static UUID formerNationBinding(MinecraftServer server, AttackAttribution attribution,
                                            UUID attackerNation, UUID defenderNation) {
        if (attribution == null || attribution.actorId() == null || defenderNation == null
                || defenderNation.equals(attackerNation)) return null;
        NationSavedData nations = NationSavedData.get(server);
        UUID formerNation = nations.cooldownFormerNation(attribution.actorId(),
                com.ruskserver.moveearth_addtional.s2.time.OpenTimeService.now(server)).orElse(null);
        if (formerNation == null) return null;
        boolean blocked = com.ruskserver.moveearth_addtional.s2.nation.MembershipCooldownPolicy.formerNationBlocks(
                formerNation, defenderNation, nations.isAllied(formerNation, defenderNation),
                SiegeSavedData.get(server).isPeaceTruceActive(formerNation, defenderNation));
        return blocked ? formerNation : null;
    }

    private static void notifyRefusal(MinecraftServer server, UUID actorId, RefusalKind kind, Component body) {
        if (actorId == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(actorId);
        if (player == null) return;
        long now = server.overworld().getGameTime();
        RefusalKey key = new RefusalKey(actorId, kind);
        Long previous = LAST_REFUSALS.get(key);
        if (previous != null && now - previous < REFUSAL_NOTICE_TICKS) return;
        LAST_REFUSALS.put(key, now);
        player.sendSystemMessage(MoveEarthMessage.warning(body));
    }

    private static final long REFUSAL_NOTICE_TICKS = 100L;
    private static final Map<RefusalKey, Long> LAST_REFUSALS = new HashMap<>();

    private enum RefusalKind { FORMER_NATION, FAILED_COOLDOWN }

    private record RefusalKey(UUID actorId, RefusalKind kind) { }

    private static UUID resolvedNation(AttackAttribution attribution, ServerLevel level, BlockPos target,
                                       TerritorySavedData.CoreRecord core) {
        if (attribution.frozen()) {
            if (attribution.contractId() == null) return attribution.nationId();
            DispatchContractSavedData.Contract contract = DispatchContractSavedData.get(level.getServer())
                    .byId(attribution.contractId()).orElse(null);
            return contract != null && core != null && contract.targetCoreId().equals(core.id())
                    && attribution.siegeId() != null && attribution.siegeId().equals(contract.siegeId())
                    ? attribution.nationId() : null;
        }
        return attribution.actorId() == null ? attribution.nationId()
                : SiegeAttributionService.combatNationForTarget(level.getServer(), attribution.actorId(), level, target)
                .orElse(attribution.nationId());
    }

    public record AttackAttribution(UUID nationId, UUID actorId, String source,
                                    UUID contractId, UUID siegeId, boolean frozen) {
        public AttackAttribution(UUID nationId, UUID actorId, String source) {
            this(nationId, actorId, source, null, null, false);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().overworld().getGameTime() % 20L != 7L) return;
        if (event.getServer().isDedicatedServer() && !ServerSchedule.isOpenNow()) return;
        long now = event.getServer().overworld().getGameTime();
        long retention = Math.max(20L, S2TerritoryConfig.siegeDuplicateLogTicks() * 4L);
        RECENT_LOGS.entrySet().removeIf(entry -> now - entry.getValue() > retention);
        if (RECENT_LOGS.size() > MAX_RECENT_LOGS) RECENT_LOGS.clear();
        SiegeSavedData siegeData = SiegeSavedData.get(event.getServer());
        SiegeSavedData.TickResult result = siegeData.advance(20L,
                siege -> SiegePresence.contested(event.getServer(), siege));
        PeaceSavedData.get(event.getServer()).advance(20L);
        TerritorySavedData.get(event.getServer()).advanceVaultCooldowns(20L);
        NationSavedData nations = NationSavedData.get(event.getServer());
        result.initialExpired().forEach(siege -> notifyParties(event.getServer(), nations, siege,
                Component.translatable("message.moveearth_addtional.siege.initial_expired")));
        result.rollingExpired().forEach(siege -> notifyParties(event.getServer(), nations, siege,
                Component.translatable("message.moveearth_addtional.siege.ended")));
        result.rollingExpired().forEach(siege -> publishSiege(event.getServer(), siege,
                NationNotificationSavedData.EventType.SIEGE_ENDED, "timer_expired"));
        SiegeSavedData.FallenTickResult fallen = siegeData.advanceFallen(
                20L, record -> counterPresence(event.getServer(), nations, record));
        fallen.counterStarted().forEach(record -> publishFallen(event.getServer(), record,
                NationNotificationSavedData.EventType.COUNTEROFFENSIVE_STARTED, "defender_present"));
        fallen.counterFailed().forEach(record -> publishFallen(event.getServer(), record,
                NationNotificationSavedData.EventType.COUNTEROFFENSIVE_FAILED, "progress_lost"));
        fallen.stageChanged().forEach(record -> {
            notifyFallenParties(event.getServer(), nations, record,
                    Component.translatable("message.moveearth_addtional.siege.fall_stage." + record.stage()));
            syncFallVisuals(event.getServer(), record);
        });
        fallen.recovered().forEach(record -> {
            SiegeLootSavedData.get(event.getServer()).revoke(record.siegeId());
            recordWar(com.ruskserver.moveearth_addtional.analytics.event.GameEventType.COUNTER_CAPTURED, record.defenderNation(), record.dimension(), record.corePos(),
                    record.radius(), warDetail(record.attackerNation(), record.individualAttacker(),
                            "core=" + record.coreType()));
            TerritorySavedData.get(event.getServer())
                .recoverCore(record.coreId(), S2TerritoryConfig.siegeCounterRecoveryPercent())
                .ifPresent(core -> {
                    TerritoryCoreHealthService.syncCore(event.getServer(), core);
                    syncFallVisuals(event.getServer(), record);
                    notifyFallenParties(event.getServer(), nations, record,
                            Component.translatable("message.moveearth_addtional.siege.counter_success",
                                    S2TerritoryConfig.siegeCounterRecoveryPercent()));
                    publishFallen(event.getServer(), record,
                            NationNotificationSavedData.EventType.COUNTEROFFENSIVE_SUCCEEDED,
                            Double.toString(S2TerritoryConfig.siegeCounterRecoveryPercent()));
                    publishFallen(event.getServer(), record,
                            NationNotificationSavedData.EventType.SIEGE_ENDED, "counteroffensive_success");
                    com.ruskserver.moveearth_addtional.s2.recovery.WarHistorySavedData.get(event.getServer()).append(
                            com.ruskserver.moveearth_addtional.s2.time.OpenTimeService.now(event.getServer()),
                            com.ruskserver.moveearth_addtional.s2.recovery.WarHistorySavedData.Type.COUNTEROFFENSIVE_SUCCEEDED,
                            com.ruskserver.moveearth_addtional.s2.recovery.WarHistorySavedData.Visibility.PUBLIC,
                            record.defenderNation(), record.individualAttacker() ? null : record.attackerNation(),
                            record.siegeId(), java.util.List.of());
                });
        });
        fallen.finalized().forEach(record -> {
            if (record.captureTicks() > 0L) publishFallen(event.getServer(), record,
                    NationNotificationSavedData.EventType.COUNTEROFFENSIVE_FAILED, "settlement_timer_expired");
            finalizeFall(event.getServer(), nations, siegeData, record);
        });
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RECENT_LOGS.clear();
        LAST_REFUSALS.clear();
    }

    private static void notifyTransition(MinecraftServer server, NationSavedData nations,
                                         SiegeSavedData.AttemptResult result) {
        if (result.siege() == null) return;
        SiegeSavedData.SiegeRecord siege = result.siege();
        java.util.List<UUID> parties = notificationParties(siege.attackerNation(),
                siege.individualAttacker(), siege.defenderNation());
        if (result.status() == SiegeSavedData.AttemptStatus.INITIAL_STARTED) {
            recordWar(com.ruskserver.moveearth_addtional.analytics.event.GameEventType.SIEGE_INITIAL, siege.defenderNation(), siege.dimension(), siege.corePos(), 0L,
                    warDetail(siege.attackerNation(), siege.individualAttacker(), null));
            String remaining = formatTicks(S2TerritoryConfig.siegeInitialLockTicks());
            // Each side reads the lock from where it stands: the attacker hit enemy land,
            // the defender's own land was hit.
            Component attackerBody = Component.translatable(
                    "message.moveearth_addtional.siege.initial_started", remaining);
            Component defenderBody = Component.translatable(
                    "message.moveearth_addtional.siege.initial_started.defender", remaining,
                    siege.corePos().getX(), siege.corePos().getY(), siege.corePos().getZ());
            if (siege.individualAttacker()) {
                ServerPlayer attacker = server.getPlayerList().getPlayer(siege.attackerNation());
                if (attacker != null) attacker.sendSystemMessage(MoveEarthMessage.warning(attackerBody));
            } else {
                NationNotificationService.publish(server, java.util.List.of(siege.attackerNation()),
                        NationNotificationSavedData.EventType.SIEGE_INITIAL_STARTED,
                        siege.dimension(), siege.corePos(), attackerBody,
                        java.util.List.of(remaining));
            }
            NationNotificationService.publish(server, java.util.List.of(siege.defenderNation()),
                    NationNotificationSavedData.EventType.SIEGE_INITIAL_STARTED,
                    siege.dimension(), siege.corePos(), defenderBody,
                    java.util.List.of(remaining));
        } else if (result.status() == SiegeSavedData.AttemptStatus.ROLLING_STARTED) {
            recordWar(com.ruskserver.moveearth_addtional.analytics.event.GameEventType.SIEGE_ROLLING, siege.defenderNation(), siege.dimension(), siege.corePos(), 0L,
                    warDetail(siege.attackerNation(), siege.individualAttacker(), null));
            NationSavedData.Nation defender = nations.nation(siege.defenderNation()).orElse(null);
            String attackerName = attackerName(server, nations, siege.attackerNation(), siege.individualAttacker());
            String defenderName = defender == null ? "?" : defender.name();
            Component body = Component.translatable(
                    "message.moveearth_addtional.siege.rolling_started", attackerName, defenderName,
                    siege.corePos().getX(), siege.corePos().getY(), siege.corePos().getZ());
            server.getPlayerList().broadcastSystemMessage(MoveEarthMessage.warning(body), false);
            NationNotificationService.publish(server, parties,
                    NationNotificationSavedData.EventType.SIEGE_STARTED,
                    siege.dimension(), siege.corePos(), null,
                    java.util.List.of(attackerName, defenderName));
            com.ruskserver.moveearth_addtional.s2.recovery.WarHistorySavedData.get(server).append(
                    com.ruskserver.moveearth_addtional.s2.time.OpenTimeService.now(server),
                    com.ruskserver.moveearth_addtional.s2.recovery.WarHistorySavedData.Type.SIEGE_STARTED,
                    com.ruskserver.moveearth_addtional.s2.recovery.WarHistorySavedData.Visibility.PUBLIC,
                    siege.defenderNation(), siege.individualAttacker() ? null : siege.attackerNation(),
                    siege.id(), java.util.List.of());
        }
    }

    private static void notifyParties(MinecraftServer server, NationSavedData nations,
                                      SiegeSavedData.SiegeRecord siege, Component body) {
        if (siege.individualAttacker()) {
            ServerPlayer attacker = server.getPlayerList().getPlayer(siege.attackerNation());
            if (attacker != null) attacker.sendSystemMessage(MoveEarthMessage.warning(body));
        }
        for (UUID nationId : notificationParties(siege.attackerNation(),
                siege.individualAttacker(), siege.defenderNation())) {
            NationSavedData.Nation nation = nations.nation(nationId).orElse(null);
            if (nation == null) continue;
            for (UUID memberId : nation.members().keySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(memberId);
                if (player != null) player.sendSystemMessage(MoveEarthMessage.warning(body));
            }
        }
    }

    private static SiegeFallPolicy.Presence counterPresence(MinecraftServer server, NationSavedData nations,
                                                             SiegeSavedData.FallenRecord record) {
        ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, record.dimension()));
        if (level == null) return SiegeFallPolicy.Presence.EMPTY_OR_ATTACKER;
        double radiusSquared = Math.pow(S2TerritoryConfig.siegeCounterRadiusBlocks(), 2.0D);
        boolean defender = false;
        boolean attacker = false;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.isAlive()
                    || com.ruskserver.moveearth_addtional.CompatEventHandler.isPlayerDown(player)
                    || PrisonerService.isRestrained(player)
                    || player.distanceToSqr(record.corePos().getCenter()) > radiusSquared) continue;
            UUID nation = nations.nationIdFor(player.getUUID()).orElse(null);
            if (record.defenderNation().equals(nation)) defender = true;
            else if (record.individualAttacker()
                    ? record.attackerNation().equals(player.getUUID())
                    : record.attackerNation().equals(nation)) attacker = true;
            SiegeParticipationSavedData.Participation participation = SiegeParticipationSavedData.get(server)
                    .forPlayer(player.getUUID()).filter(value -> value.siegeId().equals(record.siegeId()))
                    .orElse(null);
            if (participation != null && !record.individualAttacker()
                    && record.attackerNation().equals(participation.combatNation())) {
                attacker = true;
            }
        }
        return SiegeFallPolicy.presence(defender, attacker);
    }

    static void broadcastFall(MinecraftServer server, NationSavedData nations,
                                      SiegeSavedData.FallenRecord fallen) {
        NationSavedData.Nation defender = nations.nation(fallen.defenderNation()).orElse(null);
        recordWar(com.ruskserver.moveearth_addtional.analytics.event.GameEventType.CORE_FALLEN, fallen.defenderNation(), fallen.dimension(), fallen.corePos(),
                fallen.radius(), warDetail(fallen.attackerNation(), fallen.individualAttacker(),
                        "core=" + fallen.coreType()));
        server.getPlayerList().broadcastSystemMessage(MoveEarthMessage.warning(Component.translatable(
                "message.moveearth_addtional.siege.core_fallen",
                defender == null ? "?" : defender.name(),
                attackerName(server, nations, fallen.attackerNation(), fallen.individualAttacker()),
                fallen.corePos().getX(), fallen.corePos().getY(), fallen.corePos().getZ())), false);
        publishFallen(server, fallen, NationNotificationSavedData.EventType.CORE_FALLEN,
                defender == null ? "?" : defender.name());
        // The fall is not final yet; tell the defenders how to take the core back.
        Component counter = Component.translatable("message.moveearth_addtional.siege.counteroffensive_hint",
                S2TerritoryConfig.siegeCounterRadiusBlocks(),
                formatTicks(S2TerritoryConfig.siegeCounterCaptureTicks()),
                fallen.corePos().getX(), fallen.corePos().getY(), fallen.corePos().getZ());
        for (ServerPlayer member : server.getPlayerList().getPlayers()) {
            if (nations.nationIdFor(member.getUUID()).filter(fallen.defenderNation()::equals).isPresent()) {
                member.sendSystemMessage(MoveEarthMessage.info(counter));
            }
        }
        com.ruskserver.moveearth_addtional.s2.recovery.WarHistorySavedData.get(server).append(
                com.ruskserver.moveearth_addtional.s2.time.OpenTimeService.now(server),
                com.ruskserver.moveearth_addtional.s2.recovery.WarHistorySavedData.Type.CORE_FALLEN,
                com.ruskserver.moveearth_addtional.s2.recovery.WarHistorySavedData.Visibility.PUBLIC,
                fallen.defenderNation(), fallen.individualAttacker() ? null : fallen.attackerNation(),
                fallen.siegeId(), java.util.List.of());
    }

    private static void notifyFallenParties(MinecraftServer server, NationSavedData nations,
                                            SiegeSavedData.FallenRecord fallen, Component body) {
        if (fallen.individualAttacker()) {
            ServerPlayer attacker = server.getPlayerList().getPlayer(fallen.attackerNation());
            if (attacker != null) attacker.sendSystemMessage(MoveEarthMessage.warning(body));
        }
        for (UUID nationId : notificationParties(fallen.attackerNation(),
                fallen.individualAttacker(), fallen.defenderNation())) {
            NationSavedData.Nation nation = nations.nation(nationId).orElse(null);
            if (nation == null) continue;
            for (UUID memberId : nation.members().keySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(memberId);
                if (player != null) player.sendSystemMessage(MoveEarthMessage.warning(body));
            }
        }
    }

    static void syncFallVisuals(MinecraftServer server, SiegeSavedData.FallenRecord record) {
        ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, record.dimension()));
        if (level == null) return;
        for (ServerPlayer player : level.players()) ReinforcementService.sendScan(player,
                ReinforcementService.SCAN_RADIUS);
    }

    static void finalizeFall(MinecraftServer server, NationSavedData nations,
                                     SiegeSavedData siegeData, SiegeSavedData.FallenRecord record) {
        TerritorySavedData territories = TerritorySavedData.get(server);
        SiegeLootSavedData.get(server).open(record,
                com.ruskserver.moveearth_addtional.s2.time.OpenTimeService.now(server));
        ServerLevel recoveryLevel = server.getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, record.dimension()));
        int recoveryWallTarget = record.coreType() == TerritorySavedData.CoreType.CAPITAL
                && recoveryLevel != null && recoveryLevel.hasChunkAt(record.corePos())
                ? RecoveryService.countHealthyWalls(recoveryLevel, record.corePos(), record.radius(),
                com.ruskserver.moveearth_addtional.config.RecoveryDispatchConfig.wallTargetCap()) : 0;
        territories.markCoreFallen(record.coreId(), true);
        TerritorySavedData.SettlementResult settlement = territories.settleFallenCore(
                record.coreId(), record.individualAttacker() ? null : record.attackerNation(),
                S2TerritoryConfig.siegeSettlementRecoveryPercent()).orElse(null);
        if (settlement == null) {
            // Do not retain an unresolvable finalized record forever (for example after admin repair).
            siegeData.resolveFallen(record,
                    record.coreType() == TerritorySavedData.CoreType.CAPITAL,
                    S2TerritoryConfig.siegeSettlementTruceTicks());
            syncFallVisuals(server, record);
            return;
        }
        boolean capital = settlement.outcome()
                == com.ruskserver.moveearth_addtional.s2.territory.TerritoryFallSettlementPolicy.Outcome.CAPITAL_REBUILDING;
        siegeData.resolveFallen(record, capital, S2TerritoryConfig.siegeSettlementTruceTicks());
        recordWar(com.ruskserver.moveearth_addtional.analytics.event.GameEventType.FALL_SETTLED, record.defenderNation(), record.dimension(), record.corePos(),
                settlement.core().radius(), warDetail(record.attackerNation(), record.individualAttacker(),
                        "core=" + record.coreType() + ";outcome=" + settlement.outcome()));
        TerritoryCoreHealthService.syncCore(server, settlement.core());
        syncFallVisuals(server, record);
        if (capital) {
            RecoveryService.openEpisode(server, record.siegeId(), record.defenderNation(),
                    record.attackerNation(), record.individualAttacker(), record.coreId(),
                    record.dimension(), record.corePos(), record.radius(), recoveryWallTarget);
        }

        NationSavedData.Nation defender = nations.nation(record.defenderNation()).orElse(null);
        String attackerName = attackerName(server, nations, record.attackerNation(), record.individualAttacker());
        String defenderName = defender == null ? "?" : defender.name();
        Component body = switch (settlement.outcome()) {
            case CAPITAL_REBUILDING -> Component.translatable(
                    "message.moveearth_addtional.siege.capital_rebuilding", defenderName,
                    formatTicks(S2TerritoryConfig.siegeSettlementTruceTicks()));
            case OUTPOST_OCCUPIED -> Component.translatable(
                    "message.moveearth_addtional.siege.outpost_occupied", defenderName, attackerName,
                    settlement.core().radius(), record.corePos().getX(), record.corePos().getY(),
                    record.corePos().getZ(), formatTicks(S2TerritoryConfig.siegeSettlementTruceTicks()));
            case OUTPOST_NEUTRALIZED -> Component.translatable(
                    "message.moveearth_addtional.siege.outpost_neutralized", defenderName,
                    record.corePos().getX(), record.corePos().getY(), record.corePos().getZ());
        };
        server.getPlayerList().broadcastSystemMessage(MoveEarthMessage.warning(body), false);
        java.util.List<UUID> parties = notificationParties(record.attackerNation(),
                record.individualAttacker(), record.defenderNation());
        NationNotificationService.publish(server, parties,
                NationNotificationSavedData.EventType.SIEGE_ENDED,
                record.dimension(), record.corePos(), null, java.util.List.of("settled"));
        if (!record.individualAttacker() && settlement.outcome()
                == com.ruskserver.moveearth_addtional.s2.territory.TerritoryFallSettlementPolicy.Outcome.OUTPOST_OCCUPIED) {
            NationNotificationService.publish(server, java.util.List.of(record.attackerNation()),
                    NationNotificationSavedData.EventType.TERRITORY_OCCUPIED,
                    record.dimension(), record.corePos(), null,
                    java.util.List.of(defenderName, attackerName));
            NationNotificationService.publish(server, java.util.List.of(record.defenderNation()),
                    NationNotificationSavedData.EventType.TERRITORY_LOST,
                    record.dimension(), record.corePos(), null,
                    java.util.List.of(attackerName));
        } else if (settlement.outcome()
                == com.ruskserver.moveearth_addtional.s2.territory.TerritoryFallSettlementPolicy.Outcome.OUTPOST_NEUTRALIZED) {
            NationNotificationService.publish(server, java.util.List.of(record.defenderNation()),
                    NationNotificationSavedData.EventType.TERRITORY_LOST,
                    record.dimension(), record.corePos(), null,
                    java.util.List.of("neutralized"));
        }
    }

    /** War events are filed under the defending nation; the attacker goes in the detail. */
    private static void recordWar(com.ruskserver.moveearth_addtional.analytics.event.GameEventType type, UUID defender, net.minecraft.resources.ResourceLocation dimension,
                                  BlockPos pos, long value, String detail) {
        com.ruskserver.moveearth_addtional.analytics.event.GameEvents.place(type, defender, dimension, pos, value, detail);
    }

    private static String warDetail(UUID attacker, boolean individual, String extra) {
        String detail = "attacker=" + attacker + (individual ? ";individual=true" : "");
        return extra == null ? detail : detail + ";" + extra;
    }

    static void notifySiegeEnded(MinecraftServer server, UUID attacker, UUID defender,
                                 net.minecraft.resources.ResourceLocation dimension,
                                 BlockPos pos, String reason) {
        notifySiegeEnded(server, attacker, false, defender, dimension, pos, reason);
    }

    static void notifySiegeEnded(MinecraftServer server, UUID attacker, boolean individualAttacker,
                                 UUID defender, net.minecraft.resources.ResourceLocation dimension,
                                 BlockPos pos, String reason) {
        NationNotificationService.publish(server, notificationParties(attacker, individualAttacker, defender),
                NationNotificationSavedData.EventType.SIEGE_ENDED, dimension, pos, null,
                java.util.List.of(reason));
        if ("attacker_withdrew".equals(reason)) {
            NationSavedData nations = NationSavedData.get(server);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (nations.nationIdFor(player.getUUID()).filter(defender::equals).isPresent()
                        && SiegeActivityTracker.activeRecently(player)) {
                    com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(player,
                            com.ruskserver.moveearth_addtional.advancement.ModCriteria.TERRITORY_DEFENDED);
                }
            }
        }
    }

    private static void publishSiege(MinecraftServer server, SiegeSavedData.SiegeRecord siege,
                                     NationNotificationSavedData.EventType type, String detail) {
        NationNotificationService.publish(server,
                notificationParties(siege.attackerNation(), siege.individualAttacker(), siege.defenderNation()), type,
                siege.dimension(), siege.corePos(), null, java.util.List.of(detail));
    }

    private static void publishFallen(MinecraftServer server, SiegeSavedData.FallenRecord record,
                                      NationNotificationSavedData.EventType type, String detail) {
        NationNotificationService.publish(server,
                notificationParties(record.attackerNation(), record.individualAttacker(), record.defenderNation()), type,
                record.dimension(), record.corePos(), null, java.util.List.of(detail));
    }

    private static String formatTicks(long ticks) {
        long seconds = Math.max(0L, (ticks + 19L) / 20L);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L);
    }

    public static String attackerName(MinecraftServer server, NationSavedData nations, UUID attackerId,
                               boolean individualAttacker) {
        if (!individualAttacker) return nations.nation(attackerId).map(NationSavedData.Nation::name).orElse("?");
        ServerPlayer online = server.getPlayerList().getPlayer(attackerId);
        if (online != null) return online.getGameProfile().getName();
        return server.getProfileCache() == null ? attackerId.toString().substring(0, 8)
                : server.getProfileCache().get(attackerId).map(profile -> profile.getName())
                .orElse(attackerId.toString().substring(0, 8));
    }

    private static java.util.List<UUID> notificationParties(UUID attackerId, boolean individualAttacker,
                                                             UUID defenderNation) {
        return individualAttacker ? java.util.List.of(defenderNation)
                : java.util.List.of(attackerId, defenderNation);
    }

    private record LogKey(UUID player, String dimension, long pos) { }
}
