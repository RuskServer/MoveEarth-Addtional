package com.ruskserver.moveearth_addtional.client.menu;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.ModListScreen;
import org.lwjgl.glfw.GLFW;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

public final class MoveEarthTitleScreen extends Screen {
    private static final ResourceLocation LOGO = ResourceLocation.fromNamespaceAndPath(
            Moveearth_addtional.MODID, "menu/minecraft_title.png");
    private static final ResourceLocation CHANGELOG = ResourceLocation.fromNamespaceAndPath(
            Moveearth_addtional.MODID, "menu/changelog.md");
    private static final String SERVER_ADDRESS = "devbase.ruskserver.com:25565";
    private static final URI DISCORD_INVITE = URI.create("https://discord.gg/QNquTTTdZh");
    private static final int DISCORD = 0xFF5865F2;
    private static final int MENU_PANEL = 0xA812161D;
    private static final int MENU_PANEL_BORDER = 0xD0354150;

    private final StaticMeteorBackground animatedBackground = new StaticMeteorBackground();
    private final List<String> changelogSource = new ArrayList<>();
    private List<DisplayLine> changelogLines = List.of();
    private int wrappedForWidth = -1;
    private int changelogContentHeight;
    private int changelogScroll;
    private int selectedButton;
    private boolean draggingScrollbar;

    public MoveEarthTitleScreen() {
        super(Component.translatable("screen.moveearth_addtional.main_menu.title"));
    }

    @Override
    protected void init() {
        if (changelogSource.isEmpty()) loadChangelog();
        wrappedForWidth = -1;
        clampScroll(layout());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        animatedBackground.render(graphics, width, height);
        graphics.fillGradient(0, 0, width, height, 0x35000000, 0xA0000000);

        MoveEarthTitleMenuLayout.Layout layout = layout();
        drawMenuPanel(graphics, layout.left());
        renderLogo(graphics, layout.logo());
        renderMenuButtons(graphics, layout, mouseX, mouseY);
        renderChangelog(graphics, layout, mouseX, mouseY);

        boolean discordHovered = layout.discord().contains(mouseX, mouseY);
        MoveEarthUi.drawButton(graphics, font, layout.discord(),
                Component.translatable("screen.moveearth_addtional.main_menu.discord"),
                DISCORD, discordHovered, true);
    }

    private void renderLogo(GuiGraphics graphics, MoveEarthUi.Rect bounds) {
        graphics.blit(LOGO, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                0.0F, 0.0F, 1024, 269, 1024, 269);
    }

