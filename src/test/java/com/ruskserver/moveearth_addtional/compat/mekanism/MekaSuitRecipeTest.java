package com.ruskserver.moveearth_addtional.compat.mekanism;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MekaSuitRecipeTest {
    private JsonObject recipe(String name) throws Exception {
        try (var reader = Files.newBufferedReader(Path.of(
                "src/main/resources/data/moveearth_addtional/recipe/mekanism/gear/" + name + ".json"))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private long count(JsonObject recipe, char symbol) {
        return recipe.getAsJsonArray("pattern").asList().stream()
                .flatMapToInt(row -> row.getAsString().chars()).filter(c -> c == symbol).count();
    }

    @Test
    void piecesUseArmorMaterialNotNetheriteArmor() throws Exception {
        Map<String, Integer> material = Map.of(
                "mekasuit_helmet", 1, "mekasuit_bodyarmor", 3, "mekasuit_pants", 2, "mekasuit_boots", 1);
        for (var piece : material.entrySet()) {
            JsonObject json = recipe(piece.getKey());
            assertEquals("create:mechanical_crafting", json.get("type").getAsString());
            assertEquals("mekanism:" + piece.getKey(), json.getAsJsonObject("result").get("id").getAsString());
            Set<String> used = new HashSet<>();
            json.getAsJsonArray("pattern").forEach(row -> row.getAsString().chars()
                    .filter(c -> c != ' ').forEach(c -> used.add(String.valueOf((char) c))));
            assertEquals(json.getAsJsonObject("key").keySet(), used);
            assertEquals(piece.getValue().longValue(), count(json, 'M'));
            assertEquals(4, count(json, 'H'));
            assertEquals(2, count(json, 'A'));
            assertEquals(piece.getKey().equals("mekasuit_bodyarmor") ? 1 : 0, count(json, 'N'));
            assertEquals(false, json.toString().contains("netherite_helmet")
                    || json.toString().contains("netherite_chestplate")
                    || json.toString().contains("netherite_leggings")
                    || json.toString().contains("netherite_boots"));
        }
    }

    @Test
    void armorMaterialIsAnEightHourReaction() throws Exception {
        JsonObject json = recipe("meka_armor_material");
        assertEquals("mekanism:reaction", json.get("type").getAsString());
        assertEquals(8 * 60 * 60 * 20, json.get("duration").getAsInt());
        assertEquals("moveearth_addtional:meka_armor_material",
                json.getAsJsonObject("item_output").get("id").getAsString());
    }
}
