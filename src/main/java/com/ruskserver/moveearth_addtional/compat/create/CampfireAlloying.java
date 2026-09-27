package com.ruskserver.moveearth_addtional.compat.create;

import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lets a lit campfire heat a basin for brass, and only brass.
 *
 * <p>Brass gates most of Create past andesite, yet its mixing recipe needs a
 * blaze burner, which this world has no Nether to supply. A campfire is the
 * early answer; every other heated recipe (cast iron, steel, cannon melting)
 * still wants a real heat source. Which outputs qualify is the
 * {@code moveearth_addtional:campfire_alloys} item tag.
 */
public final class CampfireAlloying {
    public static final TagKey<Item> CAMPFIRE_ALLOYS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("moveearth_addtional", "campfire_alloys"));

    private CampfireAlloying() {
    }

    public static boolean campfireLitUnder(BasinBlockEntity basin) {
        Level level = basin.getLevel();
        if (level == null) return false;
        BlockState below = level.getBlockState(basin.getBlockPos().below());
        return below.is(BlockTags.CAMPFIRES) && CampfireBlock.isLitCampfire(below);
    }

    public static boolean makesCampfireAlloy(BasinBlockEntity basin, Recipe<?> recipe) {
        Level level = basin.getLevel();
        return level != null && recipe.getResultItem(level.registryAccess()).is(CAMPFIRE_ALLOYS);
    }
}
