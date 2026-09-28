package com.ruskserver.moveearth_addtional.s2.tip;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TipAdvancementLinksTest {
    @Test
    void everyLinkJoinsARealAdvancementToARealTip() {
        TipAdvancementLinks.NEXT_TIP.forEach((advancement, tip) -> {
            assertNotNull(getClass().getResource(
                    "/data/moveearth_addtional/advancement/" + advancement + ".json"), advancement);
            assertNotNull(TipCatalog.byId(tip), tip);
        });
    }

    @Test
    void unlinkedAdvancementsShowNothing() {
        assertNull(TipAdvancementLinks.nextTip("industry/steam"));
        assertNull(TipAdvancementLinks.nextTip("warfare/combat"));
    }
}
