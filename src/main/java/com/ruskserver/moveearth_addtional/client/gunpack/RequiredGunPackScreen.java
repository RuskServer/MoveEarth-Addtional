package com.ruskserver.moveearth_addtional.client.gunpack;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class RequiredGunPackScreen extends Screen {
    private static final int PANEL_WIDTH = 560;
    private static final int PANEL_HEIGHT = 226;
    private static final int TEXT = 0xFFE8EDF3;
    private static final int MUTED = 0xFFAAB4C0;
    private static final int ACCENT = 0xFF63C7FF;
    private static final int SUCCESS = 0xFF68E09B;

    private final TitleScreen parent;
    private List<RequiredGunPack> missing;
    private Component status = Component.translatable("screen.moveearth_addtional.gunpack.drop_hint");
    private boolean restartRequired;

    public RequiredGunPackScreen(TitleScreen parent, List<RequiredGunPack> missing) {
        super(Component.translatable("screen.moveearth_addtional.gunpack.title"));
        this.parent = parent;
        this.missing = List.copyOf(missing);
    }

    @Override
    protected void init() {
        int left = panelLeft();
        int top = panelTop();
        int right = left + panelWidth();
        int rowY = top + 55;

        for (RequiredGunPack pack : missing) {
            addRenderableWidget(Button.builder(
                            Component.translatable("screen.moveearth_addtional.gunpack.download"),
                            button -> Util.getPlatform().openUri(pack.downloadPage()))
                    .bounds(right - 106, rowY, 92, 20)
                    .build());
            rowY += 25;
        }

        int footerY = top + panelHeight() - 31;
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.moveearth_addtional.gunpack.open_folder"),
                        button -> openGunPackFolder())
                .bounds(left + 14, footerY, 150, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.moveearth_addtional.gunpack.recheck"),
                        button -> refreshMissing())
                .bounds(left + 170, footerY, 112, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable(restartRequired
                                        ? "screen.moveearth_addtional.gunpack.close_restart"
                                        : "screen.moveearth_addtional.gunpack.later"),
                        button -> onClose())
                .bounds(right - 126, footerY, 112, 20)
                .build());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height, 0xFF080B10, 0xFF101A26);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = panelLeft();
        int top = panelTop();
        int panelWidth = panelWidth();
        int right = left + panelWidth;
        int bottom = top + panelHeight();

        graphics.fill(left, top, right, bottom, 0xF0161C24);
        drawBorder(graphics, left, top, panelWidth, panelHeight(), 0xFF405166);
        graphics.drawCenteredString(font, title, width / 2, top + 13, TEXT);
        graphics.drawCenteredString(font,
                Component.translatable("screen.moveearth_addtional.gunpack.description"),
                width / 2, top + 29, MUTED);

        int rowY = top + 61;
        if (missing.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.moveearth_addtional.gunpack.complete"),
                    width / 2, rowY + 18, SUCCESS);
        } else {
            for (RequiredGunPack pack : missing) {
                graphics.fill(left + 14, rowY - 6, right - 14, rowY + 17, 0xFF202936);
                graphics.fill(left + 14, rowY - 6, left + 17, rowY + 17, ACCENT);
                String name = font.plainSubstrByWidth(pack.displayName(), panelWidth - 145);
                graphics.drawString(font, name, left + 25, rowY, TEXT, false);
                rowY += 25;
            }
        }

        int dropTop = top + 137;
        graphics.fill(left + 14, dropTop, right - 14, dropTop + 30, 0xFF11171F);
        drawBorder(graphics, left + 14, dropTop, panelWidth - 28, 30, 0xFF536B84);
        graphics.drawCenteredString(font,
                Component.translatable("screen.moveearth_addtional.gunpack.drop_zone"),
                width / 2, dropTop + 11, ACCENT);

        int statusY = top + 174;
        int maxWidth = panelWidth - 28;
        int lineCount = 0;
        for (FormattedCharSequence line : font.split(status, maxWidth)) {
            if (lineCount++ >= 2) {
                break;
            }
            graphics.drawCenteredString(font, line, width / 2, statusY, restartRequired ? SUCCESS : MUTED);
            statusY += 10;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        RequiredGunPackService.InstallResult result = RequiredGunPackService.installDropped(paths);
        if (result.installed() > 0) {
            restartRequired = true;
            status = result.rejected() == 0
                    ? Component.translatable("screen.moveearth_addtional.gunpack.installed", result.installed())
                    : Component.translatable("screen.moveearth_addtional.gunpack.installed_partial",
                    result.installed(), result.rejected());
        } else if (result.alreadyInstalled() > 0 && result.rejected() == 0) {
            status = Component.translatable("screen.moveearth_addtional.gunpack.already_installed");
        } else {
            status = Component.translatable("screen.moveearth_addtional.gunpack.rejected", result.lastError());
        }
        refreshMissing(false);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private void refreshMissing() {
        refreshMissing(true);
    }

    private void refreshMissing(boolean announce) {
        int previousMissingCount = missing.size();
        missing = RequiredGunPackService.findMissing();
        if (missing.size() < previousMissingCount) {
            restartRequired = true;
        }
        if (announce) {
            status = missing.isEmpty()
                    ? Component.translatable("screen.moveearth_addtional.gunpack.complete_restart")
                    : Component.translatable("screen.moveearth_addtional.gunpack.still_missing", missing.size());
        }
        rebuildWidgets();
    }

    private void openGunPackFolder() {
        Path directory = RequiredGunPackService.gunPackDirectory();
        try {
            Files.createDirectories(directory);
            Util.getPlatform().openPath(directory);
        } catch (IOException exception) {
            status = Component.translatable("screen.moveearth_addtional.gunpack.folder_error", exception.getMessage());
        }
    }

    private int panelWidth() {
        return Math.min(PANEL_WIDTH, Math.max(280, width - 24));
    }

    private int panelHeight() {
        return Math.min(PANEL_HEIGHT, Math.max(210, height - 16));
    }

    private int panelLeft() {
        return (width - panelWidth()) / 2;
    }

    private int panelTop() {
        return (height - panelHeight()) / 2;
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
