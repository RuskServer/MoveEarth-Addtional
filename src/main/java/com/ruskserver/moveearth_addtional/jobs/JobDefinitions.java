package com.ruskserver.moveearth_addtional.jobs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Loads {@code data/<namespace>/jobs/*.json} on server start and /reload. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class JobDefinitions extends SimplePreparableReloadListener<Map<ResourceLocation, JsonElement>> {
    public static final JobDefinitions INSTANCE = new JobDefinitions();
    private volatile Map<ResourceLocation, JobDefinition> definitions = Map.of();

    private JobDefinitions() {
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    public Optional<JobDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public List<JobDefinition> all() {
        return definitions.values().stream()
                .sorted(Comparator.comparing(definition -> definition.id().toString()))
                .toList();
    }

    public Set<ResourceLocation> ids() {
        return definitions.keySet();
    }

    public boolean rewardsBlock(BlockState state) {
        for (JobDefinition definition : definitions.values()) {
            if (definition.blockBreakXp(state) > 0) {
                return true;
            }
        }
        return false;
    }

    public boolean tracksPlacement(BlockState state) {
        for (JobDefinition definition : definitions.values()) {
            if (definition.tracksPlacement(state)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected Map<ResourceLocation, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonElement> json = new LinkedHashMap<>();
        resourceManager.listResources("jobs", id -> id.getPath().endsWith(".json"))
                .forEach((resourceId, resource) -> read(resourceId, resource).ifPresent(element -> {
                    String path = resourceId.getPath();
                    String jobPath = path.substring("jobs/".length(), path.length() - ".json".length());
                    json.put(ResourceLocation.fromNamespaceAndPath(resourceId.getNamespace(), jobPath), element);
                }));
        return json;
    }

    private Optional<JsonElement> read(ResourceLocation resourceId, Resource resource) {
        try (BufferedReader reader = resource.openAsReader()) {
            return Optional.of(JsonParser.parseReader(reader));
        } catch (Exception exception) {
            Moveearth_addtional.LOGGER.error("Failed to read job definition {}", resourceId, exception);
            return Optional.empty();
        }
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> prepared, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, JobDefinition> loaded = new LinkedHashMap<>();
        prepared.forEach((id, element) -> {
            try {
                loaded.put(id, parse(id, element.getAsJsonObject()));
            } catch (Exception exception) {
                Moveearth_addtional.LOGGER.error("Invalid job definition {}", id, exception);
            }
        });
        definitions = Map.copyOf(loaded);
        Moveearth_addtional.LOGGER.info("Loaded {} job definitions", definitions.size());
    }

    private static JobDefinition parse(ResourceLocation id, JsonObject json) {
        String displayName = boundedString(json, "display_name", id.toString(), 64);
        String description = boundedString(json, "description", "", 160);
        int maxLevel = positive(json, "max_level", 50);
        int pointsPerLevel = positive(json, "points_per_level", 1);

        JsonObject curve = GsonHelper.getAsJsonObject(json, "xp_curve");
        int baseXp = positive(curve, "base", 100);
        int linearXp = nonNegative(curve, "linear", 25);
        int quadraticXp = nonNegative(curve, "quadratic", 10);

        List<JobDefinition.BlockBreakReward> blockRewards = new ArrayList<>();
        if (json.has("block_break")) {
            for (JsonElement actionElement : GsonHelper.getAsJsonArray(json, "block_break")) {
                JsonObject action = actionElement.getAsJsonObject();
                JobDefinition.BlockCondition condition = parseCondition(
                        GsonHelper.getAsString(action, "condition", "any"));
                boolean excludePlayerPlaced = GsonHelper.getAsBoolean(action, "exclude_player_placed", true);
                double xp = positiveDouble(action, "xp", 1.0D);
                boolean hasTag = action.has("tag");
                boolean hasBlock = action.has("block");
                if (hasTag == hasBlock) {
                    throw new IllegalArgumentException("block_break requires exactly one of tag or block");
                }
                blockRewards.add(hasTag
                        ? JobDefinition.BlockBreakReward.ofTag(requiredLocation(action, "tag"), xp,
                        condition, excludePlayerPlaced)
                        : JobDefinition.BlockBreakReward.ofBlock(requiredBlock(action, "block"), xp,
                        condition, excludePlayerPlaced));
            }
        }

        List<JobDefinition.EntityReward> killRewards = parseEntityRewards(json, "entity_kill");
        List<JobDefinition.EntityReward> breedRewards = parseEntityRewards(json, "entity_breed");
        List<JobDefinition.ItemCraftReward> craftRewards = parseItemCraftRewards(json);
        return new JobDefinition(id, displayName, description, maxLevel, pointsPerLevel,
                baseXp, linearXp, quadraticXp, blockRewards, killRewards, breedRewards, craftRewards);
    }

    private static List<JobDefinition.ItemCraftReward> parseItemCraftRewards(JsonObject json) {
        if (!json.has("item_craft")) {
            return List.of();
        }
        List<JobDefinition.ItemCraftReward> rewards = new ArrayList<>();
        for (JsonElement actionElement : GsonHelper.getAsJsonArray(json, "item_craft")) {
            JsonObject action = actionElement.getAsJsonObject();
            rewards.add(JobDefinition.ItemCraftReward.of(
                    requiredLocation(action, "tag"), positiveDouble(action, "xp", 1.0D)));
        }
        return rewards;
    }

    private static List<JobDefinition.EntityReward> parseEntityRewards(JsonObject json, String key) {
        if (!json.has(key)) {
            return List.of();
        }
        List<JobDefinition.EntityReward> rewards = new ArrayList<>();
        for (JsonElement actionElement : GsonHelper.getAsJsonArray(json, key)) {
            JsonObject action = actionElement.getAsJsonObject();
            rewards.add(JobDefinition.EntityReward.of(
                    requiredLocation(action, "tag"), positiveDouble(action, "xp", 1.0D)));
        }
        return rewards;
    }

    private static ResourceLocation requiredLocation(JsonObject json, String key) {
        ResourceLocation location = ResourceLocation.tryParse(GsonHelper.getAsString(json, key));
        if (location == null) {
            throw new IllegalArgumentException("Invalid resource location in " + key);
        }
        return location;
    }

    private static ResourceLocation requiredBlock(JsonObject json, String key) {
        ResourceLocation location = requiredLocation(json, key);
        if (BuiltInRegistries.BLOCK.getOptional(location).isEmpty()) {
            throw new IllegalArgumentException("Unknown block: " + location);
        }
        return location;
    }

    private static JobDefinition.BlockCondition parseCondition(String value) {
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "any" -> JobDefinition.BlockCondition.ANY;
            case "mature" -> JobDefinition.BlockCondition.MATURE;
            default -> throw new IllegalArgumentException("Unknown block condition: " + value);
        };
    }

    private static int positive(JsonObject json, String key, int fallback) {
        int value = GsonHelper.getAsInt(json, key, fallback);
        if (value <= 0) {
            throw new IllegalArgumentException(key + " must be positive");
        }
        return value;
    }

    private static double positiveDouble(JsonObject json, String key, double fallback) {
        double value = GsonHelper.getAsDouble(json, key, fallback);
        if (!Double.isFinite(value) || value <= 0.0D || value > 1_000_000.0D) {
            throw new IllegalArgumentException(key + " must be a finite positive number up to 1000000");
        }
        return value;
    }

    private static String boundedString(JsonObject json, String key, String fallback, int maxLength) {
        String value = GsonHelper.getAsString(json, key, fallback);
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(key + " exceeds " + maxLength + " characters");
        }
        return value;
    }

    private static int nonNegative(JsonObject json, String key, int fallback) {
        int value = GsonHelper.getAsInt(json, key, fallback);
        if (value < 0) {
            throw new IllegalArgumentException(key + " must not be negative");
        }
        return value;
    }
}
