package com.ruskserver.moveearth_addtional.s2.technology;

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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Collections;

/** Loads {@code data/<namespace>/technology/*.json} and rejects malformed dependency graphs. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class TechnologyDefinitions extends SimplePreparableReloadListener<Map<ResourceLocation, JsonElement>> {
    public static final TechnologyDefinitions INSTANCE = new TechnologyDefinitions();
    private volatile Map<ResourceLocation, TechnologyDefinition> definitions = Map.of();
    private volatile Map<ResourceLocation, ResourceLocation> aliases = Map.of();
    private volatile Map<ObjectiveKey, List<TechnologyDefinition>> objectiveIndex = Map.of();
    private volatile long revision;

    private TechnologyDefinitions() { }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    public List<TechnologyDefinition> all() {
        return definitions.values().stream().sorted(Comparator
                .comparing(TechnologyDefinition::scope)
                .thenComparing(TechnologyDefinition::category)
                .thenComparingInt(TechnologyDefinition::x)
                .thenComparingInt(TechnologyDefinition::y)).toList();
    }

    public Optional<TechnologyDefinition> get(ResourceLocation id) {
        TechnologyDefinition direct = definitions.get(id);
        if (direct != null) return Optional.of(direct);
        ResourceLocation canonical = aliases.get(id);
        return canonical == null ? Optional.empty() : Optional.ofNullable(definitions.get(canonical));
    }

    public long revision() { return revision; }

    public ResourceLocation canonicalId(ResourceLocation id) {
        TechnologyDefinition definition = get(id).orElse(null);
        return definition == null ? id : definition.id();
    }

    public List<TechnologyDefinition> candidates(TechnologyDefinition.ObjectiveType type, ResourceLocation target) {
        return objectiveIndex.getOrDefault(new ObjectiveKey(type, null), List.of());
    }

    @Override
    protected Map<ResourceLocation, JsonElement> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonElement> json = new LinkedHashMap<>();
        manager.listResources("technology", id -> id.getPath().endsWith(".json"))
                .forEach((resourceId, resource) -> read(resourceId, resource).ifPresent(element -> {
                    String path = resourceId.getPath();
                    json.put(ResourceLocation.fromNamespaceAndPath(resourceId.getNamespace(),
                            path.substring("technology/".length(), path.length() - 5)), element);
                }));
        return json;
    }

    private Optional<JsonElement> read(ResourceLocation id, Resource resource) {
        try (BufferedReader reader = resource.openAsReader()) {
            return Optional.of(JsonParser.parseReader(reader));
        } catch (Exception exception) {
            Moveearth_addtional.LOGGER.error("Failed to read technology definition {}", id, exception);
            return Optional.empty();
        }
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> prepared, ResourceManager manager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, TechnologyDefinition> loaded = new LinkedHashMap<>();
        prepared.forEach((id, element) -> {
            try {
                JsonObject json = element.getAsJsonObject();
                if (json.has("nodes")) {
                    for (JsonElement child : GsonHelper.getAsJsonArray(json, "nodes")) {
                        JsonObject node = child.getAsJsonObject();
                        ResourceLocation nodeId = requiredLocation(GsonHelper.getAsString(node, "id"));
                        if (!requiredModsLoaded(node)) continue;
                        TechnologyDefinition previous = loaded.put(nodeId, parse(nodeId, node));
                        if (previous != null) throw new IllegalArgumentException("duplicate technology id " + nodeId);
                    }
                    return;
                }
                if (!requiredModsLoaded(json)) return;
                TechnologyDefinition previous = loaded.put(id, parse(id, json));
                if (previous != null) throw new IllegalArgumentException("duplicate technology id " + id);
            } catch (Exception exception) {
                Moveearth_addtional.LOGGER.error("Invalid technology definition {}", id, exception);
            }
        });
        pruneInvalidGraph(loaded);
        definitions = Map.copyOf(loaded);
        aliases = buildAliases(loaded);
        objectiveIndex = buildObjectiveIndex(loaded);
        revision++;
        Moveearth_addtional.LOGGER.info("Loaded {} technology nodes (definition revision {})", definitions.size(), revision);
    }

    static TechnologyDefinition parse(ResourceLocation id, JsonObject json) {
        int schema = GsonHelper.getAsInt(json, "schema", 1);
        String category = GsonHelper.getAsString(json, "category", "foundation");
        String chapter = GsonHelper.getAsString(json, "chapter", category);
        TechnologyDefinition.Scope scope = TechnologyDefinition.Scope.parse(
                GsonHelper.getAsString(json, "scope", "nation"));
        String translationPath = id.getPath().replace('/', '.');
        String titleKey = GsonHelper.getAsString(json, "title_key",
                "technology." + id.getNamespace() + "." + translationPath + ".title");
        String descriptionKey = GsonHelper.getAsString(json, "description_key",
                "technology." + id.getNamespace() + "." + translationPath + ".description");
        ResourceLocation icon = requiredLocation(GsonHelper.getAsString(json, "icon", "minecraft:knowledge_book"));
        int x = GsonHelper.getAsInt(json, "x", 0);
        int y = GsonHelper.getAsInt(json, "y", 0);
        TechnologyDefinition.PrerequisiteMode prerequisiteMode = TechnologyDefinition.PrerequisiteMode.ALL;
        List<ResourceLocation> prerequisites;
        if (json.has("prerequisites") && json.get("prerequisites").isJsonObject()) {
            JsonObject prerequisiteObject = json.getAsJsonObject("prerequisites");
            prerequisiteMode = TechnologyDefinition.PrerequisiteMode.parse(
                    GsonHelper.getAsString(prerequisiteObject, "mode", "all"));
            prerequisites = locations(prerequisiteObject, "nodes");
        } else {
            prerequisites = locations(json, "prerequisites");
        }
        List<String> unlocks = strings(json, "unlocks");
        List<ResourceLocation> gatedItems = locations(json, "gated_items");
        List<ResourceLocation> gatedBlocks = locations(json, "gated_blocks");
        List<ResourceLocation> gatedRecipes = locations(json, "gated_recipes");
        List<ResourceLocation> gatedEntities = locations(json, "gated_entities");
        List<String> gatedActions = strings(json, "gated_actions");
        List<ResourceLocation> jeiItems = locations(json, "jei_items");
        List<ResourceLocation> aliases = locations(json, "aliases");
        List<TechnologyDefinition.Objective> objectives = new ArrayList<>();
        for (JsonElement value : GsonHelper.getAsJsonArray(json, "objectives")) {
            JsonObject objective = value.getAsJsonObject();
            String targetValue = GsonHelper.getAsString(objective, "target", "");
            boolean tag = targetValue.startsWith("#");
            ResourceLocation target = targetValue.isBlank() ? null
                    : requiredLocation(tag ? targetValue.substring(1) : targetValue);
            objectives.add(new TechnologyDefinition.Objective(
                    GsonHelper.getAsString(objective, "id"),
                    TechnologyDefinition.ObjectiveType.parse(GsonHelper.getAsString(objective, "type")),
                    target, tag, GsonHelper.getAsLong(objective, "count", 1L),
                    GsonHelper.getAsString(objective, "description_key"), constraints(objective)));
        }
        return new TechnologyDefinition(schema, id, scope, category, chapter, titleKey, descriptionKey, icon, x, y,
                prerequisiteMode, prerequisites, objectives, unlocks, gatedItems, gatedBlocks, gatedRecipes,
                gatedEntities, gatedActions, jeiItems, aliases);
    }

    private static Map<String, String> constraints(JsonObject objective) {
        if (!objective.has("constraints")) return Map.of();
        JsonObject values = objective.getAsJsonObject("constraints");
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) {
                throw new IllegalArgumentException("constraint must be a scalar: " + entry.getKey());
            }
            result.put(entry.getKey(), entry.getValue().getAsString());
        }
        return Map.copyOf(result);
    }

    private static boolean requiredModsLoaded(JsonObject json) {
        if (!json.has("required_mods")) return true;
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "required_mods")) {
            if (!ModList.get().isLoaded(element.getAsString())) return false;
        }
        return true;
    }

    private static List<ResourceLocation> locations(JsonObject json, String key) {
        return strings(json, key).stream().map(TechnologyDefinitions::requiredLocation).toList();
    }

    private static List<String> strings(JsonObject json, String key) {
        if (!json.has(key)) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, key)) result.add(element.getAsString());
        return List.copyOf(result);
    }

    private static ResourceLocation requiredLocation(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) throw new IllegalArgumentException("invalid resource location: " + value);
        return id;
    }

    private static void pruneInvalidGraph(Map<ResourceLocation, TechnologyDefinition> loaded) {
        boolean removed;
        do {
            removed = loaded.entrySet().removeIf(entry -> {
                List<ResourceLocation> missing = entry.getValue().prerequisites().stream()
                        .filter(id -> !loaded.containsKey(id)).toList();
                boolean invalid = entry.getValue().prerequisiteMode() == TechnologyDefinition.PrerequisiteMode.ALL
                        ? !missing.isEmpty()
                        : !entry.getValue().prerequisites().isEmpty()
                        && missing.size() == entry.getValue().prerequisites().size();
                if (!invalid) return false;
                Moveearth_addtional.LOGGER.warn("Skipping technology {} because prerequisites {} are unavailable",
                        entry.getKey(), missing);
                return true;
            });
        } while (removed);
        Set<ResourceLocation> cyclic = new HashSet<>();
        for (ResourceLocation id : List.copyOf(loaded.keySet())) {
            try {
                visit(id, loaded, new HashSet<>(), new HashSet<>());
            } catch (IllegalArgumentException exception) {
                cyclic.add(id);
                Moveearth_addtional.LOGGER.error("Skipping cyclic technology branch rooted at {}", id);
            }
        }
        loaded.keySet().removeAll(cyclic);
    }

    private static Map<ResourceLocation, ResourceLocation> buildAliases(
            Map<ResourceLocation, TechnologyDefinition> loaded) {
        Map<ResourceLocation, ResourceLocation> result = new LinkedHashMap<>();
        loaded.values().forEach(definition -> definition.aliases().forEach(alias -> {
            ResourceLocation previous = result.putIfAbsent(alias, definition.id());
            if (previous != null && !previous.equals(definition.id())) {
                Moveearth_addtional.LOGGER.error("Technology alias {} is claimed by both {} and {}",
                        alias, previous, definition.id());
            }
        }));
        return Map.copyOf(result);
    }

    private static Map<ObjectiveKey, List<TechnologyDefinition>> buildObjectiveIndex(
            Map<ResourceLocation, TechnologyDefinition> loaded) {
        Map<ObjectiveKey, List<TechnologyDefinition>> mutable = new LinkedHashMap<>();
        loaded.values().forEach(definition -> definition.objectives().forEach(objective ->
                mutable.computeIfAbsent(new ObjectiveKey(objective.type(), null), ignored -> new ArrayList<>())
                        .add(definition)));
        Map<ObjectiveKey, List<TechnologyDefinition>> result = new LinkedHashMap<>();
        mutable.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(result);
    }

    private static void visit(ResourceLocation id, Map<ResourceLocation, TechnologyDefinition> definitions,
                              Set<ResourceLocation> visiting, Set<ResourceLocation> visited) {
        if (visited.contains(id)) return;
        if (!visiting.add(id)) throw new IllegalArgumentException("technology dependency cycle at " + id);
        for (ResourceLocation dependency : definitions.get(id).prerequisites()) {
            visit(dependency, definitions, visiting, visited);
        }
        visiting.remove(id);
        visited.add(id);
    }

    private record ObjectiveKey(TechnologyDefinition.ObjectiveType type, ResourceLocation target) { }
}
