package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.Rect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HubTabLayoutTest {
    private static final int[] LABELS = {24, 60, 72, 36, 24, 48};

    @Test
    void tabsSitInOneCentredRowAtTheTop() {
        List<Rect> tabs = HubTabLayout.tabs(960, LABELS);
        assertEquals(6, tabs.size());
        for (int i = 1; i < tabs.size(); i++) assertTrue(tabs.get(i).x() > tabs.get(i - 1).right() - 1);
        int left = tabs.getFirst().x();
        int right = 960 - tabs.getLast().right();
        assertTrue(Math.abs(left - right) <= 1, "centred");
        for (Rect tab : tabs) assertEquals(HubTabLayout.TOP, tab.y());
    }

    @Test
    void aNarrowScreenSharesTheWidthInsteadOfOverflowing() {
        List<Rect> tabs = HubTabLayout.tabs(300, LABELS);
        assertTrue(tabs.getFirst().x() >= 0);
        assertTrue(tabs.getLast().right() <= 300);
    }

    @Test
    void panelsThatClearTheStripStayWhereTheyWere() {
        assertEquals((540 - 348) / 2, HubTabLayout.panelTop(540, 348));
    }

    @Test
    void aTightPanelMovesBelowTheStripOnlyAsFarAsTheScreenAllows() {
        assertEquals(HubTabLayout.BOTTOM + HubTabLayout.PANEL_GAP, HubTabLayout.panelTop(390, 348));
        int top = HubTabLayout.panelTop(360, 348);
        assertEquals(8, top, "keeps its bottom row on screen");
        assertEquals((300 - 348) / 2, HubTabLayout.panelTop(300, 348), "too tall to fit: unchanged");
    }

    @Test
    void aPanelThatGrowsWithTheScreenAlwaysKeepsItsGapBelowTheStrip() {
        for (int height : new int[] {240, 300, 360, 480, 540}) {
            int panel = Math.min(430, HubTabLayout.maxPanelHeight(height));
            int top = HubTabLayout.panelTop(height, panel);
            assertTrue(top >= HubTabLayout.BOTTOM + HubTabLayout.PANEL_GAP, "gap at height " + height);
            assertTrue(top + panel <= height, "fits at height " + height);
        }
    }
}
