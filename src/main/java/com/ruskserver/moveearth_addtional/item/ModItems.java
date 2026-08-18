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
            () -> new BlockItem(ModBlocks.TERRITORY_CORE.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> WEAPON_CRATE = ITEMS.register("weapon_crate",
            () -> new WeaponCrateItem(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.RARE)));
}
