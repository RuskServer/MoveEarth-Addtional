package com.ruskserver.moveearth_addtional.s2.siege;

import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/** Resolves real-time nation inactivity without relying on server-open ticks. */
public final class LongAbsenceService {
    private LongAbsenceService() { }

    public static LongAbsencePolicy.Tier tier(ServerLevel level, TerritorySavedData.CoreRecord core) {
        return tier(level.getServer(), core.nationId());
    }

    public static LongAbsencePolicy.Tier tier(MinecraftServer server, UUID nationId) {
        NationSavedData.Nation nation = NationSavedData.get(server).nation(nationId).orElse(null);
        if (nation == null) return LongAbsencePolicy.Tier.FULL;
        boolean online = nation.members().keySet().stream()
                .anyMatch(member -> server.getPlayerList().getPlayer(member) != null);
        long lastSeenAt = nation.members().values().stream()
                .mapToLong(NationSavedData.Member::lastSeenAt).max().orElse(0L);
        long offlineMillis = lastSeenAt <= 0L ? Long.MAX_VALUE
                : Math.max(0L, System.currentTimeMillis() - lastSeenAt);
        return LongAbsencePolicy.tier(online, offlineMillis,
                S2TerritoryConfig.longAbsenceFullStrengthMillis(),
                S2TerritoryConfig.longAbsenceHalfStrengthMillis(),
                S2TerritoryConfig.longAbsenceQuarterStrengthMillis(),
                S2TerritoryConfig.longAbsenceDisableMillis());
    }

    public static LongAbsencePolicy.Tier tierAt(ServerLevel level, BlockPos pos) {
        return TerritorySavedData.get(level.getServer()).controllingNation(
                        level.getServer(), level.dimension().location(), pos)
                .map(nationId -> tier(level.getServer(), nationId))
                .orElse(LongAbsencePolicy.Tier.FULL);
    }
}
