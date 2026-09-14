package com.ruskserver.moveearth_addtional.s2.notification.discord;

/** Small, JDA-independent boundary for safe user-controlled Discord embed text. */
public final class DiscordText {
    private static final int MAX_LENGTH = 256;
    private static final char ZERO_WIDTH_SPACE = '\u200B';

    private DiscordText() { }

    public static String safe(String value) {
        String source = value == null ? "" : value;
        StringBuilder result = new StringBuilder(Math.min(source.length(), MAX_LENGTH));
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '@') {
                if (result.length() + 2 > MAX_LENGTH) break;
                result.append('@').append(ZERO_WIDTH_SPACE);
                continue;
            }
            if ("\\*_~`[]()>".indexOf(character) >= 0) {
                if (result.length() + 2 > MAX_LENGTH) break;
                result.append('\\');
            } else if (result.length() + 1 > MAX_LENGTH) {
                break;
            }
            result.append(character);
        }
        return result.toString();
    }
}
