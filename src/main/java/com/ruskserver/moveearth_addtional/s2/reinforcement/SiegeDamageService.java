package com.ruskserver.moveearth_addtional.s2.reinforcement;

import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import com.ruskserver.moveearth_addtional.s2.territory.NationUpkeepService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritoryClosureRecheckManager;
import com.ruskserver.moveearth_addtional.s2.territory.TerritoryCoreHealthService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.s2.territory.UpkeepPenalty;
import com.ruskserver.moveearth_addtional.s2.territory.UpkeepPenaltyPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeService;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.OfflineDefenseService;
import com.ruskserver.moveearth_addtional.s2.siege.LongAbsenceService;

import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

/** Applies server-authoritative siege damage without linking against an optional artillery mod. */
public final class SiegeDamageService {
    private SiegeDamageService() { }

    public static int configuredDamage(CbcMunitionDamage.Kind kind) {
        return switch (kind) {
            case SHOT -> S2TerritoryConfig.cbcShotDamage();
            case AP_SHOT -> S2TerritoryConfig.cbcApShotDamage();
            case HE_SHELL -> S2TerritoryConfig.cbcHeShellDamage();
            case AP_SHELL -> S2TerritoryConfig.cbcApShellDamage();
            case MORTAR -> S2TerritoryConfig.cbcMortarDamage();
            case FRAGMENTATION -> S2TerritoryConfig.cbcFragmentationDamage();
            case AUTOCANNON -> S2TerritoryConfig.cbcAutocannonDamage();
            case MACHINE_GUN -> S2TerritoryConfig.cbcMachineGunDamage();
            case UTILITY -> S2TerritoryConfig.cbcUtilityDamage();
        };
    }

    public static int configuredCoreDamage(CbcMunitionDamage.Kind kind) {
        return CbcMunitionDamage.coreDamage(kind, configuredDamage(kind),
                S2TerritoryConfig.cbcCoreDamageMultiplier());
    }

    public static int configuredTerritoryCoreDamage(CbcMunitionDamage.Kind kind, boolean pointHit) {
        int base = configuredCoreDamage(kind);
        return pointHit ? CbcMunitionDamage.coreDamage(kind, base,
                S2TerritoryConfig.cbcDirectCoreMultiplier()) : base;
    }

    public static int configuredWarnauticsDamage(WarnauticsWeaponDamage.Kind kind, boolean c4Primary) {
        return switch (kind) {
            case SMALL_BOMB -> S2TerritoryConfig.warnauticsSmallDamage();
            case SEA_BOMB -> S2TerritoryConfig.warnauticsSeaDamage();
            case MEDIUM_BOMB -> S2TerritoryConfig.warnauticsMediumDamage();
            case LARGE_BOMB -> S2TerritoryConfig.warnauticsLargeDamage();
            case MOAB -> S2TerritoryConfig.warnauticsMoabDamage();
            case CRUISE_MISSILE -> 0;
            case C4 -> c4Primary ? S2TerritoryConfig.warnauticsC4PrimaryDamage()
                    : S2TerritoryConfig.warnauticsC4SplashDamage();
            case LARGE_MINE, UNKNOWN -> 0;
        };
    }

    public static int configuredWarnauticsCoreDamage(WarnauticsWeaponDamage.Kind kind) {
        if (!WarnauticsWeaponDamage.canDamageCore(kind)) return 0;
        if (kind == WarnauticsWeaponDamage.Kind.C4) return S2TerritoryConfig.warnauticsC4CoreDamage();
        return configuredWarnauticsDamage(kind, false);
    }

    /** Returns true when the original terrain-damage attempt must be cancelled. */
    public static boolean interceptCbcImpact(ServerLevel level, BlockPos pos, CbcMunitionDamage.Kind kind) {
        return interceptCbcImpact(null, level, pos, kind);
    }

    public static boolean interceptCbcImpact(ServerPlayer attacker, ServerLevel level, BlockPos pos,
                                             CbcMunitionDamage.Kind kind) {
        if (SiegeService.peaceTruceBlocks(attacker, level, pos)) return true;
        ReinforcementSavedData reinforcements = ReinforcementSavedData.get(level);
        ReinforcementEntry entry = reinforcements.get(pos).orElse(null);
        if (entry != null && entry.enabled()) {
            SiegeService.recordAttack(attacker, level, pos, false);
            UpkeepPenalty penalty = penaltyAt(level, pos);
            if (!penalty.reinforcementProtectionEnabled()) {
                reinforcements.remove(pos);
                TerritoryClosureRecheckManager.markPotentialOpening(level, pos);
                ReinforcementService.syncChangedNearbyManagers(level, Set.of(pos));
                return false;
            }
            ReinforcementDamage result = damageReinforcement(level, pos, entry, kind, penalty, true);
            if (result.appliedDamage() > 0) SiegeService.recordAttack(attacker, level, pos, true);
            return true;
        }
        var vehicleCore = com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData
                .get(level.getServer()).at(level.dimension().location(), pos).orElse(null);
        if (vehicleCore != null) {
            SiegeService.AttackAttribution attribution = attacker == null ? null
                    : new SiegeService.AttackAttribution(
                    com.ruskserver.moveearth_addtional.s2.nation.NationSavedData.get(level.getServer())
                            .nationIdFor(attacker.getUUID()).orElse(null), attacker.getUUID(), "cbc");
            com.ruskserver.moveearth_addtional.s2.vehicle.VehicleCoreHealthService.damage(
                    level, pos, configuredCoreDamage(kind), attribution);
            return true;
        }
        TerritorySavedData.CoreRecord core = TerritorySavedData.get(level.getServer())
                .core(level.dimension().location(), pos).orElse(null);
        if (core != null) {
            SiegeService.recordAttack(attacker, level, pos, false);
            TerritorySavedData.CoreRecord after = TerritoryCoreHealthService.damage(
                    level, pos, configuredTerritoryCoreDamage(kind, true));
            if (after != null && after.health() < core.health()) SiegeService.recordAttack(attacker, level, pos, true);
            return true;
        }
        return false;
    }

