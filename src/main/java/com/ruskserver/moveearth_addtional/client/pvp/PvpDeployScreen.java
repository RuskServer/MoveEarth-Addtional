package com.ruskserver.moveearth_addtional.client.pvp;

import com.mojang.blaze3d.systems.RenderSystem;
import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import com.ruskserver.moveearth_addtional.network.c2s.pvp.C2S_PvpActionPacket;
import com.ruskserver.moveearth_addtional.network.c2s.pvp.C2S_PvpDeployPacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpAllyPositionsPacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpDeployPacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpHudPacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpZonePacket;
import com.ruskserver.moveearth_addtional.pvp.loadout.PvpLoadoutDefinition;
import com.ruskserver.moveearth_addtional.pvp.deploy.PvpTacticalMap;
import com.ruskserver.moveearth_addtional.pvp.PvpTeam;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

/**
 * The deploy screen, in the manner of a Battlefield spawn screen: the battle area as a hologram map with the
 * team's deploy points, its teammates, the hill and where the player fell, the loadouts down the side, and a
 * deploy button. It opens after the killcam and when a match starts or is joined, and cannot be closed except
 * by deploying or leaving; the server deploys the player anyway when its timer runs out.
 */
public final class PvpDeployScreen extends Screen implements com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay {
    private static final int HOLO = 0xFF2FD8FF;
    private static final int HOLO_DIM = 0x402FD8FF;
    private static final int PANEL = 0xE8060C14;
    private static final int SIDE_WIDTH = 176;
    private static final int HEADER = 26;
    private static final int GRID = 8;
    private static final long REVEAL_MILLIS = 650L;
    private static final double SWEEP_SECONDS = 2.6;
    private static final int LOADOUT_ROW = 30;

    private S2C_PvpDeployPacket state;
    private long chosenPoint;
    private String chosenLoadout;
    private int autoTicks;
    private final long openedAt = Util.getMillis();
    private int loadoutScroll;
    private boolean deployed;
    /** Ticks since deploy was pressed; past a second with the screen still open, the press was lost. */
    private int deployWait;

    public PvpDeployScreen(S2C_PvpDeployPacket state) {
        super(Component.literal("出撃準備"));
        this.state = state;
        this.chosenPoint = state.selected();
        this.chosenLoadout = state.loadoutId();
        this.autoTicks = state.autoTicks();
    }

    /** A refresh from the server: danger, the timer and the points; the player's own choices are kept. */
    public void update(S2C_PvpDeployPacket next) {
        this.state = next;
        this.autoTicks = next.autoTicks();
        if (chosenPoint != S2C_PvpDeployPacket.NO_POINT && point(chosenPoint) == null) {
            chosenPoint = S2C_PvpDeployPacket.NO_POINT;
        }
    }

