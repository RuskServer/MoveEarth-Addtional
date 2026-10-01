package com.ruskserver.moveearth_addtional.s2.reinforcement;

import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import com.ruskserver.moveearth_addtional.block.entity.VehicleCoreBlockEntity;
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

    public static int configuredVehicleCoreDamage(CbcMunitionDamage.Kind kind, boolean pointHit) {
        return CbcMunitionDamage.vehicleCoreDamage(kind, configuredCoreDamage(kind),
                S2TerritoryConfig.cbcApAutocannonVehicleCoreDamage(), pointHit);
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
            // Unattributed artillery is refused outright; own or allied artillery must not chip it unseen.
            if (attacker == null || ReinforcementEvents.friendlyToOwner(attacker.getUUID(), level, pos)) return true;
            ReinforcementDamage result = damageReinforcement(level, pos, entry, kind, penalty, true);
            if (result.appliedDamage() > 0) {
                var applied = SiegeService.recordAttack(attacker, level, pos, true);
                if (attacker != null && applied.siege() != null) {
                    com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(attacker,
                            com.ruskserver.moveearth_addtional.advancement.ModCriteria.ARTILLERY_HIT);
                }
            }
            return true;
        }
        var vehicleCore = com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData
                .get(level.getServer()).at(level.dimension().location(), pos).orElse(null);
        if (vehicleCore != null) {
            if (attacker == null) return true;
            SiegeService.AttackAttribution attribution = new SiegeService.AttackAttribution(
                    com.ruskserver.moveearth_addtional.s2.nation.NationSavedData.get(level.getServer())
                            .nationIdFor(attacker.getUUID()).orElse(null), attacker.getUUID(), "cbc");
            int damage = configuredVehicleCoreDamage(kind, true);
            if (kind == CbcMunitionDamage.Kind.AUTOCANNON
                    && !(level.getBlockEntity(pos) instanceof VehicleCoreBlockEntity)) damage = 0;
            com.ruskserver.moveearth_addtional.s2.vehicle.VehicleCoreHealthService.damage(
                    level, pos, damage, attribution);
            return true;
        }
        TerritorySavedData.CoreRecord core = TerritorySavedData.get(level.getServer())
                .core(level.dimension().location(), pos).orElse(null);
        if (core != null) {
            if (attacker == null) return true;
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
     *
     * <p>An unattributed blast (no actor and no nation, see
     * {@link com.ruskserver.moveearth_addtional.compat.cbc.CbcShotAttributionPolicy}) is still intercepted
     * but damages no reinforcement, vehicle core or territory core: nobody would answer for it, so no
     * truce, Siege or friendly-fire rule could apply.
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
        boolean attributed = attribution != null && com.ruskserver.moveearth_addtional.compat.cbc
                .CbcShotAttributionPolicy.mayDamageProtected(attribution.actorId(), attribution.nationId());
        boolean intercepted = false;
        boolean reinforcementChanged = false;
        Set<BlockPos> changedPositions = new LinkedHashSet<>();
        ReinforcementSavedData reinforcements = ReinforcementSavedData.get(level);
        // Taken before the first reinforcement is damaged, exactly like the former eager snapshot.
        ReinforcementEvents.BlastBarriers blastBarriers = new ReinforcementEvents.BlastBarriers(
                level, reinforcements, center, safeRadius + 2);
        net.minecraft.world.phys.Vec3 blastOrigin = center.getCenter();
        Map<Long, UpkeepPenalty> penaltiesByChunk = new HashMap<>();
        SiegeService.AttackBatch siege = new SiegeService.AttackBatch(attribution, level);
        ReinforcementEvents.FriendlyFire friendlyFire = attribution == null ? null
                : new ReinforcementEvents.FriendlyFire(attribution, level);
        for (ReinforcementSavedData.LocatedEntry located : reinforcements.aroundUnordered(level, center, safeRadius)) {
            ReinforcementEntry entry = located.entry();
            if (!entry.enabled()) continue;
            if (siege.truceBlocks(located.pos())) {
                intercepted = true;
                continue;
            }
            if (ReinforcementBlastOcclusion.blocked(blastOrigin, located.pos(), blastBarriers.get())) {
                intercepted = true;
                continue;
            }
            long chunkKey = net.minecraft.world.level.ChunkPos.asLong(
                    located.pos().getX() >> 4, located.pos().getZ() >> 4);
            UpkeepPenalty penalty = penaltiesByChunk.computeIfAbsent(
                    chunkKey, ignored -> penaltyAt(level, located.pos()));
            if (!penalty.reinforcementProtectionEnabled()) continue;
            intercepted = true;
            if (!attributed) continue;
            if (friendlyFire != null && friendlyFire.friendly(located.pos())) continue;
            siege.record(located.pos(), false);
            if (damage > 0) {
                ReinforcementDamage result = damageReinforcement(
                        level, located.pos(), entry, kind, penalty, false);
                if (result.appliedDamage() > 0) {
                    var applied = siege.record(located.pos(), true);
                    if (applied.siege() != null && attribution != null && attribution.actorId() != null) {
                        ServerPlayer actor = level.getServer().getPlayerList().getPlayer(attribution.actorId());
                        if (actor != null) com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(actor,
                                com.ruskserver.moveearth_addtional.advancement.ModCriteria.ARTILLERY_HIT);
                    }
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
            if (attributed && !siege.truceBlocks(center)) {
                int vehicleDamage = configuredVehicleCoreDamage(kind, safeRadius == 0);
                if (kind == CbcMunitionDamage.Kind.AUTOCANNON
                        && !(level.getBlockEntity(center) instanceof VehicleCoreBlockEntity)) vehicleDamage = 0;
                com.ruskserver.moveearth_addtional.s2.vehicle.VehicleCoreHealthService.damage(
                        level, center, vehicleDamage, attribution);
            }
        }
        long radiusSquared = (long) safeRadius * safeRadius;
        for (TerritorySavedData.CoreRecord core : TerritorySavedData.get(level.getServer())
                .coresNear(level.dimension().location(), center, safeRadius)) {
            if (core.pos().distSqr(center) > radiusSquared
                    || core.state() != TerritorySavedData.CoreState.EXPOSED || core.health() <= 0) continue;
            intercepted = true;
            if (!attributed || siege.truceBlocks(core.pos())) continue;
            if (ReinforcementBlastOcclusion.blocked(blastOrigin, core.pos(), blastBarriers.get())) continue;
            siege.record(core.pos(), false);
            TerritorySavedData.CoreRecord after = TerritoryCoreHealthService.damage(
                    level, core.pos(), configuredTerritoryCoreDamage(kind, core.pos().equals(center)));
            if (after != null && after.health() < core.health()) siege.recordCoreHit(core.pos());
        }
        if (reinforcementChanged) ReinforcementService.syncChangedNearbyManagers(level, changedPositions);
        return intercepted;
    }

    /**
     * Cheap superset test for {@link #interceptCbcProtectedArea}: false only when no enabled reinforcement,
     * vehicle core or live exposed territory core lies within {@code radius} of the impact, in which case
     * that method would neither intercept nor change anything.
     */
    public static boolean cbcAreaMayBeProtected(ServerLevel level, BlockPos center, int radius) {
        int safeRadius = Math.max(0, radius);
        if (com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData.get(level.getServer())
                .at(level.dimension().location(), center).isPresent()) return true;
        long radiusSquared = (long) safeRadius * safeRadius;
        for (TerritorySavedData.CoreRecord core : TerritorySavedData.get(level.getServer())
                .coresNear(level.dimension().location(), center, safeRadius)) {
            if (core.pos().distSqr(center) <= radiusSquared
                    && core.state() == TerritorySavedData.CoreState.EXPOSED && core.health() > 0) return true;
        }
        for (ReinforcementSavedData.LocatedEntry located : ReinforcementSavedData.get(level)
                .aroundUnordered(level, center, safeRadius)) {
            if (located.entry().enabled()) return true;
        }
        return false;
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
