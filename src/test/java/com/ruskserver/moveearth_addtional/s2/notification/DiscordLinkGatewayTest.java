package com.ruskserver.moveearth_addtional.s2.notification;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordLinkGatewayTest {
    @AfterEach
    void resetGateway() {
        DiscordLinkGateway.reset();
    }

    @Test
    void defaultsToAnOfflineJdaFreeImplementation() {
        assertFalse(DiscordLinkGateway.access().isReady());
        assertTrue(DiscordLinkGateway.access().consumeLinkCode("code").isEmpty());
        assertFalse(DiscordLinkGateway.access().canDeliverTo(1L, 2L));
    }

    @Test
    void dedicatedServerCanInstallAndRemoveItsImplementation() {
        DiscordLinkAccess installed = new DiscordLinkAccess() {
            @Override public boolean isReady() { return true; }
            @Override public Optional<DiscordLinkCodeRegistry.PendingLink> consumeLinkCode(String code) {
                return Optional.empty();
            }
            @Override public Optional<DiscordLinkCodeRegistry.PendingLink> consumeAccountLinkCode(String code) {
                return Optional.empty();
            }
            @Override public boolean canDeliverTo(long guildId, long channelId) { return true; }
        };
        DiscordLinkGateway.install(installed);
        assertSame(installed, DiscordLinkGateway.access());
        assertTrue(DiscordLinkGateway.access().isReady());

        DiscordLinkGateway.reset();
        assertFalse(DiscordLinkGateway.access().isReady());
    }
}
