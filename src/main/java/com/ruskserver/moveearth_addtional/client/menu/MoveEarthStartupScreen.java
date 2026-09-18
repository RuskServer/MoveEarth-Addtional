package com.ruskserver.moveearth_addtional.client.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import com.ruskserver.moveearth_addtional.config.StartupClientConfig;
import net.minecraft.Util;
import net.minecraft.client.NarratorStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

/** Silent warning, MoveEarth ident, and first-run accessibility/audio setup. */
public final class MoveEarthStartupScreen extends Screen {
    private static final ResourceLocation LOGO = ResourceLocation.fromNamespaceAndPath(
            Moveearth_addtional.MODID, "menu/minecraft_title.png");
    private static final NarratorStatus[] NARRATOR_STATES = {
            NarratorStatus.OFF, NarratorStatus.SYSTEM, NarratorStatus.CHAT, NarratorStatus.ALL
    };
    private static final int CONTROL_COUNT = 8;

    private final StaticMeteorBackground background = new StaticMeteorBackground();
    private final Screen parent;
    private final boolean openSetupDirectly;
    private Phase phase = Phase.WARNING;
    private long phaseStartedAt = Util.getMillis();
    private int selectedControl;
    private int draggingSlider = -1;
    private boolean reducedMotion;

    public MoveEarthStartupScreen() {
        this(null, false);
    }

    private MoveEarthStartupScreen(Screen parent, boolean openSetupDirectly) {
        super(Component.translatable("screen.moveearth_addtional.startup.title"));
        this.parent = parent;
        this.openSetupDirectly = openSetupDirectly;
        if (openSetupDirectly) phase = Phase.SETUP;
        reducedMotion = StartupClientConfig.reducedMotion();
    }

    public static MoveEarthStartupScreen settings(Screen parent) {
        return new MoveEarthStartupScreen(parent, true);
    }

    @Override
    protected void init() {
        if (openSetupDirectly) phase = Phase.SETUP;
        phaseStartedAt = Util.getMillis();
        minecraft.getMusicManager().stopPlaying();
    }

    @Override
    public void tick() {
        minecraft.getMusicManager().stopPlaying();
        if (phase == Phase.LOGO && StartupFlowPolicy.logoFinished(elapsed())) advanceFromLogo();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean animateBackground = phase != Phase.WARNING && !reducedMotion;
        // Reduced motion keeps the view centred; the same switch already governs
        // the meteors, and a background that slides about is the same objection.
        if (reducedMotion) {
            background.render(graphics, width, height, animateBackground);
        } else {
            background.render(graphics, width, height, animateBackground, mouseX, mouseY);
        }
        graphics.fillGradient(0, 0, width, height, 0x72000000, 0xC0000000);
        switch (phase) {
            case WARNING -> renderWarning(graphics);
            case LOGO -> renderLogo(graphics);
            case SETUP -> renderSetup(graphics, mouseX, mouseY);
        }
    }

