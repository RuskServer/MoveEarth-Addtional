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
            tip("combat_logout", "combat"),
            tip("prisoners", "combat"),
            tip("rest_healing", "survival")
    );
    public static final List<String> IDS = ALL.stream().map(Tip::id).toList();
    private static final Map<String, Tip> BY_ID = ALL.stream()
            .collect(Collectors.toUnmodifiableMap(Tip::id, Function.identity()));

    private TipCatalog() { }

    public static Tip byId(String id) { return BY_ID.get(id); }

    private static Tip tip(String id, String category) {
        return new Tip(id, category, "tip.moveearth_addtional." + id + ".title",
                "tip.moveearth_addtional." + id + ".body");
    }

    public record Tip(String id, String category, String titleKey, String bodyKey) { }
}
