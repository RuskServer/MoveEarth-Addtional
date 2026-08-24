package com.ruskserver.moveearth_addtional.jobs;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.List;

/** Immutable, data-pack supplied rules for one job. */
public record JobDefinition(
        ResourceLocation id,
        String displayName,
        String description,
        int maxLevel,
        int pointsPerLevel,
        int baseXp,
        int linearXp,
        int quadraticXp,
        List<BlockBreakReward> blockBreakRewards,
        List<EntityReward> entityKillRewards,
        List<EntityReward> entityBreedRewards,
        List<ItemCraftReward> itemCraftRewards) {

    public JobDefinition {
        blockBreakRewards = List.copyOf(blockBreakRewards);
        entityKillRewards = List.copyOf(entityKillRewards);
        entityBreedRewards = List.copyOf(entityBreedRewards);
        itemCraftRewards = List.copyOf(itemCraftRewards);
    }

    public long xpNeededForNextLevel(int level) {
        return JobProgression.xpNeededForNextLevel(level, maxLevel, baseXp, linearXp, quadraticXp);
    }

    public int blockBreakXp(BlockState state) {
        int reward = 0;
        for (BlockBreakReward candidate : blockBreakRewards) {
            if (candidate.matches(state)) {
                reward = Math.max(reward, candidate.xp());
            }
        }
        return reward;
    }

    public boolean tracksPlacement(BlockState state) {
        for (BlockBreakReward candidate : blockBreakRewards) {
            if (candidate.excludePlayerPlaced() && state.is(candidate.tag())) {
                return true;
            }
        }
        return false;
    }

    public int entityKillXp(EntityType<?> entityType) {
        return entityReward(entityKillRewards, entityType);
    }

    public int entityBreedXp(EntityType<?> entityType) {
        return entityReward(entityBreedRewards, entityType);
    }

    public int itemCraftXp(ItemStack result) {
        int reward = 0;
        for (ItemCraftReward candidate : itemCraftRewards) {
            if (result.is(candidate.tag())) {
                reward = Math.max(reward, candidate.xp());
            }
        }
        return reward;
    }

    private static int entityReward(List<EntityReward> rewards, EntityType<?> entityType) {
        int reward = 0;
        for (EntityReward candidate : rewards) {
            if (entityType.is(candidate.tag())) {
                reward = Math.max(reward, candidate.xp());
            }
        }
        return reward;
    }

    public record BlockBreakReward(TagKey<Block> tag, int xp, BlockCondition condition,
                                   boolean excludePlayerPlaced) {
        public static BlockBreakReward of(ResourceLocation tag, int xp, BlockCondition condition,
                                          boolean excludePlayerPlaced) {
            return new BlockBreakReward(TagKey.create(Registries.BLOCK, tag), xp,
                    condition, excludePlayerPlaced);
        }

        private boolean matches(BlockState state) {
            return state.is(tag) && (condition != BlockCondition.MATURE || isMature(state));
        }

        private static boolean isMature(BlockState state) {
            for (Property<?> property : state.getProperties()) {
                if (property instanceof IntegerProperty age && "age".equals(property.getName())) {
                    int current = state.getValue(age);
                    int maximum = age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(current);
                    return current >= maximum;
                }
            }
            return false;
        }
    }

    public record EntityReward(TagKey<EntityType<?>> tag, int xp) {
        public static EntityReward of(ResourceLocation tag, int xp) {
            return new EntityReward(TagKey.create(Registries.ENTITY_TYPE, tag), xp);
        }
    }

    public record ItemCraftReward(TagKey<Item> tag, int xp) {
        public static ItemCraftReward of(ResourceLocation tag, int xp) {
            return new ItemCraftReward(TagKey.create(Registries.ITEM, tag), xp);
        }
    }

    public enum BlockCondition {
        ANY,
        MATURE
    }
}
