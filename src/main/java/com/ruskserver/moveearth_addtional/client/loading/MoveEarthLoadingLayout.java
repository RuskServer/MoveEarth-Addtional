package com.ruskserver.moveearth_addtional.client.loading;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;

/** Resolution-independent geometry for the shared loading presentation. */
public final class MoveEarthLoadingLayout {
    private static final double LOGO_ASPECT = 1024.0D / 269.0D;

    private MoveEarthLoadingLayout() {
    }

    public static Layout calculate(int screenWidth, int screenHeight) {
        int margin = clamp(Math.min(screenWidth, screenHeight) / 32, 8, 16);
        int panelHeight = clamp(screenHeight / 7, 72, 86);
        int panelWidth = Math.max(1, screenWidth - margin * 2);
        MoveEarthUi.Rect panel = new MoveEarthUi.Rect(margin,
                Math.max(0, screenHeight - margin - panelHeight), panelWidth, panelHeight);

        int inset = 12;
        MoveEarthUi.Rect progress = new MoveEarthUi.Rect(panel.x() + inset,
                panel.bottom() - 7, Math.max(1, panel.width() - inset * 2), 3);
        int cancelWidth = clamp(panel.width() / 4, 72, 104);
        MoveEarthUi.Rect cancel = new MoveEarthUi.Rect(panel.right() - inset - cancelWidth,
                panel.y() + 8, cancelWidth, 18);

        int logoWidth = clamp((int) (screenWidth * 0.43D), 190, 460);
        logoWidth = Math.min(logoWidth, Math.max(1, screenWidth - margin * 4));
        int logoHeight = Math.max(1, (int) Math.round(logoWidth / LOGO_ASPECT));
        int logoY = Math.max(margin, (panel.y() - logoHeight) / 2);
        MoveEarthUi.Rect logo = new MoveEarthUi.Rect((screenWidth - logoWidth) / 2,
                logoY, logoWidth, logoHeight);
        return new Layout(panel, progress, cancel, logo);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record Layout(MoveEarthUi.Rect panel, MoveEarthUi.Rect progress,
                         MoveEarthUi.Rect cancel, MoveEarthUi.Rect logo) {
    }
}
