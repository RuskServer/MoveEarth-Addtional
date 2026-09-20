package com.ruskserver.moveearth_addtional.compat.rns;

import com.bmaster.createrns.content.deposit.mining.IDepositBlockMiner;
import com.bmaster.createrns.content.deposit.mining.InnerProcess;
import com.bmaster.createrns.content.deposit.mining.MiningProcess;
import com.bmaster.createrns.content.deposit.mining.recipe.MiningRecipe;
import com.bmaster.createrns.content.deposit.mining.recipe.Yield;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.RegionResourceConfig;
import com.ruskserver.moveearth_addtional.region.MaterialResolver;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Makes a mining rig cost more to turn when it is working something valuable.
 *
 * <p>Create: Rock &amp; Stone charges one fixed impact for the bearing whatever
 * it is mining, which leaves the resource tiers with no consequence in the
 * power network: a rig on a diamond deposit and a rig on a coal deposit ask the
 * same of a windmill. That is a real gap next to what this project's resource
 * design is trying to say, so the impact is made to follow the work.
 *
 * <p>Two things raise it, and deliberately both. Each deposit block under the
 * mine head adds to the load, so widening a 1x1 head to 3x3 costs power as well
 * as materials — which is how the rest of Create reads. And a deposit of an
 * exclusive material counts for more than a common one, so the resources a
 * region fights over are also the ones that demand an industry behind them.
 *
 * <p>Deposits are named from the items their mining recipe actually yields,
 * through the same convention tags every other resource here is named by. A
 * deposit block nobody can name simply counts as common.
 */
public final class RnsMinerStress {

    /** Deposit block to the material it yields. Immutable once published. */
    private static volatile Map<Block, String> depositMaterials = Map.of();

    private RnsMinerStress() { }

    /**
     * The impact a bearing should apply for what it is currently mining.
     *
     * <p>Returns the configured base when nothing is being worked, so an idle
     * rig costs what the mod always charged.
     */
    public static float impactFor(IDepositBlockMiner miner) {
        float base = (float) RegionResourceConfig.stressBase();
        if (!RegionResourceConfig.stressFollowsDeposit() || miner == null) {
            return base;
        }
        MiningProcess process = miner.getProcess();
        if (process == null) {
            return base;
        }
        Set<String> exclusive = Set.copyOf(RegionResourceConfig.exclusiveMaterials());
        double perDeposit = RegionResourceConfig.stressPerDeposit();
        double exclusiveFactor = RegionResourceConfig.stressExclusiveMultiplier();
        double added = 0.0;
        for (InnerProcess inner : process.innerProcesses) {
            if (inner.recipe == null) {
                continue;
            }
            String material = depositMaterials.get(inner.recipe.getDepositBlock());
            // Each block under the head is a separate load, so a wider head is
            // a heavier one even on the same resource.
            int blocks = Math.max(1, inner.depositPositions.size());
            added += perDeposit * blocks
                    * (material != null && exclusive.contains(material) ? exclusiveFactor : 1.0);
        }
        return (float) (base + added);
    }

    /** Resolves every deposit block's material from what its recipe yields. */
    public static void rebuild(MinecraftServer server) {
        Map<String, String> overrides =
                MaterialResolver.parseOverrides(RegionResourceConfig.materialOverrides());
        Map<Block, String> resolved = new IdentityHashMap<>();
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof MiningRecipe recipe)) {
                continue;
            }
            materialOf(recipe, overrides).ifPresent(
                    material -> resolved.put(recipe.getDepositBlock(), material));
        }
        depositMaterials = resolved;
        Moveearth_addtional.LOGGER.info(
                "Miner stress: {} deposit block(s) named; the rest cost the common rate.",
                resolved.size());
    }

    /** Forgets what it knew. */
    public static void clear() {
        depositMaterials = Map.of();
    }

    /**
     * The material a mining recipe is for, from its yields in order.
     *
     * <p>The first yield is the spoil — cobblestone and the like — and names
     * nothing, so the first item that does name something is the resource. That
     * is the same rule the ore veins are read by, for the same reason: a recipe
     * lists what it is before what merely falls out of it.
     */
    private static java.util.Optional<String> materialOf(MiningRecipe recipe,
                                                         Map<String, String> overrides) {
        List<String> ids = new ArrayList<>();
        List<List<String>> tags = new ArrayList<>();
        for (Yield yield : recipe.getYields()) {
            for (Yield.WeightedItem weighted : yield.items) {
                if (weighted.item == null) {
                    continue;
                }
                ids.add(String.valueOf(BuiltInRegistries.ITEM.getKey(weighted.item)));
                tags.add(weighted.item.builtInRegistryHolder().tags()
                        .map(TagKey::location).map(Object::toString).toList());
            }
        }
        return MaterialResolver.veinMaterial(ids, tags, overrides);
    }
}
