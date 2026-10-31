package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.Rect;

final class NationGovernanceLayout {
    private NationGovernanceLayout() { }
    static Layout calculate(int width, int height) {
        int w = Math.max(1, Math.min(580, width - 16)), h = Math.max(1, Math.min(380, height - 16));
        Rect panel = new Rect((width - w) / 2, (height - h) / 2, w, h);
        boolean compact = h < 220;
        int footer = panel.bottom() - 34;
        int top = panel.y() + (compact ? 72 : 104);
        Rect list = new Rect(panel.x() + 12, Math.min(top, footer), Math.max(0, w - 24), Math.max(0, footer - 8 - top));
        int bw = Math.max(1, (w - 40) / 3);
        return new Layout(panel, list, new Rect(panel.x() + 12, footer, bw, 22),
                new Rect(panel.x() + 20 + bw, footer, bw, 22),
                new Rect(panel.right() - 12 - bw, footer, bw, 22), compact);
    }
    static Rect[] homeActions(Rect content) {
        int w = Math.max(1, (content.width() - 8) / 2);
        return new Rect[]{new Rect(content.x(), content.bottom() - 22, w, 22),
                new Rect(content.right() - w, content.bottom() - 22, w, 22)};
    }
    record Layout(Rect panel, Rect list, Rect back, Rect refresh, Rect confirm, boolean compact) { }
}
