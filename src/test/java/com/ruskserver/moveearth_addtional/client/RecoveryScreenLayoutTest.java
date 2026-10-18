package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryScreenLayoutTest {
    @Test
    void layoutStaysInsideThePanelAtEveryScreenSize() {
        int[][] sizes = {{320, 180}, {427, 240}, {568, 328}, {640, 360}, {960, 540}, {1920, 1080}};
        for (int[] size : sizes) {
            RecoveryScreenLayout.Layout layout = RecoveryScreenLayout.calculate(size[0], size[1]);
            Rect panel = layout.panel();
            assertTrue(panel.x() >= 0 && panel.right() <= size[0], "panel width at " + size[0]);
            assertTrue(panel.bottom() <= size[1], "panel height at " + size[1]);
            assertTrue(panel.y() >= HubTabLayout.BOTTOM, "panel clears the hub tab strip at " + size[1]);
            for (Rect child : new Rect[]{layout.close(), layout.tabs(), layout.content()}) {
                assertTrue(inside(child, panel), "child inside panel at " + size[0] + "x" + size[1]);
            }
            assertFalse(overlaps(layout.tabs(), layout.content()));
        }
    }

    @Test
    void theFitSizeLeavesUsableContent() {
        RecoveryScreenLayout.Layout layout = RecoveryScreenLayout.calculate(
                RecoveryScreenLayout.FIT_WIDTH + 8, RecoveryScreenLayout.FIT_HEIGHT + 12);
        assertTrue(layout.content().height() >= 200, "content height " + layout.content().height());
        assertTrue(layout.content().width() >= 500);
    }

    @Test
    void tabsSplitTheRowWithoutOverlap() {
        Rect tabs = new Rect(10, 10, 462, 24);
        Rect first = RecoveryScreenLayout.tab(tabs, 0, 3);
        Rect last = RecoveryScreenLayout.tab(tabs, 2, 3);
        assertEquals(10, first.x());
        assertTrue(last.right() <= tabs.right());
        assertFalse(overlaps(first, RecoveryScreenLayout.tab(tabs, 1, 3)));
    }

    @Test
    void objectiveColumnsFallBackToOneWhenNarrow() {
        assertEquals(3, RecoveryScreenLayout.columns(600, 3, 180, 6));
        assertEquals(2, RecoveryScreenLayout.columns(400, 3, 180, 6));
        assertEquals(1, RecoveryScreenLayout.columns(200, 3, 180, 6));
        assertEquals(50, RecoveryScreenLayout.gridHeight(3, 3, 50, 6));
        assertEquals(106, RecoveryScreenLayout.gridHeight(3, 2, 50, 6));
        assertEquals(162, RecoveryScreenLayout.gridHeight(3, 1, 50, 6));
        Rect third = RecoveryScreenLayout.cell(0, 0, 400, 2, 2, 50, 6);
        assertEquals(0, third.x());
        assertEquals(56, third.y());
    }

    @Test
    void modalsStayInsideThePanel() {
        Rect panel = new Rect(20, 30, 400, 200);
        Rect modal = RecoveryScreenLayout.modal(panel, 520, 400);
        assertTrue(inside(modal, panel));
        Rect small = RecoveryScreenLayout.modal(panel, 200, 100);
        assertEquals(200, small.width());
        assertEquals(panel.x() + 100, small.x());
    }

    @Test
    void openMinutesRoundUp() {
        assertEquals(0L, RecoveryScreenLayout.openMinutes(0L));
        assertEquals(1L, RecoveryScreenLayout.openMinutes(1L));
        assertEquals(1L, RecoveryScreenLayout.openMinutes(1_200L));
        assertEquals(2L, RecoveryScreenLayout.openMinutes(1_201L));
    }

    private static boolean inside(Rect child, Rect parent) {
        return child.x() >= parent.x() && child.right() <= parent.right()
                && child.y() >= parent.y() && child.bottom() <= parent.bottom();
    }

    private static boolean overlaps(Rect a, Rect b) {
        return a.x() < b.right() && a.right() > b.x() && a.y() < b.bottom() && a.bottom() > b.y();
    }
}