    @Override
    protected void init() {
        // Below this the map and the loadout column get too cramped to read.
        com.ruskserver.moveearth_addtional.client.ui.ScreenFit.fit(this, 560, 320);
        super.init();
        playUi(SoundEvents.BEACON_ACTIVATE, 1.6F, 0.35F);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void tick() {
        if (autoTicks > 0) autoTicks--;
        if (deployed && ++deployWait > 20) {
            deployed = false;
            deployWait = 0;
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    // --- layout ---

    private Rect mapArea() {
        return new Rect(10, HEADER + 8, width - SIDE_WIDTH - 28, height - HEADER - 18);
    }

    private Rect side() {
        return new Rect(width - SIDE_WIDTH - 10, HEADER + 8, SIDE_WIDTH, height - HEADER - 18);
    }

    private Rect deployButton() {
        Rect side = side();
        return new Rect(side.x() + 8, side.bottom() - 74, side.width() - 16, 30);
    }

    private Rect leaveButton() {
        Rect side = side();
        return new Rect(side.x() + 8, side.bottom() - 22, side.width() - 16, 16);
    }

    private Rect loadoutList() {
        Rect side = side();
        return new Rect(side.x() + 8, side.y() + 22, side.width() - 16, deployButton().y() - side.y() - 62);
    }

    /** The world area the map shows: the tactical map's, or one around the deploy points before it arrives. */
    /** The tactical map, when the one the client holds is this match's; another map's would misplace everything. */
    private PvpTacticalMap tactical() {
        PvpTacticalMap map = PvpTacticalClient.map();
        return map != null && map.mapId().equals(state.mapId()) ? map : null;
    }

    private double[] world() {
        PvpTacticalMap map = tactical();
        if (map != null) {
            return new double[]{map.originX(), map.originZ(), map.width() * map.cellSize(), map.depth() * map.cellSize()};
        }
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (S2C_PvpDeployPacket.Point point : state.points()) {
            minX = Math.min(minX, point.x());
            minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x());
            maxZ = Math.max(maxZ, point.z());
        }
        if (minX > maxX) return new double[]{-64, -64, 128, 128};
        double span = Math.max(maxX - minX, maxZ - minZ) + 64;
        return new double[]{(minX + maxX) / 2 - span / 2, (minZ + maxZ) / 2 - span / 2, span, span};
    }

    /** The map's frame on screen, fitted into the map area with the world's proportions. */
    private Rect mapFrame() {
        Rect area = mapArea();
        double[] world = world();
        double scale = Math.min((area.width() - 24) / world[2], (area.height() - 24) / world[3]);
        int w = (int) (world[2] * scale);
        int h = (int) (world[3] * scale);
        return new Rect(area.x() + (area.width() - w) / 2, area.y() + (area.height() - h) / 2, w, h);
    }

    private float screenX(Rect frame, double[] world, double x) {
        return (float) (frame.x() + (x - world[0]) / world[2] * frame.width());
    }

    private float screenY(Rect frame, double[] world, double z) {
        return (float) (frame.y() + (z - world[1]) / world[3] * frame.height());
    }

    // --- rendering ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        float reveal = Mth.clamp((now - openedAt) / (float) REVEAL_MILLIS, 0.0F, 1.0F);
        graphics.fillGradient(0, 0, width, height, 0xF0020509, 0xF0060E18);
        drawHeader(graphics);

        Rect area = mapArea();
        graphics.fill(area.x(), area.y(), area.right(), area.bottom(), PANEL);
        drawBorder(graphics, area, 0xFF16303D);
        Rect frame = mapFrame();
        double[] world = world();

        // The map is revealed top to bottom as the screen opens.
        int revealBottom = frame.y() + (int) (frame.height() * easeOut(reveal));
        graphics.enableScissor(area.x(), area.y(), area.right(), Math.max(area.y(), revealBottom + 2));
        drawTerrain(graphics, frame);
        drawGrid(graphics, frame);
        drawZone(graphics, frame, world, now);
        drawMarkers(graphics, frame, world, now);
        drawPoints(graphics, frame, world, mouseX, mouseY, now);
        drawScanlines(graphics, frame, now);
        graphics.disableScissor();
        if (reveal < 1.0F) graphics.fill(frame.x(), revealBottom, frame.right(), revealBottom + 2, HOLO);
        drawFrameCorners(graphics, frame);

        drawSide(graphics, mouseX, mouseY);
    }

    private void drawHeader(GuiGraphics graphics) {
        graphics.fill(0, 0, width, HEADER, 0xF0081521);
        graphics.fill(0, HEADER - 1, width, HEADER, HOLO_DIM);
        String title = (state.first() ? "◢ 出撃準備" : "◢ 再出撃") + "  —  " + state.mapName();
        graphics.drawString(font, title, 12, 9, HOLO, false);
        S2C_PvpHudPacket hud = PvpClientState.hud();
        if (hud.active()) {
            String red = "RED " + hud.red();
            String blue = hud.blue() + " BLUE";
            int center = width / 2;
            graphics.drawString(font, red, center - 10 - font.width(red), 9, PvpTeam.RED.color(), false);
            graphics.drawString(font, ":", center - 2, 9, MUTED, false);
            graphics.drawString(font, blue, center + 8, 9, PvpTeam.BLUE.color(), false);
            int seconds = Math.max(0, hud.ticksLeft()) / 20;
            String time = String.format("残り %02d:%02d", seconds / 60, seconds % 60);
            graphics.drawString(font, time, width - 12 - font.width(time), 9, TEXT, false);
        }
    }

    private void drawTerrain(GuiGraphics graphics, Rect frame) {
        PvpTacticalMap map = tactical();
        ResourceLocation texture = map == null ? null : PvpTacticalClient.texture();
        if (texture == null || map == null) {
            graphics.fill(frame.x(), frame.y(), frame.right(), frame.bottom(), 0x400A2A38);
            String waiting = "地形データを準備中…";
            graphics.drawCenteredString(font, waiting, frame.x() + frame.width() / 2, frame.y() + frame.height() / 2 - 4, MUTED);
            return;
        }
        RenderSystem.enableBlend();
        graphics.blit(texture, frame.x(), frame.y(), frame.width(), frame.height(), 0.0F, 0.0F,
                map.width(), map.depth(), map.width(), map.depth());
        RenderSystem.disableBlend();
    }

    private void drawGrid(GuiGraphics graphics, Rect frame) {
        for (int i = 0; i <= GRID; i++) {
            int x = frame.x() + frame.width() * i / GRID;
            int y = frame.y() + frame.height() * i / GRID;
            graphics.fill(x, frame.y(), x + 1, frame.bottom(), HOLO_DIM);
            graphics.fill(frame.x(), y, frame.right(), y + 1, HOLO_DIM);
        }
        graphics.pose().pushPose();
        float scale = 0.75F;
        graphics.pose().scale(scale, scale, 1.0F);
        for (int i = 0; i < GRID; i++) {
            int cx = frame.x() + frame.width() * i / GRID + frame.width() / GRID / 2;
            int cy = frame.y() + frame.height() * i / GRID + frame.height() / GRID / 2;
            graphics.drawString(font, String.valueOf((char) ('A' + i)), (int) ((cx - 2) / scale), (int) ((frame.y() - 9) / scale), HOLO, false);
            graphics.drawString(font, String.valueOf(i + 1), (int) ((frame.x() - 9) / scale), (int) ((cy - 3) / scale), HOLO, false);
        }
        graphics.pose().popPose();
    }

    private void drawZone(GuiGraphics graphics, Rect frame, double[] world, long now) {
        S2C_PvpZonePacket zone = PvpHardpointClientState.zone();
        if (!zone.active()) return;
        int color = PvpHardpointClientState.color(now) & 0xFFFFFF;
        int x1 = Math.round(screenX(frame, world, Math.min(zone.min().getX(), zone.max().getX())));
        int z1 = Math.round(screenY(frame, world, Math.min(zone.min().getZ(), zone.max().getZ())));
        int x2 = Math.round(screenX(frame, world, Math.max(zone.min().getX(), zone.max().getX()) + 1));
        int z2 = Math.round(screenY(frame, world, Math.max(zone.min().getZ(), zone.max().getZ()) + 1));
        float pulse = 0.5F + 0.5F * Mth.sin(now / 260.0F);
        graphics.fill(x1, z1, x2, z2, (int) (0x30 + 0x30 * pulse) << 24 | color);
        drawBorder(graphics, new Rect(x1, z1, Math.max(1, x2 - x1), Math.max(1, z2 - z1)), 0xFF000000 | color);
        graphics.drawCenteredString(font, "丘", (x1 + x2) / 2, (z1 + z2) / 2 - 4, 0xFF000000 | color);
    }

    private void drawMarkers(GuiGraphics graphics, Rect frame, double[] world, long now) {
        int team = teamColor();
        for (S2C_PvpAllyPositionsPacket.Ally ally : PvpTacticalClient.allies()) {
            int x = Math.round(screenX(frame, world, ally.x() + 0.5));
            int y = Math.round(screenY(frame, world, ally.z() + 0.5));
            graphics.fill(x - 2, y - 2, x + 3, y + 3, 0xFF000000 | team);
            double yaw = Math.toRadians(ally.yaw());
            int tipX = x + (int) Math.round(-Math.sin(yaw) * 7);
            int tipY = y + (int) Math.round(Math.cos(yaw) * 7);
            line(graphics, x, y, tipX, tipY, 0xFF000000 | team);
            small(graphics, ally.name(), x + 5, y - 3, 0xFFDDF6FF);
        }
        if (state.hasKiller()) {
            int x = Math.round(screenX(frame, world, state.killerX() + 0.5));
            int y = Math.round(screenY(frame, world, state.killerZ() + 0.5));
            boolean on = (now / 300) % 2 == 0;
            if (on) graphics.fill(x - 3, y - 3, x + 4, y + 4, 0xFFFF4052);
            small(graphics, "敵", x + 6, y - 3, 0xFFFF4052);
        }
        if (state.hasDeath()) {
            int x = Math.round(screenX(frame, world, state.deathX() + 0.5));
            int y = Math.round(screenY(frame, world, state.deathZ() + 0.5));
            line(graphics, x - 4, y - 4, x + 4, y + 4, 0xFFFFFFFF);
            line(graphics, x - 4, y + 4, x + 4, y - 4, 0xFFFFFFFF);
            small(graphics, "倒された位置", x + 6, y - 3, 0xFFFFFFFF);
        }
    }

    private void drawPoints(GuiGraphics graphics, Rect frame, double[] world, int mouseX, int mouseY, long now) {
        int team = teamColor();
        for (S2C_PvpDeployPacket.Point point : state.points()) {
            int x = Math.round(screenX(frame, world, point.x() + 0.5));
            int y = Math.round(screenY(frame, world, point.z() + 0.5));
            boolean chosen = point.id() == chosenPoint;
            boolean hovered = Math.abs(mouseX - x) <= 7 && Math.abs(mouseY - y) <= 7;
            int color = point.danger() ? 0xFFFF4052 : 0xFF000000 | team;
            if (chosen) {
                int ring = 9 + (int) (3 * (0.5F + 0.5F * Mth.sin(now / 180.0F)));
                drawBorder(graphics, new Rect(x - ring, y - ring, ring * 2 + 1, ring * 2 + 1), 0xFFFFFFFF);
            }
            diamond(graphics, x, y, hovered || chosen ? 6 : 5, color);
            if (point.danger()) {
                if ((now / 250) % 2 == 0) graphics.drawCenteredString(font, "危険", x, y - 17, 0xFFFF4052);
            }
            String label = point.name();
            int labelColor = chosen ? 0xFFFFFFFF : hovered ? TEXT : 0xFFB7DDE8;
            graphics.fill(x + 8, y - 5, x + 12 + font.width(label), y + 6, 0xB0000000);
            graphics.drawString(font, label, x + 10, y - 3, labelColor, false);
        }
    }

    private void drawScanlines(GuiGraphics graphics, Rect frame, long now) {
        for (int y = frame.y(); y < frame.bottom(); y += 3) {
            graphics.fill(frame.x(), y, frame.right(), y + 1, 0x14000000);
        }
        double phase = (now / 1000.0 % SWEEP_SECONDS) / SWEEP_SECONDS;
        int sweep = frame.y() + (int) (phase * frame.height());
        graphics.fillGradient(frame.x(), Math.max(frame.y(), sweep - 18), frame.right(), sweep, 0x002FD8FF, 0x302FD8FF);
        graphics.fill(frame.x(), sweep, frame.right(), Math.min(frame.bottom(), sweep + 1), 0x702FD8FF);
    }

    private void drawFrameCorners(GuiGraphics graphics, Rect frame) {
        int len = 12;
        int[][] corners = {{frame.x(), frame.y(), 1, 1}, {frame.right(), frame.y(), -1, 1},
                {frame.x(), frame.bottom(), 1, -1}, {frame.right(), frame.bottom(), -1, -1}};
        for (int[] c : corners) {
            int x = c[0], y = c[1];
            graphics.fill(Math.min(x, x + c[2] * len), y - (c[3] < 0 ? 1 : 0), Math.max(x, x + c[2] * len), y + (c[3] > 0 ? 1 : 0), HOLO);
            graphics.fill(x - (c[2] < 0 ? 1 : 0), Math.min(y, y + c[3] * len), x + (c[2] > 0 ? 1 : 0), Math.max(y, y + c[3] * len), HOLO);
        }
    }

    private void drawSide(GuiGraphics graphics, int mouseX, int mouseY) {
        Rect side = side();
        graphics.fill(side.x(), side.y(), side.right(), side.bottom(), PANEL);
        drawBorder(graphics, side, 0xFF16303D);
        graphics.drawString(font, "ロードアウト", side.x() + 8, side.y() + 8, HOLO, false);

        Rect list = loadoutList();
        List<PvpLoadoutDefinition> loadouts = PvpClientState.getLoadouts();
        graphics.enableScissor(list.x(), list.y(), list.right(), list.bottom());
        for (int i = 0; i < loadouts.size(); i++) {
            PvpLoadoutDefinition loadout = loadouts.get(i);
            Rect row = new Rect(list.x(), list.y() + i * (LOADOUT_ROW + 4) - loadoutScroll, list.width(), LOADOUT_ROW);
            if (row.bottom() < list.y() || row.y() > list.bottom()) continue;
            boolean chosen = loadout.id().equals(chosenLoadout);
            drawCard(graphics, row, loadout.color(), chosen, row.contains(mouseX, mouseY) && list.contains(mouseX, mouseY));
            graphics.drawString(font, loadout.displayName(), row.x() + 8, row.y() + 5, chosen ? loadout.color() : TEXT, false);
            graphics.drawString(font, font.plainSubstrByWidth(loadout.weaponSummary(), row.width() - 14),
                    row.x() + 8, row.y() + 17, MUTED, false);
        }
        graphics.disableScissor();

        int infoY = deployButton().y() - 34;
        S2C_PvpDeployPacket.Point point = point(chosenPoint);
        graphics.drawString(font, "出撃地点", side.x() + 8, infoY, MUTED, false);
        graphics.drawString(font, font.plainSubstrByWidth(point == null ? "自動（安全な地点）" : point.name(), side.width() - 16),
                side.x() + 8, infoY + 12, point == null ? TEXT : 0xFFFFFFFF, false);

        Rect deploy = deployButton();
        drawButton(graphics, font, deploy, Component.literal("出撃"), HOLO, deploy.contains(mouseX, mouseY), true);
        int seconds = (autoTicks + 19) / 20;
        String auto = "自動出撃まで " + seconds + "秒";
        graphics.drawCenteredString(font, auto, deploy.x() + deploy.width() / 2, deploy.bottom() + 6,
                seconds <= 5 ? GOLD : MUTED);

        Rect leave = leaveButton();
        boolean hover = leave.contains(mouseX, mouseY);
        graphics.drawCenteredString(font, "試合から離脱", leave.x() + leave.width() / 2, leave.y() + 4, hover ? DANGER : 0xFF6B7684);
    }

    // --- input ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || deployed) return super.mouseClicked(mouseX, mouseY, button);
        if (deployButton().contains(mouseX, mouseY)) {
            deployNow();
            return true;
        }
        if (leaveButton().contains(mouseX, mouseY)) {
            PacketDistributor.sendToServer(new C2S_PvpActionPacket(false, chosenLoadout));
            return true;
        }
        Rect list = loadoutList();
        if (list.contains(mouseX, mouseY)) {
            List<PvpLoadoutDefinition> loadouts = PvpClientState.getLoadouts();
            for (int i = 0; i < loadouts.size(); i++) {
                Rect row = new Rect(list.x(), list.y() + i * (LOADOUT_ROW + 4) - loadoutScroll, list.width(), LOADOUT_ROW);
                if (row.contains(mouseX, mouseY)) {
                    chosenLoadout = loadouts.get(i).id();
                    playUi(SoundEvents.UI_BUTTON_CLICK.value(), 1.2F, 0.4F);
                    sendChoice(false);
                    return true;
                }
            }
            return true;
        }
        Rect frame = mapFrame();
        double[] world = world();
        for (S2C_PvpDeployPacket.Point point : state.points()) {
            int x = Math.round(screenX(frame, world, point.x() + 0.5));
            int y = Math.round(screenY(frame, world, point.z() + 0.5));
            if (Math.abs(mouseX - x) <= 7 && Math.abs(mouseY - y) <= 7) {
                if (point.danger()) {
                    playUi(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 0.6F);
                } else {
                    chosenPoint = chosenPoint == point.id() ? S2C_PvpDeployPacket.NO_POINT : point.id();
                    playUi(SoundEvents.UI_BUTTON_CLICK.value(), 1.5F, 0.4F);
                    sendChoice(false);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Rect list = loadoutList();
        if (!list.contains(mouseX, mouseY)) return false;
        int content = PvpClientState.getLoadouts().size() * (LOADOUT_ROW + 4);
        loadoutScroll = MoveEarthUi.scroll(loadoutScroll, scrollY, 20, content, list.height());
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            deployNow();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void deployNow() {
        if (deployed) return;
        deployed = true;
        deployWait = 0;
        playUi(SoundEvents.BEACON_POWER_SELECT, 1.3F, 0.5F);
        sendChoice(true);
    }

    private void sendChoice(boolean now) {
        PacketDistributor.sendToServer(new C2S_PvpDeployPacket(chosenPoint, chosenLoadout, now));
    }

    // --- helpers ---

    private S2C_PvpDeployPacket.Point point(long id) {
        for (S2C_PvpDeployPacket.Point point : state.points()) if (point.id() == id) return point;
        return null;
    }

    private int teamColor() {
        return (state.team() == PvpTeam.BLUE.ordinal() ? PvpTeam.BLUE : PvpTeam.RED).color() & 0xFFFFFF;
    }

    private void diamond(GuiGraphics graphics, int x, int y, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = radius - Math.abs(dy);
            graphics.fill(x - half, y + dy, x + half + 1, y + dy + 1, color);
        }
        graphics.fill(x - 1, y - 1, x + 2, y + 2, 0xFF000000);
    }

    private static void line(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            int x = steps == 0 ? x1 : x1 + (x2 - x1) * i / steps;
            int y = steps == 0 ? y1 : y1 + (y2 - y1) * i / steps;
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private void small(GuiGraphics graphics, String text, int x, int y, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(0.7F, 0.7F, 1.0F);
        graphics.drawString(font, text, 0, 0, color, true);
        graphics.pose().popPose();
    }

    private static float easeOut(float t) {
        return 1.0F - (1.0F - t) * (1.0F - t);
    }

    private void playUi(net.minecraft.sounds.SoundEvent sound, float pitch, float volume) {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
