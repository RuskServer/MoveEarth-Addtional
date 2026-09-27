package com.ruskserver.moveearth_addtional.compat.cbc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cast iron, bronze and steel cannon parts must cast already bored; no override may leave an unbored part. */
class CbcCastingOverridesTest {
    private static final Path CASTS = Path.of("src/main/resources/data/createbigcannons/createbigcannons/block_recipes");

    private static JsonObject read(Path path) throws Exception {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    void everyCastingOverrideSkipsTheDrill() throws Exception {
        List<Path> casts;
        try (Stream<Path> files = Files.list(CASTS)) {
            casts = files.filter(p -> p.toString().endsWith(".json")).toList();
        }
        assertTrue(casts.size() >= 22, "cast iron, bronze and steel overrides");
        for (Path path : casts) {
            JsonObject recipe = read(path);
            assertEquals("createbigcannons:cannon_casting", recipe.get("type").getAsString(), path.toString());
            String result = recipe.get("result").getAsString();
            assertFalse(result.contains("unbored"), path + " still casts " + result);
        }
    }
}
