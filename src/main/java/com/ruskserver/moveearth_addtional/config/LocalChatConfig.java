package com.ruskserver.moveearth_addtional.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned range for ordinary player chat. */
public final class LocalChatConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.IntValue RADIUS = BUILDER
            .comment("Maximum ordinary-chat distance in blocks. Chat never crosses dimensions.")
            .defineInRange("radiusBlocks", 100, 1, 1024);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private LocalChatConfig() { }

    public static int radiusBlocks() {
        return RADIUS.getAsInt();
    }
}
