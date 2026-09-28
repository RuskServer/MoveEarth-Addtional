package com.ruskserver.moveearth_addtional.s2.tip;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Stable IDs and translation keys for server-selected help tips. */
public final class TipCatalog {
    public static final List<Tip> ALL = List.of(
            tip("advancements", "basics"),
            tip("nation_hub", "basics"),
            tip("nation_application", "nation"),
            tip("nation_roles", "nation"),
            tip("diplomacy", "nation"),
            tip("nameplates", "combat"),
            tip("territory_core", "territory"),
            tip("reinforcement_welder", "reinforcement"),
            tip("reinforcement_curing", "reinforcement"),
            tip("reinforcement_overlay", "reinforcement"),
            tip("upkeep", "territory"),
            tip("territory_map", "territory"),
            tip("siege", "combat"),
            tip("siege_timer", "combat"),
            tip("counteroffensive", "combat"),
            tip("combat_logout", "combat"),
            tip("prisoners", "combat"),
            tip("rest_healing", "survival"),
            tip("nation_income", "economy"),
            tip("oxygen_depth", "survival"),
            tip("local_chat", "basics"),
            tip("industry_water_wheel", "industry"),
            tip("industry_steam", "industry"),
            tip("industry_brass", "industry"),
            tip("industry_electricity", "industry"),
            tip("industry_mekanism", "industry"),
            wikiTip("wiki", "basics")
    );
    public static final List<String> IDS = ALL.stream().map(Tip::id).toList();
    /** The loading screen cannot open links, so it leaves out the wiki tip. */
    public static final List<Tip> LOADING = ALL.stream().filter(tip -> !tip.wiki()).toList();
    private static final Map<String, Tip> BY_ID = ALL.stream()
            .collect(Collectors.toUnmodifiableMap(Tip::id, Function.identity()));

    private TipCatalog() { }

    public static Tip byId(String id) { return BY_ID.get(id); }

    /** Tips a player can be shown; the wiki tip only once the server has a wiki URL. */
    public static List<Tip> available(boolean wikiConfigured) {
        return wikiConfigured ? ALL : LOADING;
    }

    public static List<String> availableIds(boolean wikiConfigured) {
        return available(wikiConfigured).stream().map(Tip::id).toList();
    }

    private static Tip tip(String id, String category) {
        return new Tip(id, category, "tip.moveearth_addtional." + id + ".title",
                "tip.moveearth_addtional." + id + ".body", false);
    }

    private static Tip wikiTip(String id, String category) {
        return new Tip(id, category, "tip.moveearth_addtional." + id + ".title",
                "tip.moveearth_addtional." + id + ".body", true);
    }

    /** {@code wiki} tips carry a button that opens the server's configured wiki URL. */
    public record Tip(String id, String category, String titleKey, String bodyKey, boolean wiki) { }
}
