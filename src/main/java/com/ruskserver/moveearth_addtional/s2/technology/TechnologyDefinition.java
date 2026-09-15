package com.ruskserver.moveearth_addtional.s2.technology;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Immutable, server-authoritative datapack definition for a technology node. */
public record TechnologyDefinition(int schema, ResourceLocation id, Scope scope, String category, String chapter,
                                   String titleKey, String descriptionKey, ResourceLocation icon, int x, int y,
                                   PrerequisiteMode prerequisiteMode, List<ResourceLocation> prerequisites,
                                   List<Objective> objectives, List<String> unlocks,
                                   List<ResourceLocation> gatedItems, List<ResourceLocation> gatedBlocks,
                                   List<ResourceLocation> gatedRecipes, List<ResourceLocation> gatedEntities,
                                   List<String> gatedActions, List<ResourceLocation> jeiItems,
                                   List<ResourceLocation> aliases) {
    public static final int CURRENT_SCHEMA = 2;

    public TechnologyDefinition {
        if (schema < 1 || schema > CURRENT_SCHEMA) throw new IllegalArgumentException("unsupported schema: " + schema);
        if (id == null) throw new IllegalArgumentException("technology id is required");
        scope = scope == null ? Scope.NATION : scope;
        category = blankTo(category, "foundation");
        chapter = blankTo(chapter, category);
        titleKey = blankTo(titleKey, "technology." + id.getNamespace() + "." + id.getPath().replace('/', '.') + ".title");
        descriptionKey = blankTo(descriptionKey, "technology." + id.getNamespace() + "." + id.getPath().replace('/', '.') + ".description");
        icon = icon == null ? ResourceLocation.withDefaultNamespace("knowledge_book") : icon;
        prerequisiteMode = prerequisiteMode == null ? PrerequisiteMode.ALL : prerequisiteMode;
        prerequisites = copy(prerequisites);
        objectives = objectives == null ? List.of() : List.copyOf(objectives);
        if (objectives.isEmpty()) throw new IllegalArgumentException("at least one objective is required");
        if (objectives.size() > 32 || prerequisites.size() > 32) throw new IllegalArgumentException("too many technology entries");
        if (objectives.stream().map(Objective::id).distinct().count() != objectives.size()) {
            throw new IllegalArgumentException("duplicate objective id");
        }
        if (Math.abs(x) > 256 || Math.abs(y) > 256) throw new IllegalArgumentException("technology coordinates out of range");
        unlocks = unlocks == null ? List.of() : List.copyOf(unlocks);
        gatedItems = copy(gatedItems);
        gatedBlocks = copy(gatedBlocks);
        gatedRecipes = copy(gatedRecipes);
        gatedEntities = copy(gatedEntities);
        gatedActions = gatedActions == null ? List.of() : List.copyOf(gatedActions);
        jeiItems = copy(jeiItems);
        aliases = copy(aliases);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static List<ResourceLocation> copy(List<ResourceLocation> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public enum Scope {
        PERSONAL, NATION;

        static Scope parse(String value) {
            return switch (value == null ? "" : value.toLowerCase(Locale.ROOT)) {
                case "personal" -> PERSONAL;
                case "nation", "" -> NATION;
                default -> throw new IllegalArgumentException("unknown technology scope: " + value);
            };
        }
    }

    public enum PrerequisiteMode {
        ALL, ANY;

        static PrerequisiteMode parse(String value) {
            return switch (value == null ? "" : value.toLowerCase(Locale.ROOT)) {
                case "all", "" -> ALL;
                case "any" -> ANY;
                default -> throw new IllegalArgumentException("unknown prerequisite mode: " + value);
            };
        }
    }

    public record Objective(String id, ObjectiveType type, ResourceLocation target,
                            boolean tag, long required, String descriptionKey,
                            Map<String, String> constraints) {
        public Objective {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("objective id is required");
            if (type == null) throw new IllegalArgumentException("objective type is required");
            required = Math.max(1L, required);
            descriptionKey = descriptionKey == null ? "" : descriptionKey;
            constraints = constraints == null ? Map.of() : Map.copyOf(constraints);
            if (constraints.size() > 16 || id.length() > 64 || descriptionKey.length() > 160) {
                throw new IllegalArgumentException("objective definition exceeds limits");
            }
            if (type.requiresTarget() && target == null) throw new IllegalArgumentException("objective target is required");
        }

        public boolean matches(BlockState state) {
            if (!type.blockTarget() || target == null) return false;
            return tag ? state.is(TagKey.create(net.minecraft.core.registries.Registries.BLOCK, target))
                    : matchesTarget(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        }

        public boolean matches(ItemStack stack) {
            if (!type.itemTarget() || target == null) return false;
            return tag ? stack.is(TagKey.create(net.minecraft.core.registries.Registries.ITEM, target))
                    : matchesTarget(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }

        public boolean matchesTarget(ResourceLocation actual) {
            if (actual == null) return target == null;
            String namespace = constraints.get("target_namespace");
            if (namespace != null && !namespace.equals(actual.getNamespace())) return false;
            String contains = constraints.get("target_path_contains");
            if (contains != null && !actual.getPath().contains(contains)) return false;
            String prefix = constraints.get("target_path_prefix");
            if (prefix != null && !actual.getPath().startsWith(prefix)) return false;
            return namespace != null || contains != null || prefix != null || actual.equals(target);
        }

        public boolean ownTerritoryRequired() {
            return Boolean.parseBoolean(constraints.getOrDefault("own_territory", "false"));
        }
    }

    public enum ObjectiveType {
        ACTIVE_CAPITAL(false, false, false),
        OBTAIN_ITEM(true, false, true),
        CRAFT_ITEM(true, false, true),
        CRAFT_RECIPE(true, false, false),
        PLACE_BLOCK(true, true, false),
        PLACE_STRUCTURE(true, true, false),
        OPERATE_BLOCK(true, true, false),
        GENERATE_ENERGY(true, false, false),
        TRANSMIT_ENERGY(true, false, false),
        TRANSPORT_ITEMS(true, false, false),
        REINFORCE_BLOCKS(true, false, false),
        SURVIVE_TEMPERATURE(true, false, false),
        USE_EQUIPMENT(true, false, true),
        ASSEMBLE_VEHICLE(true, false, false),
        TRAVEL_DISTANCE(true, false, false),
        DAMAGE_TRAINING_TARGET(true, false, false),
        TERRITORY_ACTION(true, false, false),
        JOIN_OR_FOUND_NATION(false, false, false),
        MULTI_CONDITION(true, false, false),
        ACTION(true, false, false);

        private final boolean requiresTarget;
        private final boolean blockTarget;
        private final boolean itemTarget;

        ObjectiveType(boolean requiresTarget, boolean blockTarget, boolean itemTarget) {
            this.requiresTarget = requiresTarget;
            this.blockTarget = blockTarget;
            this.itemTarget = itemTarget;
        }

        boolean requiresTarget() { return requiresTarget; }
        boolean blockTarget() { return blockTarget; }
        boolean itemTarget() { return itemTarget; }

        static ObjectiveType parse(String value) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("objective type is required");
            try { return valueOf(value.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("unknown objective type: " + value, exception);
            }
        }
    }
}
