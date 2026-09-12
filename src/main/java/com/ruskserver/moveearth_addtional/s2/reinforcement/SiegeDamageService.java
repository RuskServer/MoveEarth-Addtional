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
                ReinforcementService.syncNearbyManagers(level, pos);
                return false;
            }
            ReinforcementDamage result = damageReinforcement(level, pos, entry, kind, penalty, true);
            if (result.appliedDamage() > 0) SiegeService.recordAttack(attacker, level, pos, true);
            return true;
        }
        TerritorySavedData.CoreRecord core = TerritorySavedData.get(level.getServer())
                .core(level.dimension().location(), pos).orElse(null);
        if (core != null) {
            SiegeService.recordAttack(attacker, level, pos, false);
            TerritorySavedData.CoreRecord after = TerritoryCoreHealthService.damage(
                    level, pos, configuredCoreDamage(kind));
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
        int safeRadius = Math.max(0, radius);
        int damage = configuredDamage(kind);
        boolean intercepted = false;
        boolean reinforcementChanged = false;
        ReinforcementSavedData reinforcements = ReinforcementSavedData.get(level);
        Map<Long, UpkeepPenalty> penaltiesByChunk = new HashMap<>();
        for (ReinforcementSavedData.LocatedEntry located : reinforcements.around(level, center, safeRadius)) {
            ReinforcementEntry entry = located.entry();
            if (!entry.enabled()) continue;
            if (SiegeService.peaceTruceBlocks(attacker, level, located.pos())) {
                intercepted = true;
                continue;
            }
            long chunkKey = net.minecraft.world.level.ChunkPos.asLong(
                    located.pos().getX() >> 4, located.pos().getZ() >> 4);
            UpkeepPenalty penalty = penaltiesByChunk.computeIfAbsent(
                    chunkKey, ignored -> penaltyAt(level, located.pos()));
            if (!penalty.reinforcementProtectionEnabled()) continue;
            intercepted = true;
            SiegeService.recordAttack(attacker, level, located.pos(), false);
            if (damage > 0) {
                ReinforcementDamage result = damageReinforcement(
                        level, located.pos(), entry, kind, penalty, false);
                if (result.appliedDamage() > 0) {
                    SiegeService.recordAttack(attacker, level, located.pos(), true);
                    reinforcementChanged = true;
                }
            }
        }
        long radiusSquared = (long) safeRadius * safeRadius;
        for (TerritorySavedData.CoreRecord core : TerritorySavedData.get(level.getServer())
                .coresNear(level.dimension().location(), center, safeRadius)) {
            if (core.pos().distSqr(center) > radiusSquared
                    || core.state() != TerritorySavedData.CoreState.EXPOSED || core.health() <= 0) continue;
            intercepted = true;
            if (SiegeService.peaceTruceBlocks(attacker, level, core.pos())) continue;
            SiegeService.recordAttack(attacker, level, core.pos(), false);
            TerritorySavedData.CoreRecord after = TerritoryCoreHealthService.damage(
                    level, core.pos(), configuredCoreDamage(kind));
            if (after != null && after.health() < core.health()) {
                SiegeService.recordAttack(attacker, level, core.pos(), true);
            }
        }
        if (reinforcementChanged) ReinforcementService.syncNearbyManagers(level, center);
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

    private static ReinforcementDamage damageReinforcement(ServerLevel level, BlockPos pos,
                                                            ReinforcementEntry entry,
                                                            CbcMunitionDamage.Kind kind,
                                                            UpkeepPenalty penalty,
                                                            boolean syncImmediately) {
        int rawDamage = UpkeepPenaltyPolicy.scaleSiegeDamage(configuredDamage(kind), penalty,
                S2TerritoryConfig.overdueDamageMultiplier());
        int damage = OfflineDefenseService.scale(level, pos, rawDamage).appliedDamage();
        if (damage <= 0) return new ReinforcementDamage(true, 0);
        ReinforcementEntry damaged = entry.damage(damage);
        ReinforcementSavedData data = ReinforcementSavedData.get(level);
        if (damaged.durability() <= 0) {
            data.remove(pos);
            TerritoryClosureRecheckManager.markPotentialOpening(level, pos);
            if (syncImmediately) ReinforcementService.syncNearbyManagers(level, pos);
            return new ReinforcementDamage(false, damage);
        }
        data.put(pos, damaged);
        if (syncImmediately) ReinforcementService.syncNearbyManagers(level, pos);
        return new ReinforcementDamage(true, damage);
    }

    public static UpkeepPenalty penaltyAt(ServerLevel level, BlockPos pos) {
        if (OfflineDefenseService.settlementProtected(level, pos)) return UpkeepPenalty.CURRENT;
        if (SiegeSavedData.get(level.getServer()).isReinforcementDisabled(
                level.dimension().location(), pos)) return UpkeepPenalty.DISABLED;
        if (!LongAbsenceService.tierAt(level, pos).reinforcementProtectionEnabled()) {
            return UpkeepPenalty.DISABLED;
        }
        return TerritorySavedData.get(level.getServer()).controllingNation(level.dimension().location(), pos)
                .map(nation -> NationUpkeepService.penalty(level.getServer(), nation))
                .orElse(UpkeepPenalty.DISABLED);
    }

    public record ReinforcementDamage(boolean remains, int appliedDamage) { }
}
