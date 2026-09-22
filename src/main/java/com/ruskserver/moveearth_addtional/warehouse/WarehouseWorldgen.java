package com.ruskserver.moveearth_addtional.warehouse;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Native structure generation for newly generated overworld chunks. */
public final class WarehouseWorldgen {
    public static final DeferredRegister<StructureType<?>> STRUCTURES =
            DeferredRegister.create(BuiltInRegistries.STRUCTURE_TYPE, Moveearth_addtional.MODID);
    public static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(BuiltInRegistries.STRUCTURE_PIECE, Moveearth_addtional.MODID);
    public static final DeferredRegister<StructurePlacementType<?>> PLACEMENTS =
            DeferredRegister.create(BuiltInRegistries.STRUCTURE_PLACEMENT, Moveearth_addtional.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<WarehouseStructure>> STRUCTURE =
            STRUCTURES.register("warehouse", () -> () -> WarehouseStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> PIECE =
            PIECES.register("warehouse", () ->
                    (context, tag) -> new WarehousePiece(context.structureTemplateManager(), tag));
    public static final DeferredHolder<StructurePlacementType<?>, StructurePlacementType<WarehouseRegionPlacement>> PLACEMENT =
            PLACEMENTS.register("warehouse_region", () -> () -> WarehouseRegionPlacement.CODEC);

    private WarehouseWorldgen() { }
}
