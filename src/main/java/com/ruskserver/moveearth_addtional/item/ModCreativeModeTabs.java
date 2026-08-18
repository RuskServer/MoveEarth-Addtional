package com.ruskserver.moveearth_addtional.item;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Moveearth_addtional.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MOVEEARTH_TAB =
            CREATIVE_MODE_TABS.register("moveearth_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.moveearth_addtional"))
                    .icon(() -> new ItemStack(ModItems.PLAYER_DETECTOR.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.PLAYER_DETECTOR.get());
                        output.accept(ModItems.TERRITORY_CORE.get());
                        output.accept(ModItems.TERRITORY_RAID.get());
                    })
                    .build());
}
