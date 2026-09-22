package com.ruskserver.moveearth_addtional.warehouse;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/** One multichunk template, generated before vegetation. */
public final class WarehouseStructure extends Structure {
    public static final MapCodec<WarehouseStructure> CODEC = simpleCodec(WarehouseStructure::new);

    public WarehouseStructure(StructureSettings settings) { super(settings); }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        var store = com.ruskserver.moveearth_addtional.terrain.TerrainTileStore.active();
        if (store == null) return Optional.empty();
        WarehouseRegionAnchors.Anchor candidate = WarehouseRegionAnchors.forChunk(context.chunkPos());
        if (candidate == null) return Optional.empty();
        WarehouseRegionAnchors.Anchor anchor = WarehouseRegionAnchors.resolve(candidate.region(),
                choice -> surfaceRange(context, choice).suitable());
        if (anchor == null || !anchor.chunk().equals(context.chunkPos())) return Optional.empty();
        var tile = store.tileAt(anchor.minX(), anchor.minZ());
        if (tile == null) return Optional.empty();
        SurfaceRange range = surfaceRange(context, anchor);
        BlockPos min = new BlockPos(anchor.minX(), range.high(), anchor.minZ());
        return Optional.of(new GenerationStub(min,
                pieces -> pieces.addPiece(new WarehousePiece(context.structureTemplateManager(), min))));
    }

    private static SurfaceRange surfaceRange(GenerationContext context, WarehouseRegionAnchors.Anchor anchor) {
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int dx : new int[] {0, WarehouseSitePolicy.WIDTH / 2, WarehouseSitePolicy.WIDTH - 1}) {
            for (int dz : new int[] {0, WarehouseSitePolicy.LENGTH / 2, WarehouseSitePolicy.LENGTH - 1}) {
                int y = context.chunkGenerator().getFirstOccupiedHeight(anchor.minX() + dx,
                        anchor.minZ() + dz, Heightmap.Types.WORLD_SURFACE_WG,
                        context.heightAccessor(), context.randomState());
                low = Math.min(low, y);
                high = Math.max(high, y);
            }
        }
        var store = com.ruskserver.moveearth_addtional.terrain.TerrainTileStore.active();
        var tile = store == null ? null : store.tileAt(anchor.minX(), anchor.minZ());
        return new SurfaceRange(high, tile != null && low >= tile.seaY() + 4 && high - low <= 10
                && high + WarehouseSitePolicy.HEIGHT < context.heightAccessor().getMaxBuildHeight());
    }

    private record SurfaceRange(int high, boolean suitable) { }

    @Override
    public StructureType<?> type() { return WarehouseWorldgen.STRUCTURE.get(); }
}
