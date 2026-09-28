package com.ruskserver.moveearth_addtional.config;

import com.ruskserver.moveearth_addtional.s2.tip.TipWikiLink;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned pacing for periodic player tips. */
public final class TipConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Enables periodic MoveEarth tips. Individual players can still opt out.")
            .define("enabled", true);
    private static final ModConfigSpec.IntValue INITIAL_DELAY_MINUTES = BUILDER
            .comment("Online minutes before a player's first tip is shown.")
            .defineInRange("initialDelayMinutes", 5, 0, 120);
    private static final ModConfigSpec.IntValue INTERVAL_MINUTES = BUILDER
            .comment("Online minutes between tips. Due tips wait until combat and captivity end.")
            .defineInRange("intervalMinutes", 30, 15, 120);
    private static final ModConfigSpec.IntValue HISTORY_SIZE = BUILDER
            .comment("Number of recently displayed tips retained per player.")
            .defineInRange("historySize", 20, 5, 100);
    private static final ModConfigSpec.ConfigValue<String> WIKI_URL = BUILDER
            .comment("MoveEarth wiki opened by the wiki tip. Set it empty to leave the wiki tip out.",
                    "Must start with https:// or http://.")
            .define("wikiUrl", "https://rusklabo.github.io/moveearth-web/guide/start/", value -> value instanceof String url && TipWikiLink.isValid(url));

    public static final ModConfigSpec SPEC = BUILDER.build();

    private TipConfig() { }

    public static boolean enabled() { return ENABLED.getAsBoolean(); }
    public static int initialDelaySeconds() { return INITIAL_DELAY_MINUTES.getAsInt() * 60; }
    public static int intervalSeconds() { return INTERVAL_MINUTES.getAsInt() * 60; }
    public static int historySize() { return HISTORY_SIZE.getAsInt(); }

    /** The configured wiki URL, or empty when none is set. */
    public static String wikiUrl() {
        String url = WIKI_URL.get().trim();
        return TipWikiLink.isValid(url) ? url : "";
    }

    public static boolean wikiConfigured() { return !wikiUrl().isEmpty(); }
}
