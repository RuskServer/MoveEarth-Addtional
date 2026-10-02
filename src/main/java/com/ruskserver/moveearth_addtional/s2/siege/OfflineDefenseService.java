package com.ruskserver.moveearth_addtional.s2.siege;

import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Applies nation-presence defense without changing stored maximum HP. */
public final class OfflineDefenseService {
    private OfflineDefenseService() { }

    public static OfflineDefensePolicy.DamageResult scale(ServerLevel level, BlockPos pos, int rawDamage) {
        var vehicle = com.ruskserver.moveearth_addtional.s2.vehicle.VehicleProtection.vehicleAt(level, pos);
        if (vehicle != null) return scaleVehicle(level, pos, rawDamage, vehicle);
        TerritorySavedData territories = TerritorySavedData.get(level.getServer());
        SiegeSavedData sieges = SiegeSavedData.get(level.getServer());
        boolean settlementProtected = territories.reservedCores(level.dimension().location(), pos).stream()
                .anyMatch(reserved -> sieges.isNationSettlementProtected(reserved.nationId())
                        || sieges.isCoreSettlementProtected(reserved.id()));
        if (!settlementProtected) {
            settlementProtected = territories.controllingNation(
                            level.getServer(), level.dimension().location(), pos)
                    .map(sieges::isNationSettlementProtected).orElse(false);
        }
        if (settlementProtected) {
            sieges.clearOfflineDamageCarry(level.dimension().location(), pos);
            return OfflineDefensePolicy.applyRatio(rawDamage, 0, 1, 0);
        }
        TerritorySavedData.CoreRecord core = territories
                .controllingCore(level.getServer(), level.dimension().location(), pos).orElse(null);
        if (core == null) {
            sieges.clearOfflineDamageCarry(level.dimension().location(), pos);
            return OfflineDefensePolicy.apply(rawDamage, 1, 0);
        }
        LongAbsencePolicy.Tier absence = LongAbsenceService.tier(level, core);
        if (absence.weakened()) {
            return sieges.applyScaledDamage(level.dimension().location(), pos, rawDamage,
                    absence.damageNumerator(), absence.damageDenominator());
        }
        int divisor = divisor(level, core);
        return sieges.applyScaledDamage(level.dimension().location(), pos, rawDamage, 1, divisor);
    }

    /**
     * Vehicle armour and cores: while the vehicle physically stands in its nation's own territory, the owner's
     * rebuilding truce refuses damage and the owner's offline defense applies (with that core's rolling-Siege
     * suppression). Elsewhere a vehicle takes full damage, truce or not.
     */
    private static OfflineDefensePolicy.DamageResult scaleVehicle(
            ServerLevel level, BlockPos pos, int rawDamage,
            com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData.VehicleRecord vehicle) {
        SiegeSavedData sieges = SiegeSavedData.get(level.getServer());
        if (com.ruskserver.moveearth_addtional.s2.vehicle.VehicleProtection.settlementTruceApplies(level, vehicle, pos)) {
            sieges.clearOfflineDamageCarry(level.dimension().location(), pos);
            return OfflineDefensePolicy.applyRatio(rawDamage, 0, 1, 0);
        }
        int divisor = com.ruskserver.moveearth_addtional.s2.vehicle.VehicleProtection.offlineDivisor(level, vehicle, pos);
        if (divisor <= 1) {
            sieges.clearOfflineDamageCarry(level.dimension().location(), pos);
            return OfflineDefensePolicy.apply(rawDamage, 1, 0);
        }
        return sieges.applyScaledDamage(level.dimension().location(), pos, rawDamage, 1, divisor);
    }

    public static int divisor(ServerLevel level, TerritorySavedData.CoreRecord core) {
        int baseDivisor = baseDivisor(level, core);
        SiegeSavedData sieges = SiegeSavedData.get(level.getServer());
        boolean fallen = sieges.isCoreFallen(core.id());
        boolean rolling = !fallen && sieges.isCoreRegenPaused(core.id());
        boolean suppressed = OfflineDefenseDayPolicy.suppressed(fallen, rolling,
                OfflineDefenseDaySavedData.get(level.getServer())
                        .allowedOn(core.id(), OfflineDefenseDaySavedData.today()));
        return suppressed ? 1 : baseDivisor;
    }

    /** Presence-only state used when a new Siege snapshots whether offline protection was already active. */
    public static int baseDivisor(ServerLevel level, TerritorySavedData.CoreRecord core) {
        NationSavedData nations = NationSavedData.get(level.getServer());
        NationSavedData.Nation nation = nations.nation(core.nationId()).orElse(null);
        if (nation == null) return 1;
        boolean online = nation.members().keySet().stream()
                .anyMatch(member -> level.getServer().getPlayerList().getPlayer(member) != null);
        long lastSeenAt = nation.members().values().stream()
                .mapToLong(NationSavedData.Member::lastSeenAt).max().orElse(0L);
        long offlineMillis = lastSeenAt <= 0L ? Long.MAX_VALUE
                : Math.max(0L, System.currentTimeMillis() - lastSeenAt);
        return OfflineDefensePolicy.divisor(online, offlineMillis,
                S2TerritoryConfig.offlineDefenseGraceMillis(), false,
                S2TerritoryConfig.offlineDefenseDamageDivisor());
    }

    public static boolean settlementProtected(ServerLevel level, BlockPos pos) {
        SiegeSavedData sieges = SiegeSavedData.get(level.getServer());
        TerritorySavedData territories = TerritorySavedData.get(level.getServer());
        return territories.reservedCores(level.dimension().location(), pos).stream()
                .anyMatch(core -> sieges.isNationSettlementProtected(core.nationId())
                        || sieges.isCoreSettlementProtected(core.id()))
                || territories.controllingNation(level.getServer(), level.dimension().location(), pos)
                .map(sieges::isNationSettlementProtected).orElse(false);
    }

}
