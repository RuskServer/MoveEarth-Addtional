package com.ruskserver.moveearth_addtional.compat.create;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Glass versions of every CEE recipe that needs terracotta. */
class CeeGlassRecipesTest {
    private static final Path DIR = Path.of("src/main/resources/data/moveearth_addtional/recipe/electroenergetics");

    @Test
    void everyGlassRecipeUsesGlassAndNoTerracotta() throws Exception {
        List<Path> recipes;
        try (Stream<Path> files = Files.list(DIR)) {
            recipes = files.filter(p -> p.toString().endsWith(".json")).toList();
        }
        assertEquals(6, recipes.size());
        for (Path path : recipes) {
            String json = Files.readString(path);
            assertTrue(json.contains("\"c:glass_blocks\""), path.toString());
            assertFalse(json.contains("terracotta"), path.toString());
            assertTrue(json.contains("\"modid\": \"electroenergetics\""), path.toString());
        }
    }
}
