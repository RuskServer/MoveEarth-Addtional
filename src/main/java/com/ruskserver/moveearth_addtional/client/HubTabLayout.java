package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.Rect;

import java.util.ArrayList;
import java.util.List;

/** Geometry of the tab strip across the top of the hub screens. Pure, so it is unit tested. */
final class HubTabLayout {
    static final int TOP = 2;
    static final int HEIGHT = 18;
    /** First row of the screen below the strip. */
    static final int BOTTOM = TOP + HEIGHT;
    /** Space kept between the strip and a panel below it. */
    static final int PANEL_GAP = 6;
    private static final int BOTTOM_MARGIN = 4;
    private static final int MIN_TAB = 56;
    private static final int PADDING = 20;
    private static final int GAP = 2;

    private HubTabLayout() { }

    /**
     * One rectangle per tab, in order, centred on the screen. Each tab fits its label; when the strip
     * would not fit, every tab shares the width equally.
     */
    static List<Rect> tabs(int screenWidth, int[] labelWidths) {
        int count = labelWidths.length;
        List<Rect> tabs = new ArrayList<>(count);
        if (count == 0) return tabs;
        int[] widths = new int[count];
        int total = GAP * (count - 1);
        for (int i = 0; i < count; i++) {
            widths[i] = Math.max(MIN_TAB, labelWidths[i] + PADDING);
            total += widths[i];
        }
        int available = Math.max(count, screenWidth - 8);
        if (total > available) {
            int each = Math.max(1, (available - GAP * (count - 1)) / count);
            java.util.Arrays.fill(widths, each);
            total = each * count + GAP * (count - 1);
        }
        int x = (screenWidth - total) / 2;
        for (int i = 0; i < count; i++) {
            tabs.add(new Rect(x, TOP, widths[i], HEIGHT));
            x += widths[i] + GAP;
        }
        return tabs;
    }

    /**
     * Where a centred panel starts once the strip takes the top of the screen. A panel that already
     * clears the strip stays where it always was; one that does not moves down only as far as the
     * screen allows, so on a short screen a fixed-size panel may tuck its top border under the strip
     * rather than lose its bottom row. Panels that grow with the screen avoid that by staying within
     * {@link #maxPanelHeight}.
     */
    static int panelTop(int screenHeight, int panelHeight) {
        int centred = (screenHeight - panelHeight) / 2;
        if (centred >= BOTTOM + PANEL_GAP) return centred;
        return Math.max(centred, Math.min(BOTTOM + PANEL_GAP, screenHeight - BOTTOM_MARGIN - panelHeight));
    }

    /** Tallest panel that fits below the strip with its gap, for panels that grow with the screen. */
    static int maxPanelHeight(int screenHeight) {
        return Math.max(1, screenHeight - BOTTOM - PANEL_GAP - BOTTOM_MARGIN);
    }
}
