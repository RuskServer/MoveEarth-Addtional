package com.ruskserver.moveearth_addtional.s2.tutorial;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TutorialCatalogTest {
    private static final String ADVANCEMENTS = "/data/moveearth_addtional/advancement/";

    @Test
    void stepsEndAtBrassAndHaveUniqueIds() {
        List<TutorialCatalog.Step> steps = TutorialCatalog.STEPS;
        assertEquals(12, steps.size());
        assertEquals("brass", steps.getLast().id());
        assertEquals(steps.size(), new HashSet<>(steps.stream().map(TutorialCatalog.Step::id).toList()).size());
    }

    @Test
    void everyCompletingAdvancementExists() {
        for (TutorialCatalog.Step step : TutorialCatalog.STEPS) {
            if (step.kind() == TutorialCatalog.Kind.NATION) continue;
            assertNotNull(getClass().getResource(ADVANCEMENTS + step.advancement() + ".json"), step.id());
            if (step.kind() == TutorialCatalog.Kind.DEFENSE) {
                assertNotNull(getClass().getResource(ADVANCEMENTS + step.memberAdvancement() + ".json"), step.id());
            }
        }
    }

    @Test
    void theTutorialOnlyAdvancementsStayOutOfTheTree() {
        for (String path : List.of("region_viewed", "andesite_casing", "mixer_basin", "zinc_ingot")) {
            JsonObject advancement = read(ADVANCEMENTS + "tutorial/" + path + ".json");
            assertNull(advancement.get("display"), path);
            assertNull(advancement.get("parent"), path);
        }
    }

    @Test
    void everyStepWordingExistsInJapaneseAndEnglish() {
        for (String language : List.of("ja_jp", "en_us")) {
            JsonObject lang = read("/assets/moveearth_addtional/lang/" + language + ".json");
            for (TutorialCatalog.Step step : TutorialCatalog.STEPS) {
                for (boolean canReinforce : List.of(true, false)) {
                    String id = "tutorial.moveearth_addtional." + TutorialCatalog.textId(step, canReinforce);
                    assertTrue(lang.has(id + ".title"), language + " " + id);
                    assertTrue(lang.has(id + ".detail"), language + " " + id);
                }
            }
            for (String key : List.of("header", "skip_hint", "skipped", "restarted", "completed")) {
                assertTrue(lang.has("tutorial.moveearth_addtional." + key), language + " " + key);
            }
        }
    }

    @Test
    void currentIsTheFirstUnfinishedStep() {
        assertEquals(0, TutorialCatalog.current(step -> false));
        assertEquals(-1, TutorialCatalog.current(step -> true));
        // Steps done out of order are passed over; the first gap is what shows.
        int nation = TutorialCatalog.STEPS.stream().map(TutorialCatalog.Step::id).toList().indexOf("nation");
        assertEquals(nation, TutorialCatalog.current(step -> !step.id().equals("nation")));
    }

    @Test
    void theNationStepDoesNotHoldBackTheRest() {
        List<String> ids = TutorialCatalog.STEPS.stream().map(TutorialCatalog.Step::id).toList();
        int nation = ids.indexOf("nation");
        // Waiting for approval or starting in the wilderness: the next goal after it shows.
        assertEquals(nation + 1, TutorialCatalog.current(step -> ids.indexOf(step.id()) < nation));
        assertEquals(ids.indexOf("brass"), TutorialCatalog.current(step -> !step.id().equals("brass")
                && !step.id().equals("nation")));
        // Only once nothing else is left does it show; the tutorial is not over until it is done.
        assertEquals(nation, TutorialCatalog.current(step -> !step.id().equals("nation")));
        assertTrue(TutorialCatalog.deferred(TutorialCatalog.STEPS.get(nation)));
        assertEquals(1, TutorialCatalog.STEPS.stream().filter(TutorialCatalog::deferred).count());
    }

    @Test
    void placedBlockCriteriaFilterByLocation() throws IOException {
        // 1.21 dropped placed_block's "block" field; a criterion that still uses it
        // matches any placed block, which once completed the water wheel advancement.
        Path root = Path.of("src/main/resources" + ADVANCEMENTS);
        Set<String> checked = new HashSet<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                JsonObject criteria = JsonParser.parseString(Files.readString(file)).getAsJsonObject()
                        .getAsJsonObject("criteria");
                for (var entry : criteria.entrySet()) {
                    JsonObject criterion = entry.getValue().getAsJsonObject();
                    if (!criterion.get("trigger").getAsString().equals("minecraft:placed_block")) continue;
                    JsonObject conditions = criterion.getAsJsonObject("conditions");
                    assertFalse(conditions.has("block"), file + " " + entry.getKey());
                    JsonElement location = conditions.get("location");
                    assertNotNull(location, file + " " + entry.getKey());
                    checked.add(root.relativize(file).toString());
                }
            }
        }
        assertTrue(checked.contains("industry/rotation.json"));
        assertTrue(checked.contains("tutorial/mixer_basin.json"));
    }

    private static JsonObject read(String resource) {
        try (var reader = new InputStreamReader(Objects.requireNonNull(
                TutorialCatalogTest.class.getResourceAsStream(resource), resource), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
