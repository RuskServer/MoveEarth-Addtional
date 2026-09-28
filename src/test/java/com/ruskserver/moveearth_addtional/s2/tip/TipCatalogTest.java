package com.ruskserver.moveearth_addtional.s2.tip;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TipCatalogTest {
    @Test
    void idsAreUnique() {
        assertEquals(TipCatalog.IDS.size(), new HashSet<>(TipCatalog.IDS).size());
    }

    @Test
    void everyTipHasJapaneseAndEnglishText() throws Exception {
        for (String language : List.of("ja_jp", "en_us")) {
            JsonObject lang = load(language);
            for (TipCatalog.Tip tip : TipCatalog.ALL) {
                assertTrue(lang.has(tip.titleKey()), language + " " + tip.titleKey());
                assertTrue(lang.has(tip.bodyKey()), language + " " + tip.bodyKey());
            }
            assertTrue(lang.has("tip.moveearth_addtional.action.wiki"), language);
        }
    }

    @Test
    void theWikiTipIsShownOnlyWithAWikiUrlAndNeverWhileLoading() {
        assertTrue(TipCatalog.availableIds(true).contains("wiki"));
        assertFalse(TipCatalog.availableIds(false).contains("wiki"));
        assertTrue(TipCatalog.LOADING.stream().noneMatch(TipCatalog.Tip::wiki));
        assertTrue(TipCatalog.availableIds(false).contains("industry_mekanism"));
    }

    private static JsonObject load(String language) throws Exception {
        var stream = Objects.requireNonNull(TipCatalogTest.class.getResourceAsStream(
                "/assets/moveearth_addtional/lang/" + language + ".json"), language);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
