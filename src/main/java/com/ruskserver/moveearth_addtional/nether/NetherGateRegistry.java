package com.ruskserver.moveearth_addtional.nether;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.item.ModCreativeModeTabs;
import com.simibubi.create.api.stress.BlockStressValues;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The gate generator, the blaze rod refiner and the Nether shard. */
public final class NetherGateRegistry {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(BuiltInRegistries.BLOCK, Moveearth_addtional.MODID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, Moveearth_addtional.MODID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Moveearth_addtional.MODID);

    public static final DeferredHolder<Block, GateGeneratorBlock> GATE_GENERATOR = BLOCKS.register("gate_generator",
            () -> new GateGeneratorBlock(BlockBehaviour.Properties.of()
                    .strength(6.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK)
                    .requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK)));
    public static final DeferredHolder<Block, BlazeRodRefinerBlock> BLAZE_ROD_REFINER =
            BLOCKS.register("blaze_processing_machine", () -> new BlazeRodRefinerBlock(BlockBehaviour.Properties.of()
                    .strength(4.0F, 6.0F).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops()));

    public static final DeferredHolder<Item, BlockItem> GATE_GENERATOR_ITEM = ITEMS.register("gate_generator",
            () -> new BlockItem(GATE_GENERATOR.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> BLAZE_ROD_REFINER_ITEM =
            ITEMS.register("blaze_processing_machine",
                    () -> new BlockItem(BLAZE_ROD_REFINER.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> NETHER_SHARD = ITEMS.register("nether_shard",
            () -> new Item(new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GateGeneratorBlockEntity>>
            GATE_GENERATOR_ENTITY = ENTITIES.register("gate_generator",
            () -> BlockEntityType.Builder.of((pos, state) -> new GateGeneratorBlockEntity(
                    NetherGateRegistry.GATE_GENERATOR_ENTITY.get(), pos, state), GATE_GENERATOR.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlazeRodRefinerBlockEntity>>
            BLAZE_ROD_REFINER_ENTITY = ENTITIES.register("blaze_processing_machine",
            () -> BlockEntityType.Builder.of((pos, state) -> new BlazeRodRefinerBlockEntity(
                    NetherGateRegistry.BLAZE_ROD_REFINER_ENTITY.get(), pos, state), BLAZE_ROD_REFINER.get()).build(null));

    private NetherGateRegistry() { }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        bus.addListener(NetherGateRegistry::setup);
        bus.addListener(NetherGateRegistry::capabilities);
        bus.addListener(NetherGateRegistry::creativeTab);
    }

    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            BlockStressValues.IMPACTS.register(GATE_GENERATOR.get(), NetherGateConfig::gateStressImpact);
            BlockStressValues.IMPACTS.register(BLAZE_ROD_REFINER.get(), NetherGateConfig::refinerStressImpact);
        });
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BLAZE_ROD_REFINER_ENTITY.get(),
                (refiner, side) -> refiner.automationHandler());
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != ModCreativeModeTabs.MOVEEARTH_TAB.getKey()) return;
        event.accept(GATE_GENERATOR_ITEM.get());
        event.accept(BLAZE_ROD_REFINER_ITEM.get());
        event.accept(NETHER_SHARD.get());
    }
}
