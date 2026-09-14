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

    public static int divisor(ServerLevel level, TerritorySavedData.CoreRecord core) {
        int baseDivisor = baseDivisor(level, core);
        boolean suppressed = SiegeSavedData.get(level.getServer()).isOfflineDefenseSuppressed(core.id());
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
