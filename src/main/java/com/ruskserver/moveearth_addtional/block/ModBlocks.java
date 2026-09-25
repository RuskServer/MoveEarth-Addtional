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
                    .strength(12.0F, 1200.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 9)
                    .requiresCorrectToolForDrops()
            ));
    public static final DeferredHolder<Block, PrisonIntakeBlock> PRISON_INTAKE = BLOCKS.register("prison_intake",
            () -> new PrisonIntakeBlock(BlockBehaviour.Properties.of()
                    .strength(8.0F, 1200.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredHolder<Block, VehicleCoreBlock> VEHICLE_CORE = BLOCKS.register("vehicle_core",
            () -> new VehicleCoreBlock(BlockBehaviour.Properties.of()
                    .strength(10.0F, 1200.0F).sound(SoundType.METAL)
                    .lightLevel(state -> 6).requiresCorrectToolForDrops()));
    /**
     * Ore deposits for the two strategic resources Create: Rock & Stone has none of.
     *
     * <p>The mod ships deposits for the base and industrial metals but not for
     * diamond or emerald, so without these the exclusive tier has only gold to
     * work with and seven of the eight regions would have nothing to trade.
     *
     * <p>A plain block, on purpose. Every check the mod makes is on the
     * {@code create_rns:deposit_blocks} tag and never on its own class, so this
     * needs no Rock & Stone type and the mod stays an optional dependency: a
     * server without it simply has two unused blocks rather than a class that
     * fails to load. Hardness and blast resistance match the mod's own deposits
     * so a miner behaves the same on these.
     */
    public static final DeferredHolder<Block, Block> DIAMOND_DEPOSIT = BLOCKS.register(
            "diamond_deposit_block", () -> new Block(depositProperties()));
    public static final DeferredHolder<Block, Block> EMERALD_DEPOSIT = BLOCKS.register(
            "emerald_deposit_block", () -> new Block(depositProperties()));

    private static BlockBehaviour.Properties depositProperties() {
        return BlockBehaviour.Properties.of()
                .strength(50.0F, 1200.0F)
                .sound(SoundType.DEEPSLATE)
                .requiresCorrectToolForDrops();
    }

    public static final DeferredHolder<Block, StorageWreckageBlock> STORAGE_WRECKAGE = BLOCKS.register("storage_wreckage",
            () -> new StorageWreckageBlock(BlockBehaviour.Properties.of()
                    .strength(12.0F, 1200.0F).sound(SoundType.METAL).noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));
    public static final DeferredHolder<Block, MarketStationBlock> MARKET_STATION = BLOCKS.register("market_station",
            () -> new MarketStationBlock(BlockBehaviour.Properties.of()
                    .strength(8.0F, 1200.0F).sound(SoundType.METAL).noOcclusion()
                    .requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK)));
}