    /**
     * Handles protected blocks before CBC's explosion raycast can transform stone into cobblestone.
     * Returning true cancels CBC terrain edits for that blast while entity damage and effects remain.
     */
    public static boolean interceptCbcProtectedArea(ServerPlayer attacker, ServerLevel level, BlockPos center,
                                                     CbcMunitionDamage.Kind kind, int radius) {
        SiegeService.AttackAttribution attribution = attacker == null ? null : new SiegeService.AttackAttribution(
                com.ruskserver.moveearth_addtional.s2.nation.NationSavedData.get(level.getServer())
                        .nationIdFor(attacker.getUUID()).orElse(null), attacker.getUUID(), "cbc");
        return interceptCbcProtectedArea(attribution, level, center, kind, radius);
    }

    public static boolean interceptCbcProtectedArea(SiegeService.AttackAttribution attribution,
                                                     ServerLevel level, BlockPos center,
                                                     CbcMunitionDamage.Kind kind, int radius) {
        int safeRadius = Math.max(0, radius);
        int damage = configuredDamage(kind);
        boolean intercepted = false;
        boolean reinforcementChanged = false;
        Set<BlockPos> changedPositions = new LinkedHashSet<>();
        ReinforcementSavedData reinforcements = ReinforcementSavedData.get(level);
        Set<BlockPos> blastBarriers = ReinforcementBlastOcclusion.barriersAround(
                level, reinforcements, center, safeRadius + 2);
        net.minecraft.world.phys.Vec3 blastOrigin = center.getCenter();
        Map<Long, UpkeepPenalty> penaltiesByChunk = new HashMap<>();
        for (ReinforcementSavedData.LocatedEntry located : reinforcements.around(level, center, safeRadius)) {
            ReinforcementEntry entry = located.entry();
            if (!entry.enabled()) continue;
            if (SiegeService.peaceTruceBlocks(attribution, level, located.pos())) {
                intercepted = true;
                continue;
            }
            if (ReinforcementBlastOcclusion.blocked(blastOrigin, located.pos(), blastBarriers)) {
                intercepted = true;
                continue;
            }
            long chunkKey = net.minecraft.world.level.ChunkPos.asLong(
                    located.pos().getX() >> 4, located.pos().getZ() >> 4);
            UpkeepPenalty penalty = penaltiesByChunk.computeIfAbsent(
                    chunkKey, ignored -> penaltyAt(level, located.pos()));
            if (!penalty.reinforcementProtectionEnabled()) continue;
            intercepted = true;
            SiegeService.recordAttack(attribution, level, located.pos(), false);
            if (damage > 0) {
                ReinforcementDamage result = damageReinforcement(
                        level, located.pos(), entry, kind, penalty, false);
                if (result.appliedDamage() > 0) {
                    SiegeService.recordAttack(attribution, level, located.pos(), true);
                    reinforcementChanged = true;
                    changedPositions.add(located.pos().immutable());
                }
            }
        }
        // Vehicle cores are deliberately point-hit only. Do not search the blast radius here,
        // otherwise a shell striking intact armor could drain the core behind it.
        var vehicleCore = com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData
                .get(level.getServer()).at(level.dimension().location(), center).orElse(null);
        if (vehicleCore != null) {
            intercepted = true;
            if (!SiegeService.peaceTruceBlocks(attribution, level, center)) {
                com.ruskserver.moveearth_addtional.s2.vehicle.VehicleCoreHealthService.damage(
                        level, center, configuredCoreDamage(kind), attribution);
            }
        }
        long radiusSquared = (long) safeRadius * safeRadius;
        for (TerritorySavedData.CoreRecord core : TerritorySavedData.get(level.getServer())
                .coresNear(level.dimension().location(), center, safeRadius)) {
            if (core.pos().distSqr(center) > radiusSquared
                    || core.state() != TerritorySavedData.CoreState.EXPOSED || core.health() <= 0) continue;
            intercepted = true;
            if (SiegeService.peaceTruceBlocks(attribution, level, core.pos())) continue;
            if (ReinforcementBlastOcclusion.blocked(blastOrigin, core.pos(), blastBarriers)) continue;
            SiegeService.recordAttack(attribution, level, core.pos(), false);
            TerritorySavedData.CoreRecord after = TerritoryCoreHealthService.damage(
                    level, core.pos(), configuredTerritoryCoreDamage(kind, core.pos().equals(center)));
            if (after != null && after.health() < core.health()) {
                SiegeService.recordAttack(attribution, level, core.pos(), true);
            }
        }
        if (reinforcementChanged) ReinforcementService.syncChangedNearbyManagers(level, changedPositions);
        return intercepted;
    }