    private static void drawMenuPanel(GuiGraphics graphics, MoveEarthUi.Rect bounds) {
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), MENU_PANEL);
        MoveEarthUi.drawBorder(graphics, bounds, MENU_PANEL_BORDER);
    }

    private void renderMenuButtons(GuiGraphics graphics, MoveEarthTitleMenuLayout.Layout layout,
                                   int mouseX, int mouseY) {
        List<MenuAction> actions = List.of(MenuAction.values());
        for (int index = 0; index < actions.size(); index++) {
            MoveEarthUi.Rect bounds = layout.buttons().get(index);
            boolean hovered = bounds.contains(mouseX, mouseY);
            if (hovered) selectedButton = index;
            int accent = index == 0 ? SUCCESS : ACCENT;
            MoveEarthUi.drawButton(graphics, font, bounds,
                    Component.translatable(actions.get(index).translationKey), accent,
                    hovered || selectedButton == index, true);
        }
    }

    private void renderChangelog(GuiGraphics graphics, MoveEarthTitleMenuLayout.Layout layout,
                                 int mouseX, int mouseY) {
        MoveEarthUi.Rect panel = layout.changelog();
        int padding = changelogPadding(panel);
        drawMenuPanel(graphics, panel);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.main_menu.changelog"),
                panel.x() + padding, panel.y() + 14, SUCCESS, false);
        String version = ModList.get().getModContainerById(Moveearth_addtional.MODID)
                .map(container -> "v" + container.getModInfo().getVersion())
                .orElse("v3.1");
        graphics.drawString(font, version, panel.right() - font.width(version) - padding,
                panel.y() + 14, MUTED, false);

        MoveEarthUi.Rect viewport = changelogViewport(panel);
        ensureWrapped(viewport.width() - 8);
        clampScroll(layout);
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        int y = viewport.y() - changelogScroll;
        for (DisplayLine line : changelogLines) {
            if (y + line.height > viewport.y() && y < viewport.bottom()) {
                if (line.text != null) graphics.drawString(font, line.text, viewport.x(), y, line.color, false);
            }
            y += line.height;
        }
        graphics.disableScissor();

        MoveEarthUi.Rect track = scrollbarTrack(panel);
        MoveEarthUi.drawScrollbar(graphics, track, viewport.height(), changelogContentHeight, changelogScroll);
        if (viewport.contains(mouseX, mouseY) && changelogContentHeight > viewport.height()) {
            graphics.drawString(font, "↕", track.x() - 9, panel.y() + 14, MUTED, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mouseX, mouseY, button);
        MoveEarthTitleMenuLayout.Layout layout = layout();
        for (int index = 0; index < layout.buttons().size(); index++) {
            if (layout.buttons().get(index).contains(mouseX, mouseY)) {
                selectedButton = index;
                activate(MenuAction.values()[index]);
                return true;
            }
        }
        if (layout.discord().contains(mouseX, mouseY)) {
            playClick();
            ConfirmLinkScreen.confirmLinkNow(this, DISCORD_INVITE, true);
            return true;
        }
        MoveEarthUi.Rect track = scrollbarTrack(layout.changelog());
        if (track.contains(mouseX, mouseY) && changelogContentHeight > changelogViewport(layout.changelog()).height()) {
            draggingScrollbar = true;
            scrollFromMouse(layout, mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingScrollbar) {
            scrollFromMouse(layout(), mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        MoveEarthTitleMenuLayout.Layout layout = layout();
        if (!layout.changelog().contains(mouseX, mouseY)) return false;
        MoveEarthUi.Rect viewport = changelogViewport(layout.changelog());
        changelogScroll = MoveEarthUi.scroll(changelogScroll, scrollY, 24,
                changelogContentHeight, viewport.height());
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_UP) {
            selectedButton = Math.floorMod(selectedButton - 1, MenuAction.values().length);
            playClick();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_TAB) {
            selectedButton = Math.floorMod(selectedButton + 1, MenuAction.values().length);
            playClick();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_SPACE) {
            activate(MenuAction.values()[selectedButton]);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            MoveEarthTitleMenuLayout.Layout layout = layout();
            int direction = keyCode == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1;
            MoveEarthUi.Rect viewport = changelogViewport(layout.changelog());
            changelogScroll = Math.max(0, Math.min(maxScroll(viewport),
                    changelogScroll + direction * Math.max(24, viewport.height() - 24)));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void activate(MenuAction action) {
        playClick();
        switch (action) {
            case MOVE_EARTH -> connectToMoveEarth();
            case SINGLEPLAYER -> minecraft.setScreen(new SelectWorldScreen(this));
            case MULTIPLAYER -> minecraft.setScreen(new JoinMultiplayerScreen(this));
            case MODS -> minecraft.setScreen(new ModListScreen(this));
            case OPTIONS -> minecraft.setScreen(new OptionsScreen(this, minecraft.options));
            case LANGUAGE -> minecraft.setScreen(new LanguageSelectScreen(
                    this, minecraft.options, minecraft.getLanguageManager()));
            case ACCESSIBILITY -> minecraft.setScreen(new AccessibilityOptionsScreen(this, minecraft.options));
            case QUIT -> minecraft.stop();
        }
    }

    private void connectToMoveEarth() {
        ServerData data = new ServerData("MoveEarth", SERVER_ADDRESS, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(SERVER_ADDRESS),
                data, false, null);
    }

    private void loadChangelog() {
        try (BufferedReader reader = minecraft.getResourceManager().openAsReader(CHANGELOG)) {
            changelogSource.addAll(MenuChangelog.currentRelease(reader.lines().toList()));
        } catch (IOException exception) {
            changelogSource.add("# v3.1");
            changelogSource.add("");
            changelogSource.add(Component.translatable(
                    "screen.moveearth_addtional.main_menu.changelog_unavailable").getString());
            Moveearth_addtional.LOGGER.warn("[MoveEarth] Could not load bundled menu changelog", exception);
        }
    }

    private void ensureWrapped(int width) {
        int safeWidth = Math.max(32, width);
        if (wrappedForWidth == safeWidth) return;
        wrappedForWidth = safeWidth;
        List<DisplayLine> wrapped = new ArrayList<>();
        for (String raw : changelogSource) {
            if (raw.isBlank()) {
                wrapped.add(new DisplayLine(null, MUTED, 5));
                continue;
            }
            int color = raw.startsWith("# ") ? SUCCESS : raw.startsWith("## ") ? GOLD
                    : raw.startsWith("- ") ? TEXT : MUTED;
            int lineHeight = raw.startsWith("# ") || raw.startsWith("## ") ? 13 : 10;
            Component text = Component.literal(MenuChangelog.displayText(raw));
            for (FormattedCharSequence sequence : font.split(text, safeWidth)) {
                wrapped.add(new DisplayLine(sequence, color, lineHeight));
                lineHeight = 10;
            }
        }
        changelogLines = List.copyOf(wrapped);
        changelogContentHeight = wrapped.stream().mapToInt(DisplayLine::height).sum();
    }

    private void scrollFromMouse(MoveEarthTitleMenuLayout.Layout layout, double mouseY) {
        MoveEarthUi.Rect viewport = changelogViewport(layout.changelog());
        MoveEarthUi.Rect track = scrollbarTrack(layout.changelog());
        int maximum = maxScroll(viewport);
        double ratio = (mouseY - track.y()) / Math.max(1.0D, track.height());
        changelogScroll = (int) Math.round(Math.max(0.0D, Math.min(1.0D, ratio)) * maximum);
    }

    private void clampScroll(MoveEarthTitleMenuLayout.Layout layout) {
        MoveEarthUi.Rect viewport = changelogViewport(layout.changelog());
        changelogScroll = Math.max(0, Math.min(maxScroll(viewport), changelogScroll));
    }

    private int maxScroll(MoveEarthUi.Rect viewport) {
        return Math.max(0, changelogContentHeight - viewport.height());
    }

    private static MoveEarthUi.Rect changelogViewport(MoveEarthUi.Rect panel) {
        int padding = changelogPadding(panel);
        return new MoveEarthUi.Rect(panel.x() + padding, panel.y() + 38,
                Math.max(1, panel.width() - padding * 2 - 4),
                Math.max(1, panel.height() - 38 - padding));
    }

    private static MoveEarthUi.Rect scrollbarTrack(MoveEarthUi.Rect panel) {
        int padding = changelogPadding(panel);
        return new MoveEarthUi.Rect(panel.right() - padding + 3, panel.y() + 38, 3,
                Math.max(1, panel.height() - 38 - padding));
    }

    private static int changelogPadding(MoveEarthUi.Rect panel) {
        return Math.max(12, Math.min(18, panel.width() / 36));
    }

    private void playClick() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private MoveEarthTitleMenuLayout.Layout layout() {
        return MoveEarthTitleMenuLayout.calculate(width, height);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        draggingScrollbar = false;
    }

    private enum MenuAction {
        MOVE_EARTH("screen.moveearth_addtional.main_menu.moveearth"),
        SINGLEPLAYER("menu.singleplayer"),
        MULTIPLAYER("menu.multiplayer"),
        MODS("fml.menu.mods"),
        OPTIONS("menu.options"),
        LANGUAGE("options.language"),
        ACCESSIBILITY("options.accessibility"),
        QUIT("menu.quit");

        private final String translationKey;

        MenuAction(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private record DisplayLine(FormattedCharSequence text, int color, int height) {
    }
}
