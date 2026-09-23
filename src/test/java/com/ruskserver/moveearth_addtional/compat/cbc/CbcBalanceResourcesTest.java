package com.ruskserver.moveearth_addtional.compat.cbc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CbcBalanceResourcesTest {
    private static final String CASTING = "/data/createbigcannons/createbigcannons/block_recipes/";

    @Test
    void allSixCastIronPartsBypassBoringAndSlidingBreechFinishesInCast() {
        Map<String, String> outputs = Map.of(
                "unbored_cast_iron_cannon_barrel", "cast_iron_cannon_barrel",
                "unbored_cast_iron_cannon_chamber", "cast_iron_cannon_chamber",
                "unbored_cast_iron_sliding_breech", "cast_iron_sliding_breech",
                "unbored_cast_iron_autocannon_barrel", "cast_iron_autocannon_barrel",
                "unbored_cast_iron_autocannon_breech", "incomplete_cast_iron_autocannon_breech",
                "unbored_cast_iron_autocannon_recoil_spring", "incomplete_cast_iron_autocannon_recoil_spring");
        outputs.forEach((id, output) -> {
            JsonObject recipe = read(CASTING + id + ".json");
            assertEquals("createbigcannons:cannon_casting", recipe.get("type").getAsString(), id);
            assertEquals("c:molten_cast_iron", recipe.getAsJsonObject("fluid").get("tag").getAsString(), id);
            assertEquals("createbigcannons:" + output, recipe.get("result").getAsString(), id);
            if (id.equals("unbored_cast_iron_sliding_breech")) {
                assertEquals("createbigcannons:sliding_breech", recipe.get("cast_shape").getAsString(), id);
            }
        });
    }

    @Test
    void castIronAndSteelWeldsDoNotReduceSafePropellantStress() {
        for (String material : new String[]{"cast_iron", "steel"}) {
            JsonObject properties = read("/data/createbigcannons/big_cannon_materials/" + material + ".json");
            assertEquals(0, properties.get("weld_stress_penalty").getAsInt(), material);
            assertEquals(true, properties.get("is_weldable").getAsBoolean(), material);
        }
    }

    private static JsonObject read(String path) {
        InputStream stream = CbcBalanceResourcesTest.class.getResourceAsStream(path);
        assertNotNull(stream, path);
        try (stream; InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new AssertionError(path, exception);
        }
    }
}
