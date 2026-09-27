package com.ruskserver.moveearth_addtional.s2.notification;

import java.util.Optional;

/** JDA-free boundary used by common network handlers for dedicated-server Discord linking. */
public interface DiscordLinkAccess {
    boolean isReady();

    Optional<DiscordLinkCodeRegistry.PendingLink> consumeLinkCode(String code);

    Optional<DiscordLinkCodeRegistry.PendingLink> consumeAccountLinkCode(String code);

    boolean canDeliverTo(long guildId, long channelId);

    default BotState state() { return isReady() ? BotState.READY : BotState.UNAVAILABLE; }

    default String guildName(long guildId) { return ""; }

    default String channelName(long guildId, long channelId) { return ""; }

    default String roleName(long guildId, long roleId) { return ""; }

    /** Invite URL for adding the bot to a Discord server; empty while the bot is not connected. */
    default String inviteUrl() { return ""; }

    default boolean reconnect() { return false; }

    enum BotState { DISABLED, STARTING, READY, AUTHENTICATION_FAILED, UNAVAILABLE }
}
