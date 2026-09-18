package com.ruskserver.moveearth_addtional.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-owned first-run and presentation preferences for the title flow. */
public final class StartupClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.BooleanValue SETUP_COMPLETED = BUILDER
            .comment("Whether the MoveEarth first-run accessibility and audio setup was completed.")
            .define("setupCompleted", false);
    private static final ModConfigSpec.BooleanValue REDUCED_MOTION = BUILDER
            .comment("Reduces title-screen movement and disables decorative meteor animation.")
            .define("reducedMotion", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private StartupClientConfig() { }

    public static boolean setupCompleted() {
        return SETUP_COMPLETED.getAsBoolean();
    }

    public static boolean reducedMotion() {
        return REDUCED_MOTION.getAsBoolean();
    }

    public static void completeSetup(boolean reducedMotion) {
        REDUCED_MOTION.set(reducedMotion);
        SETUP_COMPLETED.set(true);
        SPEC.save();
    }
}
