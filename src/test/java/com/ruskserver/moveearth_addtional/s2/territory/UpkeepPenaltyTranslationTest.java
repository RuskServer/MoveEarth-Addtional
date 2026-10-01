package com.ruskserver.moveearth_addtional.s2.territory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UpkeepPenaltyTranslationTest {
    /** The vehicle core screen shows every penalty by its lower-case name. */
    @Test
    void everyPenaltyHasAVehicleScreenLabel() throws Exception {
        for (String locale : new String[] {"ja_jp", "en_us"}) {
            JsonObject lang;
            try (var reader = new InputStreamReader(getClass().getResourceAsStream(
                    "/assets/moveearth_addtional/lang/" + locale + ".json"), StandardCharsets.UTF_8)) {
                lang = JsonParser.parseReader(reader).getAsJsonObject();
            }
            for (UpkeepPenalty penalty : UpkeepPenalty.values()) {
                String key = "screen.moveearth_addtional.vehicle_core.upkeep_state."
                        + penalty.name().toLowerCase(Locale.ROOT);
                assertTrue(lang.has(key), locale + " " + key);
            }
        }
    }
}
