package com.ruskserver.moveearth_addtional.raid;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

public enum AirshipRaidDifficulty {
    NORMAL,
    ELITE,
    LARGE;

    private static final SimpleCommandExceptionType INVALID = new SimpleCommandExceptionType(
            Component.literal("難易度は normal、elite、large のいずれかを指定してください。"));

    public static AirshipRaidDifficulty parse(String value) throws CommandSyntaxException {
        try {
            return valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw INVALID.create();
        }
    }
}
