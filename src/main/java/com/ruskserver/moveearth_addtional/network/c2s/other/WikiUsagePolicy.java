package com.ruskserver.moveearth_addtional.network.c2s.other;

import java.util.Set;

/**
 * What a client may report about its wiki use, cut down to known shapes before it reaches analytics, so a
 * modified client cannot fill the event log with arbitrary text. Pure, so it is unit tested.
 */
public final class WikiUsagePolicy {
    static final Set<String> SOURCES = Set.of("tab", "key", "held_item", "item");

    private WikiUsagePolicy() { }

    /** {@code source} or {@code source|namespace:item}; unknown sources become "other". */
    public static String openedDetail(String source, String item) {
        String known = SOURCES.contains(source) ? source : "other";
        String id = item == null ? "" : item.trim();
        boolean itemSource = known.equals("item") || known.equals("held_item");
        return itemSource && id.matches("[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,64}") ? known + "|" + id : known;
    }

    /** A wiki slug such as guide/siege, or empty when it is not one. */
    public static String page(String page) {
        String slug = page == null ? "" : page.trim();
        return slug.matches("[a-z0-9][a-z0-9_/-]{0,95}") && !slug.contains("//") ? slug : "";
    }
}
