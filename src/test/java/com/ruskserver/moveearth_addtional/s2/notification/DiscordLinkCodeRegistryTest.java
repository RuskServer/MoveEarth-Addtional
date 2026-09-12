package com.ruskserver.moveearth_addtional.s2.notification;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordLinkCodeRegistryTest {
    @Test
    void codeIsNormalizedAndConsumedOnlyOnce() {
        DiscordLinkCodeRegistry registry = new DiscordLinkCodeRegistry(new Random(7L));
        String code = registry.create(10L, 20L, 30L, 1_000L, 10_000L);

        assertEquals(8, code.length());
        assertTrue(registry.consume(code.toLowerCase(), 2_000L).isPresent());
        assertFalse(registry.consume(code, 2_000L).isPresent());
    }

    @Test
    void expiredAndSupersededCodesCannotBeUsed() {
        DiscordLinkCodeRegistry registry = new DiscordLinkCodeRegistry(new Random(9L));
        String oldCode = registry.create(10L, 20L, 30L, 1_000L, 10_000L);
        String currentCode = registry.create(10L, 21L, 31L, 2_000L, 10_000L);

        assertFalse(registry.consume(oldCode, 3_000L).isPresent());
        assertEquals(21L, registry.consume(currentCode, 3_000L).orElseThrow().channelId());

        String expiringCode = registry.create(11L, 22L, 32L, 5_000L, 100L);
        assertFalse(registry.consume(expiringCode, 5_100L).isPresent());
    }
}