    /** Returns true if reinforcement remains and the explosion must not destroy the block. */
    public static ReinforcementDamage damageReinforcement(ServerLevel level, BlockPos pos,
                                                          ReinforcementEntry entry,
                                                          CbcMunitionDamage.Kind kind) {
        UpkeepPenalty penalty = penaltyAt(level, pos);
        return damageReinforcement(level, pos, entry, kind, penalty);
    }

    static ReinforcementDamage damageReinforcement(ServerLevel level, BlockPos pos,
                                                    ReinforcementEntry entry,
                                                    CbcMunitionDamage.Kind kind,
                                                    UpkeepPenalty penalty) {
        if (!penalty.reinforcementProtectionEnabled()) {
            ReinforcementSavedData.get(level).remove(pos);
            TerritoryClosureRecheckManager.markPotentialOpening(level, pos);
            return new ReinforcementDamage(false, 0);
        }
        return damageReinforcement(level, pos, entry, kind, penalty, false);
    }

    /** Shared optional-mod entry point; applies upkeep and offline scaling to an explicit profile value. */
    public static ReinforcementDamage damageReinforcement(ServerLevel level, BlockPos pos,
                                                           ReinforcementEntry entry, int configuredDamage,
                                                           UpkeepPenalty penalty) {
        if (!penalty.reinforcementProtectionEnabled()) {
            ReinforcementSavedData.get(level).remove(pos);
            TerritoryClosureRecheckManager.markPotentialOpening(level, pos);
            return new ReinforcementDamage(false, 0);
        }
        return damageReinforcement(level, pos, entry, configuredDamage, penalty, false);
    }

    private static ReinforcementDamage damageReinforcement(ServerLevel level, BlockPos pos,
                                                            ReinforcementEntry entry,
                                                            CbcMunitionDamage.Kind kind,
                                                            UpkeepPenalty penalty,
                                                            boolean syncImmediately) {
        return damageReinforcement(level, pos, entry, configuredDamage(kind), penalty, syncImmediately);
    }

    private static ReinforcementDamage damageReinforcement(ServerLevel level, BlockPos pos,
                                                            ReinforcementEntry entry,
                                                            int configuredDamage,
                                                            UpkeepPenalty penalty,
                                                            boolean syncImmediately) {
        int rawDamage = UpkeepPenaltyPolicy.scaleSiegeDamage(configuredDamage, penalty,
                S2TerritoryConfig.overdueDamageMultiplier());
        int damage = OfflineDefenseService.scale(level, pos, rawDamage).appliedDamage();
        if (damage <= 0) return new ReinforcementDamage(true, 0);
        com.ruskserver.moveearth_addtional.s2.vehicle.VehicleRepairService.recordHit(level, pos);
        ReinforcementEntry damaged = entry.damage(damage);
        ReinforcementSavedData data = ReinforcementSavedData.get(level);
        data.recordDamage(pos, level.getGameTime(), S2TerritoryConfig.breachRepairDelayTicks());
        if (damaged.durability() <= 0) {
            data.remove(pos);
            TerritoryClosureRecheckManager.markPotentialOpening(level, pos);
            if (syncImmediately) ReinforcementService.syncChangedNearbyManagers(level, Set.of(pos));
            return new ReinforcementDamage(false, damage);
        }
        data.put(pos, damaged);
        if (syncImmediately) ReinforcementService.syncChangedNearbyManagers(level, Set.of(pos));
        return new ReinforcementDamage(true, damage);
    }

    public static UpkeepPenalty penaltyAt(ServerLevel level, BlockPos pos) {
        var vehicle = com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology.at(level, pos)
                .orElse(null);
        if (vehicle != null) {
            return NationUpkeepService.penalty(level.getServer(), vehicle.vehicle().nationId());
        }
        if (OfflineDefenseService.settlementProtected(level, pos)) return UpkeepPenalty.CURRENT;
        if (SiegeSavedData.get(level.getServer()).isReinforcementDisabled(
                level.dimension().location(), pos)) return UpkeepPenalty.DISABLED;
        if (!LongAbsenceService.tierAt(level, pos).reinforcementProtectionEnabled()) {
            return UpkeepPenalty.DISABLED;
        }
        return TerritorySavedData.get(level.getServer()).controllingNation(
                        level.getServer(), level.dimension().location(), pos)
                .map(nation -> NationUpkeepService.penalty(level.getServer(), nation))
                .orElse(UpkeepPenalty.DISABLED);
    }

    public record ReinforcementDamage(boolean remains, int appliedDamage) { }
}
