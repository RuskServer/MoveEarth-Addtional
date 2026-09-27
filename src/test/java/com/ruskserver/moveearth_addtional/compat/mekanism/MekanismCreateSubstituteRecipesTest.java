package com.ruskserver.moveearth_addtional.compat.mekanism;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mekanism's crusher is blocked, but biofuel and fluorite dust are only made by
 * it; these Create recipes stand in for it. They must not share an input with
 * Create's own milling or crushing, or Create would pick either recipe at random.
 */
class MekanismCreateSubstituteRecipesTest {
    private static final Path ROOT = Path.of(
            "src/main/resources/data/moveearth_addtional/recipe/mekanism/create_substitute");

    private static JsonObject read(Path path) throws Exception {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static void collectInputs(JsonElement ingredient, Set<String> into) {
        if (ingredient.isJsonArray()) {
            ingredient.getAsJsonArray().forEach(child -> collectInputs(child, into));
            return;
        }
        JsonObject object = ingredient.getAsJsonObject();
        if (object.has("item")) into.add(object.get("item").getAsString());
        if (object.has("tag")) into.add("#" + object.get("tag").getAsString());
        if (object.has("children")) collectInputs(object.get("children"), into);
    }

    /** Inputs of Create's own milling and crushing recipes, read from the Create jar on the classpath. */
    private static Set<String> createInputs() throws Exception {
        URL marker = MekanismCreateSubstituteRecipesTest.class.getClassLoader()
                .getResource("data/create/recipe/milling/wheat.json");
        assertNotNull(marker, "Create's recipes must be on the test classpath");
        String jar = marker.toString().substring("jar:".length(), marker.toString().indexOf("!/"));
        Set<String> inputs = new HashSet<>();
        try (FileSystem zip = FileSystems.newFileSystem(URI.create("jar:" + jar), Map.of())) {
            for (String kind : List.of("milling", "crushing")) {
                try (Stream<Path> files = Files.walk(zip.getPath("data/create/recipe/" + kind))) {
                    for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                        try (Reader reader = Files.newBufferedReader(file)) {
                            JsonObject recipe = JsonParser.parseReader(reader).getAsJsonObject();
                            if (recipe.has("ingredients")) collectInputs(recipe.get("ingredients"), inputs);
                        }
                    }
                }
            }
        }
        return inputs;
    }

    @Test
    void biofuelSubstitutesAreSingleInputMillingThatCreateDoesNotAlreadyUse() throws Exception {
        Set<String> taken = createInputs();
        assertTrue(taken.contains("minecraft:wheat"), "sanity: Create mills wheat");
        List<Path> recipes;
        try (Stream<Path> files = Files.list(ROOT.resolve("biofuel"))) {
            recipes = files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        assertFalse(recipes.isEmpty());
        for (Path path : recipes) {
            JsonObject recipe = read(path);
            assertEquals("create:milling", recipe.get("type").getAsString(), path.toString());
            assertEquals(1, recipe.getAsJsonArray("ingredients").size(), path.toString());
            Set<String> inputs = new HashSet<>();
            collectInputs(recipe.get("ingredients"), inputs);
            inputs.retainAll(taken);
            assertTrue(inputs.isEmpty(), path + " clashes with Create on " + inputs);
            String output = recipe.getAsJsonArray("results").get(0).getAsJsonObject().get("id").getAsString();
            assertTrue(output.equals("mekanism:bio_fuel") || output.equals("mekanism:block_bio_fuel"), path.toString());
        }
    }

    @Test
    void fluoriteGemsCrushIntoTheDustPelletsNeed() throws Exception {
        JsonObject recipe = read(ROOT.resolve("fluorite_dust.json"));
        assertEquals("create:crushing", recipe.get("type").getAsString());
        assertEquals("c:gems/fluorite", recipe.getAsJsonArray("ingredients").get(0).getAsJsonObject()
                .get("tag").getAsString());
        assertEquals("mekanism:dust_fluorite", recipe.getAsJsonArray("results").get(0).getAsJsonObject()
                .get("id").getAsString());
        assertFalse(createInputs().contains("#c:gems/fluorite"));
    }
}
