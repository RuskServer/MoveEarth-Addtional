package com.ruskserver.moveearth_addtional.client.loading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveEarthLoadingLayoutTest {
    @Test
    void wideLayoutUsesBottomPanelAndPreservesLogoAspect() {
        var layout = MoveEarthLoadingLayout.calculate(960, 540);

        assertTrue(layout.panel().y() > 540 / 2);
        assertEquals(540 - 16, layout.panel().bottom());
        assertTrue(layout.panel().height() < 90);
        assertTrue(layout.logo().width() >= 400);
        assertTrue(layout.logo().bottom() < layout.panel().y());
        assertTrue(Math.abs(layout.logo().width() / (double) layout.logo().height()
                - 1024.0D / 269.0D) < 0.05D);
        assertTrue(layout.progress().x() > layout.panel().x());
        assertTrue(layout.progress().right() < layout.panel().right());
    }

    @Test
    void compactLayoutKeepsEverythingOnScreen() {
        var layout = MoveEarthLoadingLayout.calculate(320, 240);

        assertTrue(layout.panel().x() >= 0);
        assertTrue(layout.panel().bottom() <= 240);
        assertEquals(72, layout.panel().height());
        assertTrue(layout.logo().width() >= 190);
        assertTrue(layout.cancel().right() < layout.panel().right());
        assertTrue(layout.progress().bottom() < layout.panel().bottom());
        assertTrue(layout.logo().x() >= 0);
        assertTrue(layout.logo().right() <= 320);
        assertTrue(layout.logo().bottom() < layout.panel().y());
    }
}
