package com.ruskserver.moveearth_addtional.warehouse;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

final class WarehousePiece extends TemplateStructurePiece {
    private static final ResourceLocation TEMPLATE = ResourceLocation.fromNamespaceAndPath(
            Moveearth_addtional.MODID, "warehouse");

    WarehousePiece(StructureTemplateManager manager, BlockPos min) {
        super(WarehouseWorldgen.PIECE.get(), 0, manager, TEMPLATE, TEMPLATE.toString(), settings(), min);
    }

    WarehousePiece(StructureTemplateManager manager, CompoundTag tag) {
        super(WarehouseWorldgen.PIECE.get(), tag, manager, ignored -> settings());
    }

    private static StructurePlaceSettings settings() {
        return new StructurePlaceSettings().addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunk, BlockPos pivot) {
        // On a gentle slope, support the warehouse instead of leaving its low edge floating.
        // The bounding box restricts writes to the chunk currently being generated.
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = templatePosition.getY();
        for (int x = Math.max(box.minX(), templatePosition.getX());
             x <= Math.min(box.maxX(), templatePosition.getX() + WarehouseSitePolicy.WIDTH - 1); x++) {
            for (int z = Math.max(box.minZ(), templatePosition.getZ());
                 z <= Math.min(box.maxZ(), templatePosition.getZ() + WarehouseSitePolicy.LENGTH - 1); z++) {
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                for (int y = surface; y < minY; y++) {
                    level.setBlock(pos.set(x, y, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                }
            }
        }
        super.postProcess(level, structures, generator, random, box, chunk, pivot);
    }

    @Override
    protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level,
                                    RandomSource random, BoundingBox box) { }
}
