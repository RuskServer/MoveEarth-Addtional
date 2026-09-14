package com.ruskserver.moveearth_addtional.s2.notification;

import java.util.Optional;

/** JDA-free boundary used by common network handlers for dedicated-server Discord linking. */
public interface DiscordLinkAccess {
    boolean isReady();

    Optional<DiscordLinkCodeRegistry.PendingLink> consumeLinkCode(String code);

    Optional<DiscordLinkCodeRegistry.PendingLink> consumeAccountLinkCode(String code);

    boolean canDeliverTo(long guildId, long channelId);
}
