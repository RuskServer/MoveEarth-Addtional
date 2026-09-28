package com.ruskserver.moveearth_addtional.client.upscale;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only settings for the optional SMAA anti-aliasing and FSR 1.0 upscaling. */
public final class UpscaleClientConfig {
    public enum Mode { OFF, SMAA, SMAA_FSR }

    /** DEPTH marks only geometry edges; LUMA also treats texture detail as edges (SMAA's default). */
    public enum EdgeDetection { DEPTH, LUMA }

    /** AMD's FSR 1.0 presets: per-axis ratio from display to world render resolution. */
    public enum Quality {
        ULTRA_QUALITY(1.3F), QUALITY(1.5F), BALANCED(1.7F), PERFORMANCE(2.0F);

        final float factor;

        Quality(float factor) { this.factor = factor; }
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.EnumValue<Mode> MODE = BUILDER
            .comment("Experimental anti-aliasing / upscaling. Defaults to off.")
            .defineEnum("mode", Mode.OFF);
    private static final ModConfigSpec.EnumValue<Quality> QUALITY = BUILDER
            .comment("FSR 1.0 quality preset: how far below display resolution the world is rendered.")
            .defineEnum("quality", Quality.ULTRA_QUALITY);
    private static final ModConfigSpec.EnumValue<EdgeDetection> EDGES = BUILDER
            .comment("What SMAA treats as an edge: world depth (geometry only) or brightness (SMAA default).")
            .defineEnum("edgeDetection", EdgeDetection.DEPTH);
    private static final ModConfigSpec.IntValue SHARPNESS = BUILDER
            .comment("FSR RCAS sharpening strength in percent (100 = strongest).")
            .defineInRange("sharpnessPercent", 80, 0, 100);
    private static final ModConfigSpec.BooleanValue SHOW_STATUS = BUILDER
            .comment("Show the anti-aliasing status on the HUD.")
            .define("showStatus", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private UpscaleClientConfig() { }

    public static Mode mode() { return MODE.get(); }
    public static Quality quality() { return QUALITY.get(); }
    public static EdgeDetection edgeDetection() { return EDGES.get(); }
    public static int sharpnessPercent() { return SHARPNESS.getAsInt(); }
    public static boolean showStatus() { return SHOW_STATUS.getAsBoolean(); }

    public static void setMode(Mode value) { MODE.set(value); }
    public static void setQuality(Quality value) { QUALITY.set(value); }
    public static void setEdgeDetection(EdgeDetection value) { EDGES.set(value); }
    public static void setSharpnessPercent(int value) { SHARPNESS.set(value); }
    public static void setShowStatus(boolean value) { SHOW_STATUS.set(value); }
}
