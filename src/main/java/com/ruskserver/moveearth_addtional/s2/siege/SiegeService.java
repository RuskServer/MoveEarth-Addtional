package com.ruskserver.moveearth_addtional.s2.siege;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
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
        UUID attackerNation = nations.nationIdFor(attacker.getUUID()).orElse(null);
        return recordAttack(new AttackAttribution(attackerNation, attacker.getUUID(), "player"),
                level, target, effectiveDamage);
    }

    public static SiegeSavedData.AttemptResult recordAttack(AttackAttribution attribution, ServerLevel level,
                                                             BlockPos target, boolean effectiveDamage) {
        if (attribution == null || (attribution.nationId() == null && attribution.actorId() == null)) {
            return new SiegeSavedData.AttemptResult(SiegeSavedData.AttemptStatus.IGNORED, null);
        }
        NationSavedData nations = NationSavedData.get(level.getServer());
        UUID attackerNation = attribution.nationId();
        TerritorySavedData.CoreRecord core = TerritorySavedData.get(level.getServer())
                .controllingCore(level.dimension().location(), target).orElse(null);
        SiegeSavedData siegeData = SiegeSavedData.get(level.getServer());
        boolean continuesIndividual = core != null && siegeData.hasActiveIndividualAttack(
                attribution.actorId(), core.id());
        SiegeAttackerPolicy.Identity identity = SiegeAttackerPolicy.resolve(
                attackerNation, attribution.actorId(), continuesIndividual);
        boolean individualAttacker = identity != null && identity.individual();
        UUID attackerId = identity == null ? null : identity.id();
        if (attackerId == null || core == null || siegeData.isCoreFallen(core.id())
                || (!individualAttacker && (attackerNation.equals(core.nationId())
                || nations.isAllied(attackerNation, core.nationId())))) {
            return new SiegeSavedData.AttemptResult(SiegeSavedData.AttemptStatus.IGNORED, null);
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
        SiegeSavedData.AttemptResult result = siegeData.registerAttempt(
                attackerId, individualAttacker, core, effectiveDamage, offlineDefenseAllowed);
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
        UUID attackerNation = nations.nationIdFor(attacker.getUUID()).orElse(null);
        return peaceTruceBlocks(new AttackAttribution(attackerNation, attacker.getUUID(), "player"), level, target);
    }

    public static boolean peaceTruceBlocks(AttackAttribution attribution, ServerLevel level, BlockPos target) {
        if (attribution == null || attribution.nationId() == null) return false;
        UUID attackerNation = attribution.nationId();
        UUID defenderNation = TerritorySavedData.get(level.getServer())
                .controllingNation(level.dimension().location(), target).orElse(null);
        return attackerNation != null && defenderNation != null
                && !attackerNation.equals(defenderNation)
                && SiegeSavedData.get(level.getServer()).isPeaceTruceActive(
                        attackerNation, defenderNation);
    }

    public record AttackAttribution(UUID nationId, UUID actorId, String source) { }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().overworld().getGameTime() % 20L != 7L) return;
        long now = event.getServer().overworld().getGameTime();
        long retention = Math.max(20L, S2TerritoryConfig.siegeDuplicateLogTicks() * 4L);
        RECENT_LOGS.entrySet().removeIf(entry -> now - entry.getValue() > retention);
        if (RECENT_LOGS.size() > MAX_RECENT_LOGS) RECENT_LOGS.clear();
        SiegeSavedData siegeData = SiegeSavedData.get(event.getServer());
        SiegeSavedData.TickResult result = siegeData.advance(20L);
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
        fallen.recovered().forEach(record -> TerritorySavedData.get(event.getServer())
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
                }));
        fallen.finalized().forEach(record -> {
            if (record.captureTicks() > 0L) publishFallen(event.getServer(), record,
                    NationNotificationSavedData.EventType.COUNTEROFFENSIVE_FAILED, "settlement_timer_expired");
            finalizeFall(event.getServer(), nations, siegeData, record);
        });
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) { RECENT_LOGS.clear(); }

    private static void notifyTransition(MinecraftServer server, NationSavedData nations,
                                         SiegeSavedData.AttemptResult result) {
        if (result.siege() == null) return;
        SiegeSavedData.SiegeRecord siege = result.siege();
        java.util.List<UUID> parties = notificationParties(siege.attackerNation(),
                siege.individualAttacker(), siege.defenderNation());
        if (result.status() == SiegeSavedData.AttemptStatus.INITIAL_STARTED) {
            String remaining = formatTicks(S2TerritoryConfig.siegeInitialLockTicks());
            Component body = Component.translatable(
                    "message.moveearth_addtional.siege.initial_started", remaining);
            if (siege.individualAttacker()) {
                ServerPlayer attacker = server.getPlayerList().getPlayer(siege.attackerNation());
                if (attacker != null) attacker.sendSystemMessage(MoveEarthMessage.warning(body));
            }
            NationNotificationService.publish(server, parties,
                    NationNotificationSavedData.EventType.SIEGE_INITIAL_STARTED,
                    siege.dimension(), siege.corePos(), body,
                    java.util.List.of(remaining));
        } else if (result.status() == SiegeSavedData.AttemptStatus.ROLLING_STARTED) {
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
                    || player.distanceToSqr(record.corePos().getCenter()) > radiusSquared) continue;
            UUID nation = nations.nationIdFor(player.getUUID()).orElse(null);
            if (record.defenderNation().equals(nation)) defender = true;
            else if (record.individualAttacker()
                    ? record.attackerNation().equals(player.getUUID())
                    : record.attackerNation().equals(nation)) attacker = true;
        }
        if (defender && !attacker) return SiegeFallPolicy.Presence.DEFENDER_ONLY;
        if (defender) return SiegeFallPolicy.Presence.CONTESTED;
        return SiegeFallPolicy.Presence.EMPTY_OR_ATTACKER;
    }

    static void broadcastFall(MinecraftServer server, NationSavedData nations,
                                      SiegeSavedData.FallenRecord fallen) {
        NationSavedData.Nation defender = nations.nation(fallen.defenderNation()).orElse(null);
        server.getPlayerList().broadcastSystemMessage(MoveEarthMessage.warning(Component.translatable(
                "message.moveearth_addtional.siege.core_fallen",
                defender == null ? "?" : defender.name(),
                attackerName(server, nations, fallen.attackerNation(), fallen.individualAttacker()),
                fallen.corePos().getX(), fallen.corePos().getY(), fallen.corePos().getZ())), false);
        publishFallen(server, fallen, NationNotificationSavedData.EventType.CORE_FALLEN,
                defender == null ? "?" : defender.name());
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
        TerritoryCoreHealthService.syncCore(server, settlement.core());
        syncFallVisuals(server, record);

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
