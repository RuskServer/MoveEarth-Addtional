package com.ruskserver.moveearth_addtional.compat.mekanism;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class MekanismNuclearRecipeCostTest {
    private JsonObject recipe(String path) throws Exception {
        try (var reader = Files.newBufferedReader(Path.of(
                "src/main/resources/data/moveearth_addtional/recipe/mekanism/" + path + ".json"))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private long count(JsonObject recipe, char symbol) {
        return recipe.getAsJsonArray("pattern").asList().stream()
                .flatMapToInt(row -> row.getAsString().chars()).filter(c -> c == symbol).count();
    }

    @Test void nuclearChemistryRequiresFiveByFiveAssemblyAndValidKeys() throws Exception {
        for (String machine : new String[]{"chemical_infuser", "chemical_dissolution_chamber", "isotopic_centrifuge"}) {
            var json = recipe("machine/" + machine);
            assertEquals("create:mechanical_crafting", json.get("type").getAsString());
            assertEquals(5, json.getAsJsonArray("pattern").size());
            var used = new HashSet<String>();
            for (var row : json.getAsJsonArray("pattern")) {
                assertEquals(5, row.getAsString().length());
                row.getAsString().chars().forEach(c -> used.add(String.valueOf((char) c)));
            }
            assertEquals(json.getAsJsonObject("key").keySet(), used);
            assertEquals(4, count(json, 'H'));
            assertEquals(4, count(json, 'P'));
            assertEquals(1, json.getAsJsonObject("result").get("count").getAsInt());
        }
    }

    @Test void repeatedReactorComponentsConsumeSteelBlocks() throws Exception {
        var fuel = recipe("nuclear/fission/fuel_assembly");
        var control = recipe("nuclear/fission/control_rod_assembly");
        for (var json : new JsonObject[]{fuel, control}) {
            assertEquals("mekanism:block_steel", json.getAsJsonObject("key")
                    .getAsJsonObject("S").get("item").getAsString());
            assertEquals(2, count(json, 'S'));
        }
        assertEquals(3, count(control, 'P'));
    }

    @Test void entryAndBasicMachinesStayAccessible() throws Exception {
        assertEquals(3, recipe("metallurgic_infuser").getAsJsonArray("pattern").size());
        assertEquals("minecraft:crafting_shaped", recipe("machine/enrichment_chamber").get("type").getAsString());
        assertEquals("minecraft:crafting_shaped", recipe("machine/electrolytic_separator").get("type").getAsString());
    }
}
