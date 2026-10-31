package com.ruskserver.moveearth_addtional.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Per-player presentation preferences: HUD visibility, effect strength and
 * announcer volume. Nothing here may change what a player can learn about the
 * world; every option only hides or softens the player's own feedback.
 */
public final class ClientDisplayConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("hud");
    }

    public static final ModConfigSpec.BooleanValue SHOW_BALANCE = BUILDER
            .comment("Show the money balance in the top-right corner.")
            .define("showBalance", true);
    public static final ModConfigSpec.BooleanValue SHOW_EVENT_HUD = BUILDER
            .comment("Show the running event's scoreboard on the right edge.")
            .define("showEventHud", true);
    public static final ModConfigSpec.EnumValue<TemperatureHud> TEMPERATURE_HUD = BUILDER
            .comment("When to show the Cold Sweat temperature panel in the top-left corner:",
                    "ALWAYS, WHEN_UNCOMFORTABLE (hidden while comfortable) or NEVER.")
            .defineEnum("temperatureHud", TemperatureHud.ALWAYS);

    static {
        BUILDER.pop().push("effects");
    }

    public static final ModConfigSpec.DoubleValue SHIELD_FLASH_STRENGTH = BUILDER
            .comment("Strength of the green screen flash when the MekaSuit heavy-hit shield blocks a hit. 0 turns it off;",
                    "the action bar message still appears.")
            .defineInRange("shieldFlashStrength", 1.0D, 0.0D, 1.0D);
    public static final ModConfigSpec.BooleanValue CALM_DEATH_SCREEN = BUILDER
            .comment("Replace the flickering VHS noise on the death screen with a still black screen.")
            .define("calmDeathScreen", false);
    public static final ModConfigSpec.BooleanValue NATION_TABLIST = BUILDER
            .comment("Show nation tags, coloured by relation, in the tab list and group players by nation.")
            .define("nationTablist", true);
    public static final ModConfigSpec.BooleanValue JOIN_CINEMATIC = BUILDER
            .comment("On joining a server, open on a view from high above and drop into your own eyes.",
                    "Any key or click ends it. Off with reduced motion too.")
            .define("joinCinematic", true);

    public static final ModConfigSpec.BooleanValue EARLY_WORLD_ENTRY = BUILDER
            .comment("After the server hand-off and receipt of terrain under the player, allow control without waiting for rendering.",
                    "Surroundings may briefly be unrendered. Disable to use vanilla loading readiness.")
            .define("earlyWorldEntry", true);

    static {
        BUILDER.pop().push("audio");
    }

    public static final ModConfigSpec.DoubleValue ANNOUNCER_VOLUME = BUILDER
            .comment("Volume of the PvP announcer voice (first blood, kill streaks, match start).")
            .defineInRange("announcerVolume", 1.0D, 0.0D, 1.0D);
    public static final ModConfigSpec.DoubleValue NOTICE_VOLUME = BUILDER
            .comment("Volume of the server notice chime.")
            .defineInRange("noticeVolume", 1.0D, 0.0D, 1.0D);
    public static final ModConfigSpec.DoubleValue UI_HOVER_VOLUME = BUILDER
            .comment("Volume of the sound played when the cursor moves onto a button. 0 turns it off.")
            .defineInRange("uiHoverVolume", 1.0D, 0.0D, 1.0D);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientDisplayConfig() { }

    /** When the temperature panel shows. */
    public enum TemperatureHud { ALWAYS, WHEN_UNCOMFORTABLE, NEVER }
}
