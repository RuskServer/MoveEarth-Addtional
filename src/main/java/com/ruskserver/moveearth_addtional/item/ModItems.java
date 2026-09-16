package com.ruskserver.moveearth_addtional.item;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.block.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, Moveearth_addtional.MODID);

    public static final DeferredHolder<Item, BlockItem> PLAYER_DETECTOR = ITEMS.register("player_detector",
            () -> new BlockItem(ModBlocks.PLAYER_DETECTOR.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> TERRITORY_CORE = ITEMS.register("territory_core",
            () -> new TerritoryCoreBlockItem(ModBlocks.TERRITORY_CORE.get(),
                    new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final DeferredHolder<Item, BlockItem> PRISON_INTAKE = ITEMS.register("prison_intake",
            () -> new BlockItem(ModBlocks.PRISON_INTAKE.get(), new Item.Properties()
                    .rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final DeferredHolder<Item, BlockItem> VEHICLE_CORE = ITEMS.register("vehicle_core",
            () -> new BlockItem(ModBlocks.VEHICLE_CORE.get(), new Item.Properties()
                    .rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final DeferredHolder<Item, Item> RESTRAINTS = ITEMS.register("restraints",
            () -> new Item(new Item.Properties().stacksTo(1).durability(64)
                    .rarity(net.minecraft.world.item.Rarity.UNCOMMON)));

    public static final DeferredHolder<Item, Item> WEAPON_CRATE = ITEMS.register("weapon_crate",
            () -> new WeaponCrateItem(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.RARE)));

    public static final DeferredHolder<Item, Item> GAS_MASK = ITEMS.register("gas_mask",
            () -> new com.ruskserver.moveearth_addtional.oxygen.GasMaskItem(new Item.Properties().durability(240).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));

    public static final DeferredHolder<Item, Item> CARBON_FILTER = ITEMS.register("carbon_filter",
            () -> new com.ruskserver.moveearth_addtional.oxygen.CarbonFilterItem(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.COMMON)));

    public static final DeferredHolder<Item, Item> WELDING_TOOL = ITEMS.register("welding_tool",
            () -> new WeldingToolItem(new Item.Properties().stacksTo(1).durability(512)
                    .rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
}
