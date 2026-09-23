package com.ruskserver.moveearth_addtional.compat.create;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CreateIronSupplyRecipesTest {
    @Test
    void gravelWashingKeepsFlintButNoLongerProducesIron() {
        String path = "/data/create/recipe/splashing/gravel.json";
        InputStream stream = CreateIronSupplyRecipesTest.class.getResourceAsStream(path);
        assertNotNull(stream, path);
        try (stream; InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject recipe = JsonParser.parseReader(reader).getAsJsonObject();
            assertEquals("create:splashing", recipe.get("type").getAsString());
            JsonArray ingredients = recipe.getAsJsonArray("ingredients");
            assertEquals(1, ingredients.size());
            assertEquals("minecraft:gravel", ingredients.get(0).getAsJsonObject().get("item").getAsString());
            JsonArray results = recipe.getAsJsonArray("results");
            assertEquals(1, results.size());
            JsonObject flint = results.get(0).getAsJsonObject();
            assertEquals("minecraft:flint", flint.get("id").getAsString());
            assertEquals(0.25D, flint.get("chance").getAsDouble());
        } catch (java.io.IOException exception) {
            throw new AssertionError(path, exception);
        }
    }
}
