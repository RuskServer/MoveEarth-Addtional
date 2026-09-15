package com.ruskserver.moveearth_addtional.client.menu;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;

import java.util.ArrayList;
import java.util.List;

final class MoveEarthTitleMenuLayout {
    static final int BUTTON_COUNT = 8;

    private MoveEarthTitleMenuLayout() {
    }

    static Layout calculate(int screenWidth, int screenHeight) {
        boolean spacious = screenWidth >= 640 && screenHeight >= 360;
        int margin = spacious
                ? clamp(Math.min(screenWidth, screenHeight) / 30, 12, 28)
                : clamp(Math.min(screenWidth, screenHeight) / 36, 8, 18);
        int gap = spacious ? clamp(screenWidth / 60, 14, 24) : clamp(screenWidth / 80, 8, 14);
        int sectionGap = spacious
                ? clamp(screenHeight / 30, 14, 24)
                : clamp(screenHeight / 54, 8, 14);

        int minimumPanelHeight = 20 + BUTTON_COUNT * 14 + (BUTTON_COUNT - 1) * 3;
        int maximumLogoHeight = Math.max(24,
                screenHeight - margin * 2 - sectionGap - minimumPanelHeight);
        int desiredLogoWidth = spacious
                ? clamp(Math.round(screenWidth * 0.44F), 240, 420)
                : clamp(Math.round(screenWidth * 0.52F), 220, 460);
        int logoWidth = Math.min(desiredLogoWidth, screenWidth - margin * 2);
        logoWidth = Math.min(logoWidth,
                Math.max(1, (int) Math.floor(maximumLogoHeight * 1024.0D / 269.0D)));
        int logoHeight = Math.max(1, Math.round(logoWidth * 269.0F / 1024.0F));
        MoveEarthUi.Rect logo = new MoveEarthUi.Rect((screenWidth - logoWidth) / 2, margin,
                logoWidth, logoHeight);

        int contentTop = logo.bottom() + sectionGap;
        int contentWidth = Math.min(1000, Math.max(1, screenWidth - margin * 2));
        int contentX = (screenWidth - contentWidth) / 2;
        int contentHeight = Math.max(1, screenHeight - margin - contentTop);
        int minimumRightWidth = Math.min(178, Math.max(96, contentWidth / 2));
        int leftWidth = clamp(Math.round(contentWidth * (spacious ? 0.26F : 0.28F)), 118, 260);
        leftWidth = Math.min(leftWidth, Math.max(90, contentWidth - gap - minimumRightWidth));

        int horizontalInset = spacious ? 16 : 10;
        int verticalInset = spacious ? 16 : 10;
        int buttonGap = spacious ? clamp(screenHeight / 70, 6, 10) : screenHeight < 300 ? 3 : 5;
        int availableButtonHeight = Math.max(BUTTON_COUNT * 14,
                contentHeight - verticalInset * 2 - buttonGap * (BUTTON_COUNT - 1));
        int buttonHeight = clamp(availableButtonHeight / BUTTON_COUNT, 14, spacious ? 26 : 28);
        int buttonStackHeight = BUTTON_COUNT * buttonHeight + (BUTTON_COUNT - 1) * buttonGap;
        int leftHeight = spacious
                ? Math.min(contentHeight, buttonStackHeight + verticalInset * 2)
                : contentHeight;
        int leftY = contentTop + Math.max(0, (contentHeight - leftHeight) / 2);
        MoveEarthUi.Rect left = new MoveEarthUi.Rect(contentX, leftY, leftWidth, leftHeight);
        int rightX = contentX + leftWidth + gap;
        MoveEarthUi.Rect right = new MoveEarthUi.Rect(rightX, contentTop,
                Math.max(1, contentX + contentWidth - rightX), contentHeight);

        int buttonsTop = left.y() + Math.max(verticalInset,
                (left.height() - buttonStackHeight) / 2);
        List<MoveEarthUi.Rect> buttons = new ArrayList<>(BUTTON_COUNT);
        for (int index = 0; index < BUTTON_COUNT; index++) {
            buttons.add(new MoveEarthUi.Rect(left.x() + horizontalInset,
                    buttonsTop + index * (buttonHeight + buttonGap),
                    Math.max(1, left.width() - horizontalInset * 2), buttonHeight));
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
