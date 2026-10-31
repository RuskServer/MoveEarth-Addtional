package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.Rect;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NationGovernanceLayoutTest {
    @Test
    void choicesAndActionsFitAtCommonGuiScales() {
        for (int[] size : new int[][]{{320, 240}, {640, 360}, {1280, 720}, {1920, 1080}}) {
            var layout = NationGovernanceLayout.calculate(size[0], size[1]);
            var p = layout.panel();
            assertTrue(p.x() >= 0 && p.y() >= 0 && p.right() <= size[0] && p.bottom() <= size[1]);
            for (Rect rect : new Rect[]{layout.list(), layout.back(), layout.refresh(), layout.confirm()}) {
                assertTrue(rect.x() >= p.x() && rect.y() >= p.y() && rect.right() <= p.right() && rect.bottom() <= p.bottom());
            }
            assertTrue(layout.list().bottom() < layout.back().y());
            assertTrue(layout.back().right() < layout.refresh().x());
            assertTrue(layout.refresh().right() < layout.confirm().x());
            assertTrue(layout.list().height() >= 46);
        }
    }
    @Test
    void ownerShortcutsShareSeparateHitAndDrawBoundsEvenOnCompactHome() {
        for (Rect content : new Rect[]{new Rect(10, 20, 264, 82), new Rect(10, 20, 500, 250)}) {
            var actions = NationGovernanceLayout.homeActions(content);
            assertTrue(actions[0].right() < actions[1].x());
            assertTrue(actions[0].y() >= content.y());
            assertEquals(content.bottom(), actions[1].bottom());
        }
    }
}
