package com.ruskserver.moveearth_addtional.warehouse;

import com.ruskserver.moveearth_addtional.item.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** The only source for generated warehouse contents; no boss equipment drops. */
public final class WarehouseRewardTable {
    public static final int PRECISION_ASSEMBLIES = 2;
    private static final ResourceLocation BLAZE_BURNER_ID = ResourceLocation.fromNamespaceAndPath(
            "create", "blaze_burner");

    private WarehouseRewardTable() { }

    /**
     * One line of the table.
     *
     * <p>The counts live here rather than inside {@link #generate} so that what a
     * player is shown and what a warehouse actually holds cannot drift apart. A
     * second copy of these numbers for display would be wrong the first time one
     * of them was tuned, and nothing would fail — the display would simply lie.
     *
     * @param item the item dropped; may be {@link Items#AIR} when the mod that
     *             owns it is absent, which is why callers ask {@link #exists()}
     * @param min  the smallest count a cycle can yield
     * @param max  the largest, equal to {@code min} for a fixed drop
     */
    public record Drop(Item item, int min, int max) {
        /** False when the owning mod is not installed and the id resolved to air. */
        public boolean exists() { return item != Items.AIR; }

        /** Whether the count varies, and so needs explaining rather than just showing. */
        public boolean varies() { return max > min; }

        /** The count a player is guaranteed, which is what a display shows. */
        public ItemStack least() { return new ItemStack(item, min); }

        ItemStack roll(RandomSource random) {
            return new ItemStack(item, varies() ? min + random.nextInt(max - min + 1) : min);
        }
    }

    public static Item blazeBurner() {
        return BuiltInRegistries.ITEM.get(BLAZE_BURNER_ID);
    }

    /** What every cleared warehouse yields, whatever the draw. */
    public static List<Drop> guaranteedDrops() {
        return List.of(new Drop(ModItems.PRECISION_FIRING_ASSEMBLY.get(),
                PRECISION_ASSEMBLIES, PRECISION_ASSEMBLIES));
    }

    /**
     * The bonus pool, exactly one of which is drawn per cycle.
     *
     * <p>Built on each call rather than held in a static field: the Create entry
     * is looked up by id, and a field would freeze whatever the item registry
     * held the moment this class was first touched.
     */
    public static List<Drop> bonusDrops() {
        return List.of(
                new Drop(Items.NETHER_WART, 2, 4),
                new Drop(Items.QUARTZ, 4, 8),
                new Drop(Items.GLOWSTONE_DUST, 3, 6),
                new Drop(Items.BLAZE_ROD, 1, 1),
                new Drop(Items.GHAST_TEAR, 1, 1),
                new Drop(blazeBurner(), 1, 1));
    }

    public static List<ItemStack> generate(int region, int cycle) {
        RandomSource random = RandomSource.create(0x4D4F564545415254L ^ ((long) region << 32) ^ cycle);
        List<Drop> pool = bonusDrops();
        Drop bonus = pool.get(random.nextInt(pool.size()));
        List<ItemStack> contents = new ArrayList<>();
        for (Drop drop : guaranteedDrops()) {
            contents.add(drop.least());
        }
        contents.add(bonus.roll(random));
        return List.copyOf(contents);
    }
}
