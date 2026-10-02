package com.ruskserver.moveearth_addtional.s2.notification;

/** Builds the bot's server-invite URL from its application ID so it follows token or application changes. */
public final class DiscordInviteLink {
    // Discord permission bit positions.
    private static final int VIEW_CHANNEL = 10;
    private static final int SEND_MESSAGES = 11;
    private static final int EMBED_LINKS = 14;
    private static final int READ_MESSAGE_HISTORY = 16;
    private static final int MENTION_EVERYONE = 17;
    private static final int USE_APPLICATION_COMMANDS = 31;

    /**
     * View channels, send messages, embed links, read history and mention roles, plus
     * slash commands. Built from named bits: a hand-written constant once granted
     * webhook management and activities instead, and no way to see a private channel.
     */
    public static final long PERMISSIONS = bit(VIEW_CHANNEL) | bit(SEND_MESSAGES) | bit(EMBED_LINKS)
            | bit(READ_MESSAGE_HISTORY) | bit(MENTION_EVERYONE) | bit(USE_APPLICATION_COMMANDS);
    private static final String PREFIX = "https://discord.com/oauth2/authorize?client_id=";

    private DiscordInviteLink() { }

    private static long bit(int position) {
        return 1L << position;
    }

    /** Returns an empty string unless {@code applicationId} is a Discord snowflake. */
    public static String url(String applicationId) {
        if (applicationId == null || !applicationId.matches("[0-9]{15,21}")) return "";
        return PREFIX + applicationId + "&permissions=" + PERMISSIONS
                + "&integration_type=0&scope=bot+applications.commands";
    }

    /** Client-side guard: only open links that point at Discord's own OAuth page. */
    public static boolean trusted(String url) {
        return url != null && url.startsWith(PREFIX) && url.length() <= 256;
    }
}
