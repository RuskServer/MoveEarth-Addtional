package com.ruskserver.moveearth_addtional.s2.siege;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
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
    private static final Map<LogKey, Long> RECENT_LOGS = new HashMap<>();

    private SiegeService() { }

    public static SiegeSavedData.AttemptResult recordAttack(ServerPlayer attacker, ServerLevel level,
                                                             BlockPos target, boolean effectiveDamage) {
        if (attacker == null) return new SiegeSavedData.AttemptResult(SiegeSavedData.AttemptStatus.IGNORED, null);
        NationSavedData nations = NationSavedData.get(level.getServer());
        UUID attackerNation = nations.nationIdFor(attacker.getUUID()).orElse(null);
        TerritorySavedData.CoreRecord core = TerritorySavedData.get(level.getServer())
                .controllingCore(level.dimension().location(), target).orElse(null);
        SiegeSavedData siegeData = SiegeSavedData.get(level.getServer());
        if (attackerNation == null || core == null || siegeData.isCoreFallen(core.id())
                || attackerNation.equals(core.nationId())
                || nations.isAllied(attackerNation, core.nationId())) {
            return new SiegeSavedData.AttemptResult(SiegeSavedData.AttemptStatus.IGNORED, null);
        }

        long gameTick = level.getServer().overworld().getGameTime();
        LogKey logKey = new LogKey(attacker.getUUID(), level.dimension().location().toString(), target.asLong());
        boolean shouldLog = gameTick >= RECENT_LOGS.getOrDefault(logKey, Long.MIN_VALUE)
                + S2TerritoryConfig.siegeDuplicateLogTicks();
        if (shouldLog) {
            RECENT_LOGS.put(logKey, gameTick);
            Moveearth_addtional.LOGGER.info("Siege attempt: player={} attackerNation={} defenderNation={} target={} effective={}",
                    attacker.getGameProfile().getName(), attackerNation, core.nationId(), target, effectiveDamage);
        }
        boolean offlineDefenseAllowed = OfflineDefenseService.baseDivisor(level, core) > 1;
        SiegeSavedData.AttemptResult result = siegeData.registerAttempt(
                attackerNation, core, effectiveDamage, offlineDefenseAllowed);
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
        UUID defenderNation = TerritorySavedData.get(level.getServer())
                .controllingNation(level.dimension().location(), target).orElse(null);
        return attackerNation != null && defenderNation != null
                && !attackerNation.equals(defenderNation)
                && SiegeSavedData.get(level.getServer()).isPeaceTruceActive(
                        attackerNation, defenderNation);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().overworld().getGameTime() % 20L != 0L) return;
        SiegeSavedData siegeData = SiegeSavedData.get(event.getServer());
        SiegeSavedData.TickResult result = siegeData.advance(20L);
        PeaceSavedData.get(event.getServer()).advance(20L);
        TerritorySavedData.get(event.getServer()).advanceVaultCooldowns(20L);
        NationSavedData nations = NationSavedData.get(event.getServer());
        result.initialExpired().forEach(siege -> notifyParties(event.getServer(), nations, siege,
                Component.translatable("message.moveearth_addtional.siege.initial_expired")));
        result.rollingExpired().forEach(siege -> notifyParties(event.getServer(), nations, siege,
                Component.translatable("message.moveearth_addtional.siege.ended")));
        SiegeSavedData.FallenTickResult fallen = siegeData.advanceFallen(
                20L, record -> counterPresence(event.getServer(), nations, record));
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
                }));
        fallen.finalized().forEach(record -> finalizeFall(
                event.getServer(), nations, siegeData, record));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) { RECENT_LOGS.clear(); }

    private static void notifyTransition(MinecraftServer server, NationSavedData nations,
                                         SiegeSavedData.AttemptResult result) {
        if (result.siege() == null) return;
        if (result.status() == SiegeSavedData.AttemptStatus.INITIAL_STARTED) {
            notifyParties(server, nations, result.siege(),
                    Component.translatable("message.moveearth_addtional.siege.initial_started",
                            formatTicks(S2TerritoryConfig.siegeInitialLockTicks())));
        } else if (result.status() == SiegeSavedData.AttemptStatus.ROLLING_STARTED) {
            NationSavedData.Nation attacker = nations.nation(result.siege().attackerNation()).orElse(null);
            NationSavedData.Nation defender = nations.nation(result.siege().defenderNation()).orElse(null);
            String attackerName = attacker == null ? "?" : attacker.name();
            String defenderName = defender == null ? "?" : defender.name();
            server.getPlayerList().broadcastSystemMessage(MoveEarthMessage.warning(Component.translatable(
                    "message.moveearth_addtional.siege.rolling_started", attackerName, defenderName,
                    result.siege().corePos().getX(), result.siege().corePos().getY(), result.siege().corePos().getZ())), false);
        }
    }

    private static void notifyParties(MinecraftServer server, NationSavedData nations,
                                      SiegeSavedData.SiegeRecord siege, Component body) {
        for (UUID nationId : java.util.List.of(siege.attackerNation(), siege.defenderNation())) {
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
            else if (record.attackerNation().equals(nation)) attacker = true;
        }
        if (defender && !attacker) return SiegeFallPolicy.Presence.DEFENDER_ONLY;
        if (defender) return SiegeFallPolicy.Presence.CONTESTED;
        return SiegeFallPolicy.Presence.EMPTY_OR_ATTACKER;
    }

    static void broadcastFall(MinecraftServer server, NationSavedData nations,
                                      SiegeSavedData.FallenRecord fallen) {
        NationSavedData.Nation attacker = nations.nation(fallen.attackerNation()).orElse(null);
        NationSavedData.Nation defender = nations.nation(fallen.defenderNation()).orElse(null);
        server.getPlayerList().broadcastSystemMessage(MoveEarthMessage.warning(Component.translatable(
                "message.moveearth_addtional.siege.core_fallen",
                defender == null ? "?" : defender.name(), attacker == null ? "?" : attacker.name(),
                fallen.corePos().getX(), fallen.corePos().getY(), fallen.corePos().getZ())), false);
    }

    private static void notifyFallenParties(MinecraftServer server, NationSavedData nations,
                                            SiegeSavedData.FallenRecord fallen, Component body) {
        for (UUID nationId : java.util.List.of(fallen.attackerNation(), fallen.defenderNation())) {
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
                record.coreId(), record.attackerNation(),
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

        NationSavedData.Nation attacker = nations.nation(record.attackerNation()).orElse(null);
        NationSavedData.Nation defender = nations.nation(record.defenderNation()).orElse(null);
        String attackerName = attacker == null ? "?" : attacker.name();
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
    }

    private static String formatTicks(long ticks) {
        long seconds = Math.max(0L, (ticks + 19L) / 20L);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L);
    }

    private record LogKey(UUID player, String dimension, long pos) { }
}
