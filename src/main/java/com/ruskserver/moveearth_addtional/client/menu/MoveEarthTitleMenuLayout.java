package com.ruskserver.moveearth_addtional.client.menu;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;

import java.util.ArrayList;
import java.util.List;

final class MoveEarthTitleMenuLayout {
    static final int BUTTON_COUNT = 8;

    private MoveEarthTitleMenuLayout() {
    }

    static Layout calculate(int screenWidth, int screenHeight) {
        int margin = clamp(Math.min(screenWidth, screenHeight) / 36, 8, 18);
        int gap = clamp(screenWidth / 80, 8, 14);
        int sectionGap = clamp(screenHeight / 54, 8, 14);

        int minimumPanelHeight = 20 + BUTTON_COUNT * 14 + (BUTTON_COUNT - 1) * 3;
        int maximumLogoHeight = Math.max(24,
                screenHeight - margin * 2 - sectionGap - minimumPanelHeight);
        int desiredLogoWidth = clamp(Math.round(screenWidth * 0.52F), 220, 460);
        int logoWidth = Math.min(desiredLogoWidth, screenWidth - margin * 2);
        logoWidth = Math.min(logoWidth,
                Math.max(1, (int) Math.floor(maximumLogoHeight * 1024.0D / 269.0D)));
        int logoHeight = Math.max(1, Math.round(logoWidth * 269.0F / 1024.0F));
        MoveEarthUi.Rect logo = new MoveEarthUi.Rect((screenWidth - logoWidth) / 2, margin,
                logoWidth, logoHeight);

        int contentTop = logo.bottom() + sectionGap;
        int contentWidth = Math.min(1080, Math.max(1, screenWidth - margin * 2));
        int contentX = (screenWidth - contentWidth) / 2;
        int contentHeight = Math.max(1, screenHeight - margin - contentTop);
        int minimumRightWidth = Math.min(178, Math.max(96, contentWidth / 2));
        int leftWidth = clamp(Math.round(contentWidth * 0.28F), 118, 280);
        leftWidth = Math.min(leftWidth, Math.max(90, contentWidth - gap - minimumRightWidth));

        MoveEarthUi.Rect left = new MoveEarthUi.Rect(contentX, contentTop, leftWidth, contentHeight);
        int rightX = left.right() + gap;
        MoveEarthUi.Rect right = new MoveEarthUi.Rect(rightX, contentTop,
                Math.max(1, contentX + contentWidth - rightX), contentHeight);

        int buttonsTop = left.y() + 10;
        int buttonsBottom = left.bottom() - 10;
        int buttonGap = screenHeight < 300 ? 3 : 5;
        int availableHeight = Math.max(BUTTON_COUNT * 14,
                buttonsBottom - buttonsTop - buttonGap * (BUTTON_COUNT - 1));
        int buttonHeight = clamp(availableHeight / BUTTON_COUNT, 14, 28);
        List<MoveEarthUi.Rect> buttons = new ArrayList<>(BUTTON_COUNT);
        for (int index = 0; index < BUTTON_COUNT; index++) {
            buttons.add(new MoveEarthUi.Rect(left.x() + 10,
                    buttonsTop + index * (buttonHeight + buttonGap),
                    Math.max(1, left.width() - 20), buttonHeight));
        }

        int discordHeight = clamp(screenHeight / 18, 24, 32);
        MoveEarthUi.Rect discord = new MoveEarthUi.Rect(right.x(), right.bottom() - discordHeight,
                right.width(), discordHeight);
        MoveEarthUi.Rect changelog = new MoveEarthUi.Rect(right.x(), right.y(), right.width(),
                Math.max(1, discord.y() - gap - right.y()));
        return new Layout(left, right, logo, List.copyOf(buttons), changelog, discord);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    record Layout(MoveEarthUi.Rect left, MoveEarthUi.Rect right, MoveEarthUi.Rect logo,
                  List<MoveEarthUi.Rect> buttons, MoveEarthUi.Rect changelog,
                  MoveEarthUi.Rect discord) {
    }
}
