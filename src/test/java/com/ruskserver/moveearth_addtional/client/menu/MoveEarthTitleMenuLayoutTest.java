package com.ruskserver.moveearth_addtional.client.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveEarthTitleMenuLayoutTest {
    @Test
    void wideLayoutKeepsMenuLeftAndChangelogRight() {
        var layout = MoveEarthTitleMenuLayout.calculate(960, 540);

        assertEquals(MoveEarthTitleMenuLayout.BUTTON_COUNT, layout.buttons().size());
        assertEquals(480, layout.logo().x() + layout.logo().width() / 2);
        assertTrue(layout.logo().bottom() < layout.left().y());
        assertEquals(layout.left().y(), layout.right().y());
        assertEquals(layout.left().bottom(), layout.right().bottom());
        assertTrue(layout.left().right() < layout.changelog().x());
        assertTrue(layout.changelog().width() > layout.left().width());
        assertTrue(layout.discord().y() > layout.changelog().y());
        assertTrue(Math.abs(layout.logo().width() / (double) layout.logo().height()
                - 1024.0D / 269.0D) < 0.04D);
    }

    @Test
    void compactLayoutKeepsControlsInsideScreen() {
        var layout = MoveEarthTitleMenuLayout.calculate(320, 240);

        assertTrue(layout.logo().x() >= 0);
        assertTrue(layout.logo().right() <= 320);
        assertTrue(layout.logo().bottom() < layout.left().y());
        assertTrue(layout.left().x() >= 0);
        assertTrue(layout.discord().right() <= 320);
        assertTrue(layout.discord().bottom() <= 240);
        assertTrue(layout.buttons().getLast().bottom() <= layout.left().bottom());
    }
}
