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
            case UTILITY -> S2TerritoryConfig.cbcUtilityDamage();
        };
    }

    /** Returns true when the original terrain-damage attempt must be cancelled. */
    public static boolean interceptCbcImpact(ServerLevel level, BlockPos pos, CbcMunitionDamage.Kind kind) {
        ReinforcementSavedData reinforcements = ReinforcementSavedData.get(level);
        ReinforcementEntry entry = reinforcements.get(pos).orElse(null);
        if (entry != null && entry.enabled()) {
            UpkeepPenalty penalty = penaltyAt(level, pos);
            if (!penalty.reinforcementProtectionEnabled()) {
                reinforcements.remove(pos);
                TerritoryClosureRecheckManager.markPotentialOpening(level, pos);
                ReinforcementService.syncNearbyManagers(level, pos);
                return false;
            }
            damageReinforcement(level, pos, entry, kind, penalty, true);
            return true;
        }
        TerritorySavedData.CoreRecord core = TerritorySavedData.get(level.getServer())
                .core(level.dimension().location(), pos).orElse(null);
        if (core != null) {
            TerritoryCoreHealthService.damage(level, pos, configuredDamage(kind));
            return true;
        }
        return false;
    }

    /** Returns true if reinforcement remains and the explosion must not destroy the block. */
    public static boolean damageReinforcement(ServerLevel level, BlockPos pos,
                                              ReinforcementEntry entry, CbcMunitionDamage.Kind kind) {
        UpkeepPenalty penalty = penaltyAt(level, pos);
        if (!penalty.reinforcementProtectionEnabled()) {
            ReinforcementSavedData.get(level).remove(pos);
            TerritoryClosureRecheckManager.markPotentialOpening(level, pos);
            return false;
        }
        return damageReinforcement(level, pos, entry, kind, penalty, false);
    }

    private static boolean damageReinforcement(ServerLevel level, BlockPos pos, ReinforcementEntry entry,
                                               CbcMunitionDamage.Kind kind, UpkeepPenalty penalty,
                                               boolean syncImmediately) {
        int damage = UpkeepPenaltyPolicy.scaleSiegeDamage(configuredDamage(kind), penalty,
                S2TerritoryConfig.overdueDamageMultiplier());
        ReinforcementEntry damaged = entry.damage(damage);
        ReinforcementSavedData data = ReinforcementSavedData.get(level);
        if (damaged.durability() <= 0) {
            data.remove(pos);
            TerritoryClosureRecheckManager.markPotentialOpening(level, pos);
            if (syncImmediately) ReinforcementService.syncNearbyManagers(level, pos);
            return false;
        }
        data.put(pos, damaged);
        if (syncImmediately) ReinforcementService.syncNearbyManagers(level, pos);
        return true;
    }

    public static UpkeepPenalty penaltyAt(ServerLevel level, BlockPos pos) {
        return TerritorySavedData.get(level.getServer()).controllingNation(level.dimension().location(), pos)
                .map(nation -> NationUpkeepService.penalty(level.getServer(), nation))
                .orElse(UpkeepPenalty.CURRENT);
    }
}
