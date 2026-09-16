package com.ruskserver.moveearth_addtional.compat.warnautics;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementEntry;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementSavedData;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementService;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementBlastOcclusion;
import com.ruskserver.moveearth_addtional.s2.reinforcement.SiegeDamageService;
import com.ruskserver.moveearth_addtional.s2.reinforcement.WarnauticsWeaponDamage;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritoryCoreHealthService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.s2.territory.UpkeepPenalty;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Runtime-only bridge to Create Warnautics' public block-detonation hooks. */
public final class WarnauticsReinforcementCompat {
    private static final String MOD_ID = "cbc_more_content";
    private static final String DETONATE_EVENT = "com.cbc_more_content.event.WarnauticsBlockDetonateEvent";
    private static final String CHIP_EVENT = "com.cbc_more_content.event.WarnauticsBlockChipEvent";
    private static final String TERRAIN_EXPLOSION =
            "com.cbc_more_content.effects.BombExplosionHandler$TerrainShellExplosion";

    private static volatile boolean detonationBridgeReady;
    private static Method detonateLevel;
    private static Method detonateExplosion;
    private static Method detonateCenter;
    private static Method detonateSize;
    private static Method detonateToBlow;
    private static Method chipLevel;
    private static Method chipPos;

    private WarnauticsReinforcementCompat() { }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerIfPresent() {
        if (!ModList.get().isLoaded(MOD_ID)) return;
        try {
            Class<? extends Event> detonateClass = (Class<? extends Event>) Class.forName(DETONATE_EVENT);
            Class<? extends Event> chipClass = (Class<? extends Event>) Class.forName(CHIP_EVENT);
            detonateLevel = detonateClass.getMethod("getLevel");
            detonateExplosion = detonateClass.getMethod("getExplosion");
            detonateCenter = detonateClass.getMethod("getCenter");
            detonateSize = detonateClass.getMethod("getSize");
            detonateToBlow = detonateClass.getMethod("getToBlow");
            chipLevel = chipClass.getMethod("getLevel");
            chipPos = chipClass.getMethod("getPos");

            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, (Class) detonateClass,
                    (Consumer<Event>) WarnauticsReinforcementCompat::onDetonate);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, (Class) chipClass,
                    (Consumer<Event>) WarnauticsReinforcementCompat::onChip);
            detonationBridgeReady = true;
            Moveearth_addtional.LOGGER.info("Enabled Create Warnautics reinforcement damage bridge");
        } catch (ReflectiveOperationException | LinkageError exception) {
            detonationBridgeReady = false;
            Moveearth_addtional.LOGGER.warn(
                    "Create Warnautics is present but its block-detonation hooks are unavailable; using generic explosion protection",
                    exception);
        }
    }

    /** Only defer the generic explosion path when the dedicated listener is fully usable. */
    public static boolean handles(Explosion explosion) {
        return detonationBridgeReady && explosion != null
                && TERRAIN_EXPLOSION.equals(explosion.getClass().getName());
    }

    private static void onDetonate(Event event) {
        try {
            if (!(detonateLevel.invoke(event) instanceof ServerLevel level)
                    || !(detonateExplosion.invoke(event) instanceof Explosion explosion)
                    || !(detonateCenter.invoke(event) instanceof Vec3 center)
                    || !(detonateToBlow.invoke(event) instanceof List<?> rawToBlow)) return;

            String sizeName = enumName(detonateSize.invoke(event));
            Entity source = explosion.getDirectSourceEntity();
            WarnauticsWeaponDamage.Kind kind = WarnauticsWeaponDamage.classify(sizeName, entityPath(source));
            ServerPlayer attacker = SiegeService.attributablePlayer(source);
            WarnauticsC4SavedData.Charge charge = kind == WarnauticsWeaponDamage.Kind.C4
                    ? WarnauticsC4SavedData.get(level).consume(BlockPos.containing(center)) : null;
            SiegeService.AttackAttribution attribution = charge != null
                    ? new SiegeService.AttackAttribution(charge.nationId(), charge.placerId(), "warnautics_c4",
                    charge.contractId(), charge.siegeId(), true)
                    : sourceAttribution(level, center, source, attacker);
            processDetonation(level, center, rawToBlow, kind, attribution,
                    charge == null ? null : charge.support());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Moveearth_addtional.LOGGER.error(
                    "Failed to apply Create Warnautics reinforcement protection; retaining the original blast list",
                    exception);
        }
    }

    private static void processDetonation(ServerLevel level, Vec3 center, List<?> rawToBlow,
                                          WarnauticsWeaponDamage.Kind kind,
                                          SiegeService.AttackAttribution attribution,
                                          BlockPos c4Primary) {
        ReinforcementSavedData reinforcements = ReinforcementSavedData.get(level);
        Set<BlockPos> changed = new LinkedHashSet<>();
        Set<BlockPos> processed = new HashSet<>();
        Map<Long, UpkeepPenalty> penaltiesByChunk = new HashMap<>();
        Map<BlockPos, BlockState> protectedStates = new HashMap<>();
        BlockPos centerPos = BlockPos.containing(center);
        int snapshotRadius = 2;
        for (Object value : rawToBlow) {
            if (value instanceof BlockPos affected) {
                snapshotRadius = Math.max(snapshotRadius, Math.max(Math.abs(affected.getX() - centerPos.getX()),
                        Math.max(Math.abs(affected.getY() - centerPos.getY()),
                                Math.abs(affected.getZ() - centerPos.getZ()))) + 2);
            }
        }
        snapshotRadius = Math.min(64, snapshotRadius);
        Set<BlockPos> blastBarriers = ReinforcementBlastOcclusion.barriersAround(
                level, reinforcements, centerPos, snapshotRadius);

        Iterator<?> iterator = rawToBlow.iterator();
        while (iterator.hasNext()) {
            Object value = iterator.next();
            if (!(value instanceof BlockPos pos) || !processed.add(pos)) continue;
            if (SiegeService.peaceTruceBlocks(attribution, level, pos)) {
                iterator.remove();
                continue;
            }
            if (TerritorySavedData.get(level.getServer())
                    .core(level.dimension().location(), pos).isPresent()) {
                // Core damage is distance-based below; the physical core block is never vaporized.
                iterator.remove();
                continue;
            }
            var vehicleCore = com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData
                    .get(level.getServer()).at(level.dimension().location(), pos).orElse(null);
            if (vehicleCore != null) {
                if (ReinforcementBlastOcclusion.blocked(center, pos, blastBarriers)) {
                    iterator.remove();
                    continue;
                }
                int coreDamage = SiegeDamageService.configuredWarnauticsCoreDamage(kind);
                if (kind != WarnauticsWeaponDamage.Kind.C4 || pos.equals(c4Primary)) {
                    com.ruskserver.moveearth_addtional.s2.vehicle.VehicleCoreHealthService.damage(
                            level, pos, coreDamage);
                }
                iterator.remove();
                continue;
            }

            ReinforcementEntry entry = reinforcements.get(pos).orElse(null);
            if (entry == null) continue;
            if (ReinforcementBlastOcclusion.blocked(center, pos, blastBarriers)) {
                iterator.remove();
                protectedStates.put(pos.immutable(), level.getBlockState(pos));
                continue;
            }
            SiegeService.recordAttack(attribution, level, pos, false);
            if (!entry.enabled()) {
                reinforcements.remove(pos);
                changed.add(pos.immutable());
                continue;
            }
            long chunkKey = net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
            UpkeepPenalty penalty = penaltiesByChunk.computeIfAbsent(
                    chunkKey, ignored -> SiegeDamageService.penaltyAt(level, pos));
            int damage = SiegeDamageService.configuredWarnauticsDamage(
                    kind, c4Primary != null && c4Primary.equals(pos));
            SiegeDamageService.ReinforcementDamage result = SiegeDamageService.damageReinforcement(
                    level, pos, entry, damage, penalty);
            if (!result.remains()) changed.add(pos.immutable());
            if (result.appliedDamage() > 0) {
                changed.add(pos.immutable());
                SiegeService.recordAttack(attribution, level, pos, true);
            }
            if (result.remains()) iterator.remove();
            if (result.remains()) protectedStates.put(pos.immutable(), level.getBlockState(pos));
        }

        damageExposedCores(level, center, kind, attribution, c4Primary, blastBarriers);
        scheduleScuffRepair(level, protectedStates, 1);
        // MOAB surface scuff is deferred by Warnautics; recheck after its delayed pass too.
        scheduleScuffRepair(level, protectedStates, 10);
        if (!changed.isEmpty()) ReinforcementService.syncChangedNearbyManagers(level, changed);
    }

    private static void scheduleScuffRepair(ServerLevel level, Map<BlockPos, BlockState> protectedStates,
                                            int delayTicks) {
        if (protectedStates.isEmpty() || level.getServer() == null) return;
        int executeAt = level.getServer().getTickCount() + delayTicks;
        level.getServer().tell(new TickTask(executeAt, () -> {
            ReinforcementSavedData data = ReinforcementSavedData.get(level);
            protectedStates.forEach((pos, original) -> {
                if (!level.hasChunkAt(pos) || data.get(pos).filter(ReinforcementEntry::enabled).isEmpty()
                        || level.getBlockState(pos).equals(original)) return;
                level.setBlock(pos, original, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            });
        }));
    }

    private static void damageExposedCores(ServerLevel level, Vec3 center, WarnauticsWeaponDamage.Kind kind,
                                           SiegeService.AttackAttribution attribution, BlockPos c4Primary,
                                           Set<BlockPos> blastBarriers) {
        if (!WarnauticsWeaponDamage.canDamageCore(kind)) return;
        if (kind == WarnauticsWeaponDamage.Kind.C4 && c4Primary == null) return;
        int maximum = SiegeDamageService.configuredWarnauticsCoreDamage(kind);
        if (maximum <= 0) return;
        double radius = kind == WarnauticsWeaponDamage.Kind.C4
                ? 2.0D : S2TerritoryConfig.warnauticsCoreRadius();
        BlockPos centerPos = BlockPos.containing(center);
        for (TerritorySavedData.CoreRecord core : TerritorySavedData.get(level.getServer())
                .coresNear(level.dimension().location(), centerPos, (int) Math.ceil(radius))) {
            if (core.state() != TerritorySavedData.CoreState.EXPOSED || core.health() <= 0
                    || SiegeService.peaceTruceBlocks(attribution, level, core.pos())) continue;
            if (ReinforcementBlastOcclusion.blocked(center, core.pos(), blastBarriers)) continue;
            int damage = kind == WarnauticsWeaponDamage.Kind.C4
                    ? (core.pos().equals(c4Primary) ? maximum : 0)
                    : WarnauticsWeaponDamage.distanceScaledDamage(
                    maximum, core.pos().getCenter().distanceTo(center), radius);
            if (damage <= 0) continue;
            SiegeService.recordAttack(attribution, level, core.pos(), false);
            TerritorySavedData.CoreRecord after = TerritoryCoreHealthService.damage(level, core.pos(), damage);
            if (after != null && after.health() < core.health()) {
                SiegeService.recordAttack(attribution, level, core.pos(), true);
            }
        }
    }

    private static void onChip(Event event) {
        try {
            if (!(event instanceof ICancellableEvent cancellable)
                    || !(chipLevel.invoke(event) instanceof ServerLevel level)
                    || !(chipPos.invoke(event) instanceof BlockPos pos)) return;
            boolean protectedBlock = ReinforcementSavedData.get(level).get(pos)
                    .filter(ReinforcementEntry::enabled).isPresent()
                    || com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData.get(level.getServer())
                    .at(level.dimension().location(), pos).isPresent()
                    || TerritorySavedData.get(level.getServer())
                    .core(level.dimension().location(), pos).isPresent();
            if (protectedBlock) cancellable.setCanceled(true);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Moveearth_addtional.LOGGER.debug("Failed to read Create Warnautics block-chip event", exception);
        }
    }

    private static String enumName(Object value) {
        return value instanceof Enum<?> enumValue ? enumValue.name() : String.valueOf(value);
    }

    private static SiegeService.AttackAttribution playerAttribution(ServerPlayer player) {
        if (player == null) return null;
        UUID nation = NationSavedData.get(player.server).nationIdFor(player.getUUID()).orElse(null);
        return new SiegeService.AttackAttribution(nation, player.getUUID(), "warnautics_entity");
    }

    private static SiegeService.AttackAttribution sourceAttribution(ServerLevel level, Vec3 center,
                                                                    Entity source, ServerPlayer fallbackPlayer) {
        if (source != null) {
            SiegeService.AttackAttribution frozen =
                    com.ruskserver.moveearth_addtional.s2.dispatch.AttributionSnapshotService
                            .attribution(source, "warnautics_projectile");
            if (frozen != null) return frozen;
            var persistent = source.getPersistentData();
            if (persistent.hasUUID(WarnauticsWeaponEvents.ATTRIBUTION_NATION)
                    || persistent.hasUUID(WarnauticsWeaponEvents.ATTRIBUTION_ACTOR)) {
                return new SiegeService.AttackAttribution(
                        persistent.hasUUID(WarnauticsWeaponEvents.ATTRIBUTION_NATION)
                                ? persistent.getUUID(WarnauticsWeaponEvents.ATTRIBUTION_NATION) : null,
                        persistent.hasUUID(WarnauticsWeaponEvents.ATTRIBUTION_ACTOR)
                                ? persistent.getUUID(WarnauticsWeaponEvents.ATTRIBUTION_ACTOR) : null,
                        "warnautics_aerial_bomb");
            }
            String weaponPath = entityPath(source);
            if (!weaponPath.isEmpty()) {
                WarnauticsBombSavedData.Placement placement = WarnauticsBombSavedData.get(level)
                        .claimNearest(level, center, weaponPath, level.getGameTime());
                if (placement != null) {
                    return new SiegeService.AttackAttribution(
                            placement.nationId(), placement.placerId(), "warnautics_sable_bomb",
                            placement.contractId(), placement.siegeId(), true);
                }
            }
        }
        return playerAttribution(fallbackPlayer);
    }

    private static String entityPath(Entity source) {
        if (source == null) return "";
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(source.getType());
        return id != null && MOD_ID.equals(id.getNamespace()) ? id.getPath() : "";
    }

    public static void clearRuntimeState() {
        detonationBridgeReady = false;
    }
}
