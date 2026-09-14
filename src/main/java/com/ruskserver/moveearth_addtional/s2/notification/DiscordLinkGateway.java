package com.ruskserver.moveearth_addtional.s2.notification;

import java.util.Optional;

/** Holds the dedicated-server Discord implementation without exposing JDA types to common code. */
public final class DiscordLinkGateway {
    private static final DiscordLinkAccess OFFLINE = new DiscordLinkAccess() {
        @Override public boolean isReady() { return false; }
        @Override public Optional<DiscordLinkCodeRegistry.PendingLink> consumeLinkCode(String code) {
            return Optional.empty();
        }
        @Override public Optional<DiscordLinkCodeRegistry.PendingLink> consumeAccountLinkCode(String code) {
            return Optional.empty();
        }
        @Override public boolean canDeliverTo(long guildId, long channelId) { return false; }
    };

    private static volatile DiscordLinkAccess access = OFFLINE;

    private DiscordLinkGateway() { }

    public static DiscordLinkAccess access() { return access; }

    public static void install(DiscordLinkAccess implementation) {
        access = implementation == null ? OFFLINE : implementation;
    }

    public static void reset() { access = OFFLINE; }
}
