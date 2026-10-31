package com.ruskserver.moveearth_addtional.compat.vehicle;

import org.junit.jupiter.api.Test;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class SableSafetyMessagesTest {
    @Test
    void japaneseFallbackFormatsAllArgumentsWithoutClientLangEntries() {
        String template = SableSafetyMessageCatalog.template(
                SableSafetyMessageCatalog.Message.ASSEMBLY_REFUSED, "ja_jp");
        String rendered = String.format(Locale.ROOT, template, 128, 65, 64);
        assertTrue(rendered.contains("組み立てを中止"));
        assertTrue(rendered.contains("128"));
        assertTrue(rendered.contains("65"));
        assertTrue(rendered.contains("64"));
        assertFalse(rendered.contains("message.moveearth_addtional"));
        assertFalse(rendered.contains("%"));
    }

    @Test
    void englishFallbackAndUnknownLanguageAreReadable() {
        var message = SableSafetyMessageCatalog.Message.DEBRIS_WARNING;
        String english = SableSafetyMessageCatalog.template(message, "en_us");
        String rendered = String.format(Locale.ROOT, english, 7, 60);
        assertTrue(rendered.contains("7 nearby physics bodies"));
        assertTrue(rendered.contains("60 seconds"));
        assertEquals(english, SableSafetyMessageCatalog.template(message, "../../untrusted"));
    }

    @Test
    void everyWarningHasBothTemplatesAndAStableKeyForUpdatedClients() {
        for (var message : SableSafetyMessageCatalog.Message.values()) {
            assertFalse(SableSafetyMessageCatalog.template(message, "ja_jp").isBlank());
            assertFalse(SableSafetyMessageCatalog.template(message, "en_us").isBlank());
            assertTrue(message.key().startsWith("message.moveearth_addtional."));
        }
    }
}
