package com.ruskserver.moveearth_addtional.s2.notification;

public record NotificationPreference(InGameMode inGame, DiscordMode discord, MentionPolicy mention) {
    public NotificationPreference {
        if (inGame == null) inGame = InGameMode.IMMEDIATE;
        if (discord == null) discord = DiscordMode.OFF;
        if (mention == null) mention = MentionPolicy.NONE;
    }

    public enum InGameMode { IMMEDIATE, OFF }
    public enum DiscordMode { IMMEDIATE, DIGEST, OFF }
    public enum MentionPolicy { NONE, URGENT_ONLY }
}
