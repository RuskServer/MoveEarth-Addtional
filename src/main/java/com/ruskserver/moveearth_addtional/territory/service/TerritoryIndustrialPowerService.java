package com.ruskserver.moveearth_addtional.territory.service;

import com.ruskserver.moveearth_addtional.territory.create.CreateStressCache;
import com.ruskserver.moveearth_addtional.territory.create.CreateStressSnapshot;
import com.ruskserver.moveearth_addtional.territory.data.TerritoryPowerSavedData;
import com.ruskserver.moveearth_addtional.territory.domain.TerritoryOwnerId;
import net.minecraft.server.MinecraftServer;

public final class TerritoryIndustrialPowerService {
    private TerritoryIndustrialPowerService() {
    }

    public static Breakdown get(MinecraftServer server, TerritoryOwnerId ownerId) {
        CreateStressSnapshot create = CreateStressCache.get(server, ownerId);
        double manualAdjustment = TerritoryPowerSavedData.get(server).industrialScore(ownerId);
        return new Breakdown(create, manualAdjustment, create.industrialScore() + manualAdjustment);
    }

    public record Breakdown(
            CreateStressSnapshot create,
            double manualAdjustment,
            double totalScore
    ) {
    }
}
