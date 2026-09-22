package com.ruskserver.moveearth_addtional.warehouse;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

import java.util.Optional;

/** Places exactly the selected anchor chunk for each eligible region. */
public final class WarehouseRegionPlacement extends StructurePlacement {
    private static final ResourceLocation VILLAGES = ResourceLocation.withDefaultNamespace("villages");
    public static final MapCodec<WarehouseRegionPlacement> CODEC = RecordCodecBuilder.mapCodec(
            instance -> placementCodec(instance).apply(instance, WarehouseRegionPlacement::new));

    private WarehouseRegionPlacement(Vec3i offset, FrequencyReductionMethod method, float frequency,
                                     int salt, Optional<ExclusionZone> exclusion) {
        super(offset, method, frequency, salt, exclusion);
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
        // The candidate map is prepared before worldgen and is safe to read on generation workers.
        WarehouseRegionAnchors.Anchor anchor = WarehouseRegionAnchors.forChunk(
                new net.minecraft.world.level.ChunkPos(chunkX, chunkZ));
        if (anchor == null) return false;
        var villages = state.possibleStructureSets().stream().filter(holder -> holder.unwrapKey()
                .map(key -> key.location().equals(VILLAGES)).orElse(false)).findFirst();
        return WarehouseRegionAnchors.villageSafe(anchor, candidate -> villages.isEmpty()
                || !state.hasStructureChunkInRange(villages.get(),
                candidate.chunk().x, candidate.chunk().z, 7));
    }

    @Override
    public StructurePlacementType<?> type() { return WarehouseWorldgen.PLACEMENT.get(); }
}
