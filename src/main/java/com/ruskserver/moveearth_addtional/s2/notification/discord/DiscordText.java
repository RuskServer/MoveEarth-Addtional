package com.ruskserver.moveearth_addtional.s2.notification.discord;

/** Small, JDA-independent boundary for safe user-controlled Discord embed text. */
public final class DiscordText {
    private static final int MAX_LENGTH = 512;

    private DiscordText() { }

    public static String safe(String value) {
        String source = value == null ? "" : value;
        StringBuilder result = new StringBuilder(Math.min(source.length(), MAX_LENGTH));
        for (int index = 0; index < source.length() && result.length() < MAX_LENGTH; index++) {
            char character = source.charAt(index);
            if ("\\*_~`[]()>@".indexOf(character) >= 0 && result.length() + 1 < MAX_LENGTH) {
                result.append('\\');
            }
            result.append(character);
        }
        return result.toString();
    }
}
