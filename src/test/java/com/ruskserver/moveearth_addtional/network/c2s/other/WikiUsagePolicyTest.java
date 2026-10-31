package com.ruskserver.moveearth_addtional.network.c2s.other;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WikiUsagePolicyTest {
    @Test
    void keepsKnownSourcesAndItemIdsOnly() {
        assertEquals("tab", WikiUsagePolicy.openedDetail("tab", "minecraft:stone"));
        assertEquals("item|moveearth_addtional:territory_core",
                WikiUsagePolicy.openedDetail("item", "moveearth_addtional:territory_core"));
        assertEquals("held_item", WikiUsagePolicy.openedDetail("held_item", "not an id"));
        assertEquals("other", WikiUsagePolicy.openedDetail("<script>", ""));
    }

    @Test
    void acceptsOnlyWikiSlugs() {
        assertEquals("guide/siege", WikiUsagePolicy.page("guide/siege"));
        assertEquals("", WikiUsagePolicy.page("../etc/passwd"));
        assertEquals("", WikiUsagePolicy.page("Guide Page"));
        assertEquals("", WikiUsagePolicy.page(null));
    }
}
