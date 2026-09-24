package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S2HubLayoutTest {
    @Test
    void compactAndNormalLayoutsRemainInsidePanel() {
        assertLayout(S2HubLayout.calculate(320, 240, true));
        assertLayout(S2HubLayout.calculate(320, 180, true));
        assertLayout(S2HubLayout.calculate(1280, 720, true));
        assertLayout(S2HubLayout.calculate(1280, 720, false));
    }

    private static void assertLayout(S2HubLayout.Layout layout) {
        Rect panel = layout.panel();
        for (Rect child : new Rect[]{layout.sections(), layout.content()}) {
            assertTrue(child.x() >= panel.x() && child.right() <= panel.right());
            assertTrue(child.y() >= panel.y() && child.bottom() <= panel.bottom());
        }
        assertFalse(overlaps(layout.sections(), layout.content()));
        if (layout.subpages().width() > 0) {
            assertFalse(overlaps(layout.subpages(), layout.content()));
        }
    }

    private static boolean overlaps(Rect a, Rect b) {
        return a.x() < b.right() && a.right() > b.x() && a.y() < b.bottom() && a.bottom() > b.y();
    }
}
