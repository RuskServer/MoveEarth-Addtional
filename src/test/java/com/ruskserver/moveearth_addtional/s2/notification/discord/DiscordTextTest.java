package com.ruskserver.moveearth_addtional.s2.notification.discord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordTextTest {
    @Test
    void escapesMentionsAndMarkdown() {
        assertEquals("@\u200Beveryone \\*alert\\* \\[x\\]", DiscordText.safe("@everyone *alert* [x]"));
    }

    @Test
    void limitsEmbedFieldInput() {
        assertTrue(DiscordText.safe("x".repeat(800)).length() <= 256);
    }

    @Test
    void neverLeavesAnUnescapedBoundaryCharacter() {
        String escaped = DiscordText.safe("x".repeat(255) + "*");
        assertEquals(255, escaped.length());
    }
}
