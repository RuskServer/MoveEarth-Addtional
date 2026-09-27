package com.ruskserver.moveearth_addtional.compat.create;

import com.ruskserver.moveearth_addtional.config.CreateIndustryConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.Tags;

/**
 * Slows Create's fan processing of ore, so electric smelting is worth building.
 *
 * <p>A lava fan over a belt smelts thousands of items a minute for no running
 * cost, several times what a fully upgraded Mekanism smelting factory manages.
 * Only ore-derived inputs are slowed. Washing is covered as well as blasting,
 * because washing crushed raw ore yields nuggets without any smelting.
 */
public final class FanOreProcessing {
    private static final TagKey<Item> CRUSHED_RAW_MATERIALS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("create", "crushed_raw_materials"));

    private FanOreProcessing() {
    }

    /** Fan processing time for {@code stack}, given Create's configured time. */
    public static int processingTime(int configuredTicks, ItemStack stack) {
        if (!CreateIndustryConfig.fanOreEnabled() || !isOre(stack)) return configuredTicks;
        return FanOreProcessingMath.scaledTicks(configuredTicks, CreateIndustryConfig.fanOreTimeMultiplier());
    }

    private static boolean isOre(ItemStack stack) {
        return stack.is(Tags.Items.ORES)
                || stack.is(Tags.Items.RAW_MATERIALS)
                || stack.is(CRUSHED_RAW_MATERIALS)
                || stack.is(Tags.Items.DUSTS);
    }
}
