package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.Rect;

/** Responsive geometry for the two-level S2 hub navigation. */
final class S2HubLayout {
    private static final int MAX_WIDTH = 720;
    private static final int MAX_HEIGHT = 430;

    private S2HubLayout() { }

    static Layout calculate(int screenWidth, int screenHeight, boolean hasSubpages) {
        int panelWidth = Math.min(Math.max(1, screenWidth - 4),
                Math.min(MAX_WIDTH, Math.max(240, screenWidth - 20)));
        int panelHeight = Math.min(Math.max(1, screenHeight - 4),
                Math.min(MAX_HEIGHT, Math.max(160, screenHeight - 20)));
        Rect panel = new Rect((screenWidth - panelWidth) / 2, (screenHeight - panelHeight) / 2,
                panelWidth, panelHeight);
        Rect sections = new Rect(panel.x() + 12, panel.y() + 48, panel.width() - 24, 27);
        Rect subpages = hasSubpages
                ? new Rect(panel.x() + 18, panel.y() + 80, panel.width() - 36, 23)
                : new Rect(panel.x() + 18, panel.y() + 80, 0, 0);
        int contentY = Math.min(panel.bottom() - 16, hasSubpages ? panel.y() + 112 : panel.y() + 87);
        Rect content = new Rect(panel.x() + 18, contentY, panel.width() - 36,
                Math.max(0, panel.bottom() - 16 - contentY));
        return new Layout(panel, sections, subpages, content);
    }

    static Rect item(Rect row, int index, int count) {
        int safeCount = Math.max(1, count);
        int left = row.x() + row.width() * index / safeCount;
        int right = row.x() + row.width() * (index + 1) / safeCount;
        return new Rect(left, row.y(), Math.max(1, right - left), row.height());
    }

    record Layout(Rect panel, Rect sections, Rect subpages, Rect content) { }
}