    private void renderWarning(GuiGraphics graphics) {
        int panelWidth = Math.max(1, Math.min(500, width - 24));
        int panelHeight = Math.max(1, Math.min(224, height - 24));
        Rect panel = new Rect((width - panelWidth) / 2, (height - panelHeight) / 2,
                panelWidth, panelHeight);
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), 0xDC10151C);
        drawBorder(graphics, panel, GOLD);
        graphics.drawCenteredString(font, Component.translatable(
                "screen.moveearth_addtional.startup.warning.title"), width / 2, panel.y() + 22, GOLD);

        int textWidth = panel.width() - 44;
        int y = panel.y() + 52;
        for (Component paragraph : List.of(
                Component.translatable("screen.moveearth_addtional.startup.warning.visual"),
                Component.translatable("screen.moveearth_addtional.startup.warning.audio"),
                Component.translatable("screen.moveearth_addtional.startup.warning.advice"))) {
            for (var line : font.split(paragraph, textWidth)) {
                graphics.drawCenteredString(font, line, width / 2, y, TEXT);
                y += 11;
            }
            y += 7;
        }

        boolean ready = StartupFlowPolicy.warningCanContinue(elapsed());
        graphics.drawCenteredString(font, Component.translatable(ready
                        ? "screen.moveearth_addtional.startup.continue"
                        : "screen.moveearth_addtional.startup.wait"),
                width / 2, panel.bottom() - 25, ready ? SUCCESS : MUTED);
    }

    private void renderLogo(GuiGraphics graphics) {
        long elapsed = elapsed();
        float appear = StartupFlowPolicy.easedProgress(elapsed, 650L);
        float disappear = elapsed > 1_400L
                ? 1.0F - Math.min(1.0F, (elapsed - 1_400L) / 400.0F) : 1.0F;
        float alpha = Math.max(0.0F, Math.min(1.0F, appear * disappear));
        int logoWidth = Math.min(width - 40, Math.max(220, Math.min(620, Math.round(width * 0.58F))));
        int logoHeight = Math.max(1, Math.round(logoWidth * 269.0F / 1024.0F));
        int yOffset = reducedMotion ? 0 : Math.round((1.0F - appear) * 12.0F);
        int x = (width - logoWidth) / 2;
        int y = (height - logoHeight) / 2 + yOffset;
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(LOGO, x, y, logoWidth, logoHeight, 0.0F, 0.0F, 1024, 269, 1024, 269);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.drawCenteredString(font, Component.translatable(
                "screen.moveearth_addtional.startup.skip"), width / 2,
                Math.min(height - 20, y + logoHeight + 30), MUTED);
    }

    private void renderSetup(GuiGraphics graphics, int mouseX, int mouseY) {
        Rect panel = setupPanel();
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), 0xE410151C);
        drawBorder(graphics, panel, SUCCESS);
        graphics.drawCenteredString(font, Component.translatable(
                "screen.moveearth_addtional.startup.setup.title"), width / 2, panel.y() + 14, SUCCESS);
        graphics.drawCenteredString(font, Component.translatable(
                "screen.moveearth_addtional.startup.setup.description"), width / 2, panel.y() + 29, MUTED);

        List<Rect> rows = setupRows(panel);
        drawChoice(graphics, rows.get(0), 0, mouseX, mouseY,
                Component.translatable("screen.moveearth_addtional.startup.narrator"),
                minecraft.options.narrator().get().getName(), minecraft.getNarrator().isActive());
        drawChoice(graphics, rows.get(1), 1, mouseX, mouseY,
                Component.translatable("screen.moveearth_addtional.startup.subtitles"),
                Component.translatable(minecraft.options.showSubtitles().get()
                        ? "options.on" : "options.off"), true);
        drawSlider(graphics, rows.get(2), 2, mouseX, mouseY,
                Component.translatable("screen.moveearth_addtional.startup.master_volume"), SoundSource.MASTER);
        drawSlider(graphics, rows.get(3), 3, mouseX, mouseY,
                Component.translatable("screen.moveearth_addtional.startup.music_volume"), SoundSource.MUSIC);
        drawSlider(graphics, rows.get(4), 4, mouseX, mouseY,
                Component.translatable("screen.moveearth_addtional.startup.effects_volume"), SoundSource.BLOCKS);
        drawChoice(graphics, rows.get(5), 5, mouseX, mouseY,
                Component.translatable("screen.moveearth_addtional.startup.reduced_motion"),
                Component.translatable(reducedMotion ? "options.on" : "options.off"), true);
        drawButton(graphics, font, rows.get(6), Component.translatable(
                        "screen.moveearth_addtional.startup.advanced_accessibility"), ACCENT,
                selectedControl == 6 || rows.get(6).contains(mouseX, mouseY), true);
        drawButton(graphics, font, rows.get(7), Component.translatable(
                        "screen.moveearth_addtional.startup.finish"), SUCCESS,
                selectedControl == 7 || rows.get(7).contains(mouseX, mouseY), true);
    }

    private void drawChoice(GuiGraphics graphics, Rect row, int index, int mouseX, int mouseY,
                            Component label, Component value, boolean enabled) {
        boolean focused = selectedControl == index || row.contains(mouseX, mouseY);
        drawCard(graphics, row, SUCCESS, selectedControl == index, focused);
        graphics.drawString(font, label, row.x() + 10, row.y() + 8, enabled ? TEXT : MUTED, false);
        graphics.drawString(font, enabled ? value : Component.translatable("options.narrator.notavailable"),
                row.right() - 10 - font.width(enabled ? value : Component.translatable("options.narrator.notavailable")),
                row.y() + 8, enabled ? SUCCESS : MUTED, false);
    }

    private void drawSlider(GuiGraphics graphics, Rect row, int index, int mouseX, int mouseY,
                            Component label, SoundSource source) {
        boolean focused = selectedControl == index || row.contains(mouseX, mouseY);
        drawCard(graphics, row, SUCCESS, selectedControl == index, focused);
        double value = minecraft.options.getSoundSourceOptionInstance(source).get();
        String percentage = Math.round(value * 100.0D) + "%";
        graphics.drawString(font, label, row.x() + 10, row.y() + 5, TEXT, false);
        graphics.drawString(font, percentage, row.right() - 10 - font.width(percentage), row.y() + 5, SUCCESS, false);
        Rect track = sliderTrack(row);
        graphics.fill(track.x(), track.y(), track.right(), track.bottom(), BAR_BACKGROUND);
        graphics.fill(track.x(), track.y(), track.x() + Math.round(track.width() * (float) value),
                track.bottom(), SUCCESS);
        drawBorder(graphics, track, focused ? SUCCESS : BORDER);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (phase == Phase.WARNING) {
            if (StartupFlowPolicy.warningCanContinue(elapsed())) beginPhase(Phase.LOGO);
            return true;
        }
        if (phase == Phase.LOGO) {
            if (StartupFlowPolicy.logoCanSkip(elapsed())) advanceFromLogo();
            return true;
        }

        List<Rect> rows = setupRows(setupPanel());
        for (int index = 0; index < rows.size(); index++) {
            if (!rows.get(index).contains(mouseX, mouseY)) continue;
            selectedControl = index;
            if (index >= 2 && index <= 4) {
                draggingSlider = index;
                updateSlider(index, mouseX, rows.get(index));
            } else {
                activateSetupControl(index, 1);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (phase == Phase.SETUP && button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && draggingSlider >= 2 && draggingSlider <= 4) {
            updateSlider(draggingSlider, mouseX, setupRows(setupPanel()).get(draggingSlider));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingSlider >= 0) {
            previewVolume(draggingSlider);
            draggingSlider = -1;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (phase == Phase.WARNING) {
            if (isContinueKey(keyCode) && StartupFlowPolicy.warningCanContinue(elapsed())) beginPhase(Phase.LOGO);
            return true;
        }
        if (phase == Phase.LOGO) {
            if (isContinueKey(keyCode) && StartupFlowPolicy.logoCanSkip(elapsed())) advanceFromLogo();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            selectedControl = Math.floorMod(selectedControl - 1, CONTROL_COUNT);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_TAB) {
            selectedControl = Math.floorMod(selectedControl + 1, CONTROL_COUNT);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) {
            activateSetupControl(selectedControl, keyCode == GLFW.GLFW_KEY_RIGHT ? 1 : -1);
            return true;
        }
        if (isContinueKey(keyCode)) {
            activateSetupControl(selectedControl, 1);
            return true;
        }
        return false;
    }

    private void activateSetupControl(int index, int direction) {
        switch (index) {
            case 0 -> cycleNarrator(direction);
            case 1 -> minecraft.options.showSubtitles().set(!minecraft.options.showSubtitles().get());
            case 2, 3, 4 -> adjustVolume(index, direction * 0.05D);
            case 5 -> reducedMotion = !reducedMotion;
            case 6 -> minecraft.setScreen(new AccessibilityOptionsScreen(this, minecraft.options));
            case 7 -> finishSetup();
            default -> { }
        }
        if (index != 7) playClick();
    }

    private void cycleNarrator(int direction) {
        if (!minecraft.getNarrator().isActive()) return;
        NarratorStatus current = minecraft.options.narrator().get();
        int index = 0;
        for (int candidate = 0; candidate < NARRATOR_STATES.length; candidate++) {
            if (NARRATOR_STATES[candidate] == current) index = candidate;
        }
        minecraft.options.narrator().set(NARRATOR_STATES[Math.floorMod(index + direction,
                NARRATOR_STATES.length)]);
    }

    private void adjustVolume(int index, double delta) {
        SoundSource source = sliderSource(index);
        var option = minecraft.options.getSoundSourceOptionInstance(source);
        option.set(Math.max(0.0D, Math.min(1.0D, option.get() + delta)));
        previewVolume(index);
    }

    private void updateSlider(int index, double mouseX, Rect row) {
        Rect track = sliderTrack(row);
        double value = Math.max(0.0D, Math.min(1.0D, (mouseX - track.x()) / Math.max(1.0D, track.width())));
        minecraft.options.getSoundSourceOptionInstance(sliderSource(index)).set(value);
    }

    private static SoundSource sliderSource(int index) {
        return switch (index) {
            case 2 -> SoundSource.MASTER;
            case 3 -> SoundSource.MUSIC;
            default -> SoundSource.BLOCKS;
        };
    }

    private void previewVolume(int index) {
        if (index == 4) {
            minecraft.getSoundManager().play(new SimpleSoundInstance(
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS,
                    0.35F, 1.15F, RandomSource.create(), BlockPos.ZERO));
        } else if (index == 2) {
            playClick();
        }
    }

    private void finishSetup() {
        minecraft.options.save();
        StartupClientConfig.completeSetup(reducedMotion);
        playClick();
        minecraft.setScreen(parent == null ? new MoveEarthTitleScreen() : parent);
    }

    private void advanceFromLogo() {
        if (StartupClientConfig.setupCompleted()) {
            minecraft.setScreen(new MoveEarthTitleScreen());
        } else {
            beginPhase(Phase.SETUP);
        }
    }

    private void beginPhase(Phase next) {
        phase = next;
        phaseStartedAt = Util.getMillis();
    }

    private long elapsed() {
        return Math.max(0L, Util.getMillis() - phaseStartedAt);
    }

    private Rect setupPanel() {
        int panelWidth = Math.max(1, Math.min(520, width - 16));
        int panelHeight = Math.max(1, Math.min(365, height - 16));
        return new Rect((width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight);
    }

    private static List<Rect> setupRows(Rect panel) {
        int gap = panel.height() < 290 ? 3 : 5;
        int top = panel.y() + 48;
        int bottomPadding = 12;
        int available = panel.bottom() - bottomPadding - top - gap * (CONTROL_COUNT - 1);
        int rowHeight = Math.max(17, available / CONTROL_COUNT);
        java.util.ArrayList<Rect> rows = new java.util.ArrayList<>(CONTROL_COUNT);
        for (int index = 0; index < CONTROL_COUNT; index++) {
            rows.add(new Rect(panel.x() + 16, top + index * (rowHeight + gap),
                    panel.width() - 32, rowHeight));
        }
        return List.copyOf(rows);
    }

    private static Rect sliderTrack(Rect row) {
        return new Rect(row.x() + 10, row.bottom() - 8, Math.max(1, row.width() - 20), 4);
    }

    private static boolean isContinueKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_SPACE;
    }

    private void playClick() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Phase { WARNING, LOGO, SETUP }
}
