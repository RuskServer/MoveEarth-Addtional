package com.ruskserver.moveearth_addtional.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** What the world does outside the 19:00-23:00 opening hours. */
public final class ScheduleConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.BooleanValue FREEZE_WORLD_WHILE_CLOSED = BUILDER
            .comment("Stop the day/night and weather cycles on a dedicated server outside opening hours,",
                    "so in-game days, and seasons from mods such as Ecliptic Seasons that count them,",
                    "only pass while players can be online. Operator gamerules are never changed;",
                    "their configured values take effect again at opening time.")
            .define("freezeWorldWhileClosed", true);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private ScheduleConfig() { }

    public static boolean freezeWorldWhileClosed() {
        return FREEZE_WORLD_WHILE_CLOSED.getAsBoolean();
    }
}
