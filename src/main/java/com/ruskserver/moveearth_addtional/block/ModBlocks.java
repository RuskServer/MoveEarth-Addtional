package com.ruskserver.moveearth_addtional.block;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, Moveearth_addtional.MODID);

    public static final DeferredHolder<Block, PlayerDetectorBlock> PLAYER_DETECTOR = BLOCKS.register("player_detector",
            () -> new PlayerDetectorBlock(BlockBehaviour.Properties.of()
                    .strength(3.0F, 3.0F)
                    .sound(SoundType.METAL)
            ));

    public static final DeferredHolder<Block, TerritoryCoreBlock> TERRITORY_CORE = BLOCKS.register("territory_core",
            () -> new TerritoryCoreBlock(BlockBehaviour.Properties.of()
                    .strength(8.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK)
            ));

    public static final DeferredHolder<Block, TerritoryRaidBlock> TERRITORY_RAID = BLOCKS.register("territory_raid",
            () -> new TerritoryRaidBlock(BlockBehaviour.Properties.of()
                    .strength(6.0F, 12.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            ));
}
