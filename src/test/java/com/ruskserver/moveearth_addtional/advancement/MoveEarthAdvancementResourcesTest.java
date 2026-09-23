package com.ruskserver.moveearth_addtional.advancement;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveEarthAdvancementResourcesTest {
    private static final List<String> ADVANCEMENTS = List.of(
            "root", "getting_started/open_nation_hub", "getting_started/check_recipe",
            "getting_started/cold_protection", "getting_started/rest", "nation/view_territory",
            "nation/apply", "nation/citizen", "nation/found", "nation/treasury", "nation/upkeep",
            "industry/andesite_alloy", "industry/rotation", "industry/iron_sheet", "industry/steam",
            "industry/electricity", "industry/mekanism", "industry/freight", "engineering/welding_tool",
            "engineering/reinforce", "engineering/seal", "engineering/vehicle_core",
            "engineering/vehicle_repair", "engineering/siege_repair", "warfare/combat",
            "warfare/siege_participant", "warfare/artillery", "warfare/mobile_force", "warfare/revive",
            "warfare/imprison", "warfare/free_prisoner", "warfare/core_sabotage", "warfare/defend",
            "exploration/warehouse", "exploration/warehouse_raid", "exploration/warehouse_boss",
            "exploration/warehouse_loot");

    @Test
    void rootUsesVanillaJoinCriterion() {
        JsonObject root = read("/data/moveearth_addtional/advancement/root.json");
        assertEquals("minecraft:tick",
                root.getAsJsonObject("criteria").getAsJsonObject("joined").get("trigger").getAsString());
        assertEquals("moveearth_addtional:territory_core",
                root.getAsJsonObject("display").getAsJsonObject("icon").get("id").getAsString());
    }

    @Test
    void nationHubIsTheFirstChildAndDisplaysTheConfiguredKey() {
        JsonObject hub = read(
                "/data/moveearth_addtional/advancement/getting_started/open_nation_hub.json");
        assertEquals("moveearth_addtional:root", hub.get("parent").getAsString());

        JsonObject opened = hub.getAsJsonObject("criteria").getAsJsonObject("opened");
        assertEquals("moveearth_addtional:event", opened.get("trigger").getAsString());
        assertEquals(ModCriteria.NATION_HUB_OPENED,
                opened.getAsJsonObject("conditions").get("event").getAsString());

        JsonObject description = hub.getAsJsonObject("display").getAsJsonObject("description");
        assertEquals("key.moveearth_addtional.s2_hub",
                description.getAsJsonArray("with").get(0).getAsJsonObject().get("keybind").getAsString());
    }

    @Test
    void allThirtySevenAdvancementsHaveResolvableParentsTranslationsAndCriteria() {
        assertEquals(37, ADVANCEMENTS.size());
        Set<String> ids = Set.copyOf(ADVANCEMENTS);
        JsonObject ja = read("/assets/moveearth_addtional/lang/ja_jp.json");
        JsonObject en = read("/assets/moveearth_addtional/lang/en_us.json");
        int rootChildren = 0;
        for (String id : ADVANCEMENTS) {
            JsonObject advancement = advancement(id);
            JsonObject criteria = advancement.getAsJsonObject("criteria");
            assertNotNull(criteria, id + " criteria");
            assertFalse(criteria.entrySet().isEmpty(), id + " criteria");

            if (!id.equals("root")) {
                String parent = advancement.get("parent").getAsString();
                assertTrue(parent.startsWith("moveearth_addtional:"), id + " parent namespace");
                assertTrue(ids.contains(parent.substring("moveearth_addtional:".length())), id + " parent");
                if (parent.equals("moveearth_addtional:root")) rootChildren++;
            }

            JsonObject display = advancement.getAsJsonObject("display");
            assertNotNull(display, id + " display");
            assertTranslation(display.getAsJsonObject("title"), ja, en, id + " title");
            assertTranslation(display.getAsJsonObject("description"), ja, en, id + " description");
        }
        assertEquals(1, rootChildren, "the nation hub must be the root's only direct child");
    }

    @Test
    void everyCustomCriterionUsesARegisteredEventName() {
        Set<String> registered = Set.of(
                ModCriteria.NATION_HUB_OPENED, ModCriteria.TERRITORY_VIEWED, ModCriteria.NATION_APPLIED,
                ModCriteria.NATION_CITIZEN, ModCriteria.NATION_FOUNDED, ModCriteria.TREASURY_CONFIGURED,
                ModCriteria.UPKEEP_PAID, ModCriteria.COLD_PROTECTION, ModCriteria.REST_HEALED,
                ModCriteria.ELECTRICITY_BUILT, ModCriteria.FREIGHT_COMPLETED,
                ModCriteria.REINFORCEMENT_ACTIVATED, ModCriteria.TERRITORY_SEALED,
                ModCriteria.VEHICLE_CORE_REGISTERED, ModCriteria.VEHICLE_REPAIRED,
                ModCriteria.SIEGE_REINFORCEMENT_REPAIRED, ModCriteria.COMBAT_STARTED,
                ModCriteria.SIEGE_PARTICIPATED, ModCriteria.ARTILLERY_HIT,
                ModCriteria.MOBILE_FORCE_PARTICIPATED, ModCriteria.ALLY_REVIVED,
                ModCriteria.PRISONER_IMPRISONED, ModCriteria.PRISONER_FREED,
                ModCriteria.CORE_SABOTAGE_COMPLETED, ModCriteria.TERRITORY_DEFENDED,
                ModCriteria.WAREHOUSE_ENTERED, ModCriteria.WAREHOUSE_RAID_PARTICIPATED,
                ModCriteria.WAREHOUSE_BOSS_DEFEATED, ModCriteria.WAREHOUSE_LOOT_OPENED);
        for (String id : ADVANCEMENTS) {
            JsonObject criteria = advancement(id).getAsJsonObject("criteria");
            criteria.entrySet().forEach(entry -> {
                JsonObject criterion = entry.getValue().getAsJsonObject();
                if (!criterion.get("trigger").getAsString().equals("moveearth_addtional:event")) return;
                String event = criterion.getAsJsonObject("conditions").get("event").getAsString();
                assertTrue(registered.contains(event), id + " event " + event);
            });
        }
    }

    private static JsonObject advancement(String id) {
        return read("/data/moveearth_addtional/advancement/" + id + ".json");
    }

    private static void assertTranslation(JsonObject component, JsonObject ja, JsonObject en, String label) {
        assertNotNull(component, label);
        String key = component.get("translate").getAsString();
        assertTrue(ja.has(key), label + " ja_jp " + key);
        assertTrue(en.has(key), label + " en_us " + key);
    }

    private static JsonObject read(String path) {
        InputStream stream = MoveEarthAdvancementResourcesTest.class.getResourceAsStream(path);
        assertNotNull(stream, path);
        try (stream; InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new AssertionError(path, exception);
        }
    }
}
