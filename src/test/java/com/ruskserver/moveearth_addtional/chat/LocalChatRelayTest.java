package com.ruskserver.moveearth_addtional.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LocalChatRelayTest {
    @Test
    void identifiesVanillaChatForwardedAsSystemText() {
        assertEquals("zirconium1131", LocalChatRelay.senderNameFromVanillaLine(
                "<zirconium1131> hello").orElseThrow());
    }

    @Test
    void doesNotTreatOtherPlainTextAsLocalChat() {
        assertTrue(LocalChatRelay.senderNameFromVanillaLine("The event starts now").isEmpty());
        assertTrue(LocalChatRelay.senderNameFromVanillaLine("<RuskLabo> hello").isPresent());
        assertTrue(LocalChatRelay.senderNameFromVanillaLine("<RuskLabo>hello").isEmpty());
        assertTrue(LocalChatRelay.senderNameFromVanillaLine("<Not a player> hello").isEmpty());
    }
}
