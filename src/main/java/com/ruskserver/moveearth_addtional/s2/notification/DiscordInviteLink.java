package com.ruskserver.moveearth_addtional.s2.notification;

/** Builds the bot's server-invite URL from its application ID so it follows token or application changes. */
public final class DiscordInviteLink {
    /** View channels, send messages, embed links, read history and mention roles, plus slash commands. */
    public static final long PERMISSIONS = 552440170496L;
    private static final String PREFIX = "https://discord.com/oauth2/authorize?client_id=";

    private DiscordInviteLink() { }

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
