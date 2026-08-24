package com.ruskserver.moveearth_addtional.jobs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.resources.ResourceLocation;
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

    public boolean rewardsBlock(BlockState state) {
        for (JobDefinition definition : definitions.values()) {
            if (definition.blockBreakXp(state) > 0) {
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
        String displayName = GsonHelper.getAsString(json, "display_name", id.toString());
        int maxLevel = positive(json, "max_level", 50);
        int pointsPerLevel = positive(json, "points_per_level", 1);

        JsonObject curve = GsonHelper.getAsJsonObject(json, "xp_curve");
        int baseXp = positive(curve, "base", 100);
        int linearXp = nonNegative(curve, "linear", 25);
        int quadraticXp = nonNegative(curve, "quadratic", 10);

        List<JobDefinition.BlockBreakReward> rewards = new ArrayList<>();
        for (JsonElement actionElement : GsonHelper.getAsJsonArray(json, "block_break")) {
            JsonObject action = actionElement.getAsJsonObject();
            ResourceLocation tag = ResourceLocation.tryParse(GsonHelper.getAsString(action, "tag"));
            if (tag == null) {
                throw new IllegalArgumentException("Invalid block tag");
            }
            rewards.add(JobDefinition.BlockBreakReward.of(tag, positive(action, "xp", 1)));
        }
        return new JobDefinition(id, displayName, maxLevel, pointsPerLevel,
                baseXp, linearXp, quadraticXp, rewards);
    }

    private static int positive(JsonObject json, String key, int fallback) {
        int value = GsonHelper.getAsInt(json, key, fallback);
        if (value <= 0) {
            throw new IllegalArgumentException(key + " must be positive");
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
