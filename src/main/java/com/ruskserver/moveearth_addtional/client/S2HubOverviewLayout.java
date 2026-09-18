package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.Rect;

/** Responsive two-row action layout for the nation overview footer. */
final class S2HubOverviewLayout {
    private static final int BUTTON_HEIGHT = 22;

    private S2HubOverviewLayout() { }

    static Actions calculate(Rect content) {
        int gap = content.width() >= 400 ? 8 : 4;
        int bottomY = content.bottom() - BUTTON_HEIGHT - 1;
        int topY = bottomY - BUTTON_HEIGHT - gap;

        int topWidth = Math.max(1, (content.width() - gap) / 2);
        Rect treasury = new Rect(content.x(), topY, topWidth, BUTTON_HEIGHT);
        Rect preview = new Rect(treasury.right() + gap, topY,
                Math.max(1, content.right() - treasury.right() - gap), BUTTON_HEIGHT);

        int firstWidth = Math.max(1, (content.width() - gap * 2) / 3);
        int secondWidth = Math.max(1, (content.width() - gap * 2 - firstWidth) / 2);
        Rect membership = new Rect(content.x(), bottomY, firstWidth, BUTTON_HEIGHT);
        Rect recovery = new Rect(membership.right() + gap, bottomY, secondWidth, BUTTON_HEIGHT);
        Rect vault = new Rect(recovery.right() + gap, bottomY,
                Math.max(1, content.right() - recovery.right() - gap), BUTTON_HEIGHT);
        return new Actions(membership, recovery, vault, treasury, preview);
    }

    record Actions(Rect membership, Rect recovery, Rect vault, Rect treasury, Rect preview) { }
}
