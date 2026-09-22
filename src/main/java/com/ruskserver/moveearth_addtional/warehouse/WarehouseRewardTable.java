package com.ruskserver.moveearth_addtional.warehouse;

import com.ruskserver.moveearth_addtional.item.ModItems;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** The only source for generated warehouse contents; no boss equipment drops. */
public final class WarehouseRewardTable {
    public static final int PRECISION_ASSEMBLIES = 2;

    private WarehouseRewardTable() { }

    public static List<ItemStack> generate(int region, int cycle) {
        RandomSource random = RandomSource.create(0x4D4F564545415254L ^ ((long) region << 32) ^ cycle);
        ItemStack bonus = switch (random.nextInt(5)) {
            case 0 -> new ItemStack(Items.NETHER_WART, 2 + random.nextInt(3));
            case 1 -> new ItemStack(Items.QUARTZ, 4 + random.nextInt(5));
            case 2 -> new ItemStack(Items.GLOWSTONE_DUST, 3 + random.nextInt(4));
            case 3 -> new ItemStack(Items.BLAZE_ROD, 1);
            default -> new ItemStack(Items.GHAST_TEAR, 1);
        };
        return List.of(new ItemStack(ModItems.PRECISION_FIRING_ASSEMBLY.get(), PRECISION_ASSEMBLIES), bonus);
    }
}
