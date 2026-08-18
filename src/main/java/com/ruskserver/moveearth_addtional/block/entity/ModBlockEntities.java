package com.ruskserver.moveearth_addtional.block.entity;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.block.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Moveearth_addtional.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlayerDetectorBlockEntity>> PLAYER_DETECTOR = BLOCK_ENTITIES.register("player_detector",
            () -> BlockEntityType.Builder.of(PlayerDetectorBlockEntity::new, ModBlocks.PLAYER_DETECTOR.get()).build(null));
}
