package com.ruskserver.moveearth_addtional.territory.service;

import com.ruskserver.moveearth_addtional.territory.data.TerritoryCoreSavedData;
import com.ruskserver.moveearth_addtional.territory.data.TerritoryPowerSavedData;
import com.ruskserver.moveearth_addtional.territory.domain.DistanceModel;
import com.ruskserver.moveearth_addtional.territory.domain.InfluenceEngine;
import com.ruskserver.moveearth_addtional.territory.domain.InfluenceResult;
import com.ruskserver.moveearth_addtional.territory.domain.InfluenceSettings;
import com.ruskserver.moveearth_addtional.territory.domain.InfluenceSource;
import com.ruskserver.moveearth_addtional.territory.domain.ProtectionAction;
import com.ruskserver.moveearth_addtional.territory.domain.RaidEmitter;
import com.ruskserver.moveearth_addtional.territory.domain.TerritoryPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class TerritoryInfluenceService {
    private static final InfluenceEngine ENGINE = new InfluenceEngine();
    private static final InfluenceSettings SETTINGS = new InfluenceSettings(
            64.0D,
            1.0D,
            0.25D,
            5.0D,
            128.0D,
            DistanceModel.SPHERE_3D,
            Map.of(
                    ProtectionAction.CONTAINER_ACCESS, 16.0D,
                    ProtectionAction.PLAYER_DAMAGE, 32.0D,
                    ProtectionAction.BLOCK_MODIFICATION, 48.0D,
                    ProtectionAction.SABLE_DAMAGE, 64.0D
            )
    );

    private TerritoryInfluenceService() {
    }

    public static InfluenceResult evaluate(ServerLevel level, BlockPos pos) {
        return evaluate(level, pos, List.of());
    }

    public static InfluenceResult evaluate(ServerLevel level, BlockPos pos, Collection<RaidEmitter> raids) {
        String dimensionId = level.dimension().location().toString();
        TerritoryPowerSavedData powerData = TerritoryPowerSavedData.get(level.getServer());
        List<InfluenceSource> sources = TerritoryCoreSavedData.get(level.getServer())
                .coresIn(dimensionId)
                .stream()
                .map(core -> new InfluenceSource(core, powerData.industrialScore(core.ownerId())))
                .toList();
        TerritoryPosition query = new TerritoryPosition(
                dimensionId,
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D
        );
        return ENGINE.evaluate(query, sources, raids, SETTINGS);
    }

    public static InfluenceSettings settings() {
        return SETTINGS;
    }
}
