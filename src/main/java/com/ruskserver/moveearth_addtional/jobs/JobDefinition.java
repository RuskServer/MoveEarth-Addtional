package com.ruskserver.moveearth_addtional.jobs;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Immutable, data-pack supplied rules for one job. */
public record JobDefinition(
        ResourceLocation id,
        String displayName,
        int maxLevel,
        int pointsPerLevel,
        int baseXp,
        int linearXp,
        int quadraticXp,
        List<BlockBreakReward> blockBreakRewards) {

    public JobDefinition {
        blockBreakRewards = List.copyOf(blockBreakRewards);
    }

    public long xpNeededForNextLevel(int level) {
        return JobProgression.xpNeededForNextLevel(level, maxLevel, baseXp, linearXp, quadraticXp);
    }

    public int blockBreakXp(BlockState state) {
        int reward = 0;
        for (BlockBreakReward candidate : blockBreakRewards) {
            if (state.is(candidate.tag())) {
                reward = Math.max(reward, candidate.xp());
            }
        }
        return reward;
    }

    public record BlockBreakReward(TagKey<Block> tag, int xp) {
        public static BlockBreakReward of(ResourceLocation tag, int xp) {
            return new BlockBreakReward(TagKey.create(Registries.BLOCK, tag), xp);
        }
    }
}
