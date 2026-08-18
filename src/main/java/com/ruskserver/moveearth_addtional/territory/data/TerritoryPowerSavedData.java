package com.ruskserver.moveearth_addtional.territory.data;

import com.ruskserver.moveearth_addtional.territory.domain.TerritoryOwnerId;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

public final class TerritoryPowerSavedData extends SavedData {
    private static final String DATA_NAME = "moveearth_territory_power";
    private final IndustrialScoreRegistry industrialScores = new IndustrialScoreRegistry();

    public double industrialScore(TerritoryOwnerId ownerId) {
        return industrialScores.get(ownerId);
    }

    public void setIndustrialScore(TerritoryOwnerId ownerId, double score) {
        if (industrialScores.set(ownerId, score)) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag scoresTag = new CompoundTag();
        industrialScores.entries().forEach((ownerId, score) ->
                scoresTag.putDouble(ownerId.value().toString(), score));
        tag.put("IndustrialScores", scoresTag);
        return tag;
    }

    public static TerritoryPowerSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TerritoryPowerSavedData data = new TerritoryPowerSavedData();
        CompoundTag scoresTag = tag.getCompound("IndustrialScores");
        for (String key : scoresTag.getAllKeys()) {
            try {
                double score = scoresTag.getDouble(key);
                if (Double.isFinite(score) && score >= 0.0D) {
                    data.industrialScores.set(TerritoryOwnerId.of(UUID.fromString(key)), score);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed owner IDs from manually edited or older data.
            }
        }
        return data;
    }

    public static TerritoryPowerSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        TerritoryPowerSavedData::new,
                        TerritoryPowerSavedData::load,
                        null
                ),
                DATA_NAME
        );
    }
}
