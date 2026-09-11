package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.network.C2S_CreateNationPacket;
import com.ruskserver.moveearth_addtional.network.C2S_RequestS2HubPacket;
import com.ruskserver.moveearth_addtional.network.S2C_S2ActionResultPacket;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import com.ruskserver.moveearth_addtional.s2.nation.NationNamePolicy;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

public final class NationCreateScreen extends Screen implements SuppressesChatOverlay {
    private static final int PANEL_WIDTH = 430;
    private static final int PANEL_HEIGHT = 248;
    private static int nextRequestId;

    private long revision;
    private EditBox nameEdit;
    private EditBox tagEdit;
    private int pendingRequestId = -1;
    private Component toast;
    private int toastTicks;

    public NationCreateScreen(long revision) {
        super(Component.translatable("screen.moveearth_addtional.nation.create_title"));
        this.revision = revision;
    }

    @Override
    protected void init() {
        Rect panel = panelBounds();
        nameEdit = new EditBox(font, panel.x() + 30, panel.y() + 76,
                panel.width() - 60, 18, Component.translatable("screen.moveearth_addtional.nation.name"));
        tagEdit = new EditBox(font, panel.x() + 30, panel.y() + 127,
                124, 18, Component.translatable("screen.moveearth_addtional.nation.tag"));
        nameEdit.setMaxLength(NationNamePolicy.MAX_NAME_LENGTH);
        tagEdit.setMaxLength(NationNamePolicy.MAX_TAG_LENGTH);
        configure(nameEdit);
        configure(tagEdit);
        addRenderableWidget(nameEdit);
        addRenderableWidget(tagEdit);
        setInitialFocus(nameEdit);
    }

    private static void configure(EditBox edit) {
        edit.setBordered(false);
        edit.setTextColor(TEXT);
        edit.setTextColorUneditable(MUTED);
    }

    public void handleResult(S2C_S2ActionResultPacket packet) {
        if (packet.requestId() != pendingRequestId) return;
        pendingRequestId = -1;
        revision = packet.latestRevision();
        toast = MoveEarthMessage.error(Component.translatable(packet.messageKey()));
        toastTicks = 80;
    }

    @Override
    public void tick() {
        super.tick();
        if (toastTicks > 0) toastTicks--;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(graphics, width, height);
        Rect panel = panelBounds();
        drawPanel(graphics, panel);
        graphics.drawString(font, title, panel.x() + 20, panel.y() + 16, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.nation.create_detail"),
                panel.x() + 20, panel.y() + 32, MUTED, false);
        Rect close = new Rect(panel.right() - 28, panel.y() + 8, 20, 20);
        drawClose(graphics, font, close, close.contains(mouseX, mouseY));

        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.nation.name"),
                panel.x() + 30, panel.y() + 61, MUTED, false);
        drawField(graphics, nameEdit);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.nation.tag"),
                panel.x() + 30, panel.y() + 112, MUTED, false);
        drawField(graphics, tagEdit);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.nation.tag_hint"),
                panel.x() + 164, panel.y() + 132, MUTED, false);

        NationNamePolicy.Validation validation = validation();
        if (!validation.valid() && (!nameEdit.getValue().isEmpty() || !tagEdit.getValue().isEmpty())) {
            graphics.drawString(font, Component.translatable(
                            "screen.moveearth_addtional.nation.validation." + validation.reason()),
                    panel.x() + 30, panel.y() + 158, DANGER, false);
        }

        Rect cancel = cancelBounds(panel);
        Rect create = createBounds(panel);
        drawButton(graphics, font, cancel,
                Component.translatable("screen.moveearth_addtional.nation.cancel"), MUTED,
                cancel.contains(mouseX, mouseY), true);
        boolean canCreate = validation.valid() && pendingRequestId < 0;
        drawButton(graphics, font, create,
                Component.translatable(pendingRequestId < 0
                        ? "screen.moveearth_addtional.nation.create" : "screen.moveearth_addtional.nation.creating"),
                SUCCESS, canCreate && create.contains(mouseX, mouseY), canCreate);

        super.render(graphics, mouseX, mouseY, partialTick);
        if (toastTicks > 0 && toast != null) drawToast(graphics, font, width, height, toast, DANGER);
    }

    private static void drawField(GuiGraphics graphics, EditBox field) {
        Rect bounds = new Rect(field.getX() - 5, field.getY() - 3, field.getWidth() + 10, field.getHeight() + 6);
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), CARD);
        drawBorder(graphics, bounds, field.isFocused() ? ACCENT : BORDER);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            Rect panel = panelBounds();
            if (new Rect(panel.right() - 28, panel.y() + 8, 20, 20).contains(mouseX, mouseY)
                    || cancelBounds(panel).contains(mouseX, mouseY)) {
                returnToHub();
                return true;
            }
            NationNamePolicy.Validation validation = validation();
            if (pendingRequestId < 0 && validation.valid()
                    && createBounds(panel).contains(mouseX, mouseY)) {
                pendingRequestId = ++nextRequestId;
                PacketDistributor.sendToServer(new C2S_CreateNationPacket(
                        pendingRequestId, revision, validation.name(), validation.tag()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private NationNamePolicy.Validation validation() {
        return NationNamePolicy.validate(nameEdit.getValue(), tagEdit.getValue());
    }

    private void returnToHub() {
        PacketDistributor.sendToServer(new C2S_RequestS2HubPacket(S2HubTab.OVERVIEW));
    }

    @Override
    public void onClose() {
        returnToHub();
    }

    private Rect panelBounds() {
        int panelWidth = Math.min(PANEL_WIDTH, width - 20);
        int panelHeight = Math.min(PANEL_HEIGHT, height - 20);
        return new Rect((width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight);
    }

    private static Rect cancelBounds(Rect panel) {
        return new Rect(panel.x() + 20, panel.bottom() - 39, 98, 23);
    }

    private static Rect createBounds(Rect panel) {
        return new Rect(panel.right() - 140, panel.bottom() - 39, 120, 23);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
