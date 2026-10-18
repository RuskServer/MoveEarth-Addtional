package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.Rect;

/** Responsive geometry for the recovery and dispatch screen. Pure, so it is unit tested. */
final class RecoveryScreenLayout {
    static final int MAX_WIDTH = 720;
    static final int MAX_HEIGHT = 430;
    /** The smallest panel the screen is laid out for; smaller GUI scales are used below it. */
    static final int FIT_WIDTH = 560;
    static final int FIT_HEIGHT = 320;
    private static final int TAB_WIDTH = 150;
    private static final int TAB_GAP = 6;

    private RecoveryScreenLayout() { }

    static Layout calculate(int screenWidth, int screenHeight) {
        int panelWidth = Math.min(Math.max(1, screenWidth - 4),
                Math.min(MAX_WIDTH, Math.max(240, screenWidth - 20)));
        int room = HubTabLayout.maxPanelHeight(screenHeight);
        int panelHeight = Math.min(room, Math.min(MAX_HEIGHT, Math.max(160, room)));
        Rect panel = new Rect((screenWidth - panelWidth) / 2, HubTabLayout.panelTop(screenHeight, panelHeight),
                panelWidth, panelHeight);
        Rect close = new Rect(panel.right() - 30, panel.y() + 8, 20, 20);
        Rect tabs = new Rect(panel.x() + 18, panel.y() + 44,
                Math.max(1, Math.min(panel.width() - 36, 3 * TAB_WIDTH + 2 * TAB_GAP)), 24);
        int contentY = Math.min(panel.bottom() - 14, panel.y() + 78);
        Rect content = new Rect(panel.x() + 18, contentY, Math.max(1, panel.width() - 36),
                Math.max(0, panel.bottom() - 14 - contentY));
        return new Layout(panel, close, tabs, content);
    }

    static Rect tab(Rect tabs, int index, int count) {
        int safeCount = Math.max(1, count);
        int width = Math.max(1, (tabs.width() - TAB_GAP * (safeCount - 1)) / safeCount);
        return new Rect(tabs.x() + index * (width + TAB_GAP), tabs.y(), width, tabs.height());
    }

    /** How many of {@code count} blocks fit side by side when each needs {@code minimumWidth}. */
    static int columns(int width, int count, int minimumWidth, int gap) {
        int safeCount = Math.max(1, count);
        for (int columns = safeCount; columns > 1; columns--) {
            if ((width - gap * (columns - 1)) / columns >= minimumWidth) return columns;
        }
        return 1;
    }

    /** Cell {@code index} of a grid of {@code columns} cells per row, each {@code cellHeight} tall. */
    static Rect cell(int x, int y, int width, int index, int columns, int cellHeight, int gap) {
        int safeColumns = Math.max(1, columns);
        int cellWidth = Math.max(1, (width - gap * (safeColumns - 1)) / safeColumns);
        int column = index % safeColumns;
        int row = index / safeColumns;
        return new Rect(x + column * (cellWidth + gap), y + row * (cellHeight + gap), cellWidth, cellHeight);
    }

    static int gridHeight(int count, int columns, int cellHeight, int gap) {
        int rows = (Math.max(0, count) + Math.max(1, columns) - 1) / Math.max(1, columns);
        return rows == 0 ? 0 : rows * cellHeight + (rows - 1) * gap;
    }

    /** A modal of at most the wanted size, centred on the panel and inside it. */
    static Rect modal(Rect panel, int wantedWidth, int wantedHeight) {
        int width = Math.max(1, Math.min(wantedWidth, panel.width() - 24));
        int height = Math.max(1, Math.min(wantedHeight, panel.height() - 16));
        return new Rect(panel.x() + (panel.width() - width) / 2, panel.y() + (panel.height() - height) / 2,
                width, height);
    }

    /** Whole open-time minutes, rounded up so a nearly finished wait never reads as none. */
    static long openMinutes(long ticks) {
        return ticks <= 0L ? 0L : (ticks + 1_199L) / 1_200L;
    }

    record Layout(Rect panel, Rect close, Rect tabs, Rect content) { }
}
