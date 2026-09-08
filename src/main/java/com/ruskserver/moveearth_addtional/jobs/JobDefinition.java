package com.ruskserver.moveearth_addtional.jobs;

import net.minecraft.core.registries.BuiltInRegistries;
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
        List<ItemCraftReward> itemCraftRewards,
        double gunCraftXp,
        double attachmentCraftXp,
        double gunDisassemblyXp) {

    public JobDefinition {
        blockBreakRewards = List.copyOf(blockBreakRewards);
        entityKillRewards = List.copyOf(entityKillRewards);
        entityBreedRewards = List.copyOf(entityBreedRewards);
        itemCraftRewards = List.copyOf(itemCraftRewards);
    }

    public long xpNeededForNextLevel(int level) {
        return JobProgression.xpNeededForNextLevel(level, maxLevel, baseXp, linearXp, quadraticXp);
    }

    public double blockBreakXp(BlockState state) {
        double reward = 0;
        for (BlockBreakReward candidate : blockBreakRewards) {
            if (candidate.matches(state)) {
                reward = Math.max(reward, candidate.xp());
            }
        }
        return reward;
    }

    public boolean tracksPlacement(BlockState state) {
        for (BlockBreakReward candidate : blockBreakRewards) {
            if (candidate.excludePlayerPlaced() && candidate.matchesBlock(state)) {
                return true;
            }
        }
        return false;
    }

    public double entityKillXp(EntityType<?> entityType) {
        return entityReward(entityKillRewards, entityType);
    }

    public double entityBreedXp(EntityType<?> entityType) {
        return entityReward(entityBreedRewards, entityType);
    }

    public double itemCraftXp(ItemStack result) {
        double reward = 0;
        for (ItemCraftReward candidate : itemCraftRewards) {
            if (result.is(candidate.tag())) {
                reward = Math.max(reward, candidate.xp());
            }
        }
        return reward;
    }

    public double gunCraftXp(ItemStack result) {
        return gunCraftXp > 0 && com.tacz.guns.api.item.IGun.getIGunOrNull(result) != null ? gunCraftXp : 0.0D;
    }

    public double attachmentCraftXp(ItemStack result) {
        return attachmentCraftXp > 0 && com.tacz.guns.api.item.IAttachment.getIAttachmentOrNull(result) != null
                ? attachmentCraftXp : 0.0D;
    }

    private static double entityReward(List<EntityReward> rewards, EntityType<?> entityType) {
        double reward = 0;
        for (EntityReward candidate : rewards) {
            if (entityType.is(candidate.tag())) {
                reward = Math.max(reward, candidate.xp());
            }
        }
        return reward;
    }

    public record BlockBreakReward(TagKey<Block> tag, ResourceLocation blockId, double xp, BlockCondition condition,
                                   boolean excludePlayerPlaced) {
        public static BlockBreakReward ofTag(ResourceLocation tag, double xp, BlockCondition condition,
                                             boolean excludePlayerPlaced) {
            return new BlockBreakReward(TagKey.create(Registries.BLOCK, tag), null, xp,
                    condition, excludePlayerPlaced);
        }

        public static BlockBreakReward ofBlock(ResourceLocation blockId, double xp, BlockCondition condition,
                                               boolean excludePlayerPlaced) {
            return new BlockBreakReward(null, blockId, xp, condition, excludePlayerPlaced);
        }

        private boolean matches(BlockState state) {
            return matchesBlock(state) && (condition != BlockCondition.MATURE || isMature(state));
        }

        private boolean matchesBlock(BlockState state) {
            return tag != null ? state.is(tag)
                    : blockId != null && blockId.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
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

    public record EntityReward(TagKey<EntityType<?>> tag, double xp) {
        public static EntityReward of(ResourceLocation tag, double xp) {
            return new EntityReward(TagKey.create(Registries.ENTITY_TYPE, tag), xp);
        }
    }

    public record ItemCraftReward(TagKey<Item> tag, double xp) {
        public static ItemCraftReward of(ResourceLocation tag, double xp) {
            return new ItemCraftReward(TagKey.create(Registries.ITEM, tag), xp);
        }
    }

    public enum BlockCondition {
        ANY,
        MATURE
    }
}
