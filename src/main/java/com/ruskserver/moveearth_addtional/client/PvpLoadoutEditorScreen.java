package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import com.ruskserver.moveearth_addtional.network.C2S_DeleteLoadoutPacket;
import com.ruskserver.moveearth_addtional.network.C2S_ReorderLoadoutsPacket;
import com.ruskserver.moveearth_addtional.network.C2S_SaveLoadoutPacket;
import com.ruskserver.moveearth_addtional.pvp.PvpLoadoutDefinition;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.ruskserver.moveearth_addtional.client.ui.MoveEarthTextField;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

/**
 * 管理者向け PvP ロードアウト・インゲームエディターGUI。
 */
public final class PvpLoadoutEditorScreen extends Screen implements com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay {

    private static final int PANEL_WIDTH = 620;
    private static final int PANEL_HEIGHT = 350;

    private final List<PvpLoadoutDefinition> loadouts = new ArrayList<>();
    private int selectedIndex = 0;
    private int scrollOffset = 0;

    // 編集用入力ボックス
    private MoveEarthTextField idEdit;
    private MoveEarthTextField nameEdit;
    private MoveEarthTextField descEdit;
    private MoveEarthTextField weaponSummaryEdit;
    private MoveEarthTextField attachSummaryEdit;
    private MoveEarthTextField ttkEdit;
    private MoveEarthTextField colorEdit;

    private boolean isCreatingNew = false;
    private PvpLoadoutDefinition editingCopy;
    private boolean deleteConfirmation;
    private Component toastMessage;
    private int toastColor = ACCENT;
    private int toastTicks;

    public PvpLoadoutEditorScreen(List<PvpLoadoutDefinition> initialLoadouts) {
        super(Component.literal("PvP ロードアウトエディター"));
        for (PvpLoadoutDefinition def : initialLoadouts) {
            this.loadouts.add(def.copy());
        }
        if (!loadouts.isEmpty()) {
            this.editingCopy = loadouts.getFirst().copy();
        }
    }

    public void updateLoadouts(List<PvpLoadoutDefinition> updated) {
        this.loadouts.clear();
        for (PvpLoadoutDefinition def : updated) {
            this.loadouts.add(def.copy());
        }
        if (selectedIndex >= loadouts.size()) {
            selectedIndex = Math.max(0, loadouts.size() - 1);
        }
        if (!loadouts.isEmpty()) {
            this.editingCopy = loadouts.get(selectedIndex).copy();
            loadFieldsFromEditingCopy();
        }
        showToast(Component.literal("ロードアウトを保存しました"), SUCCESS);
    }

    @Override
    protected void init() {
        super.init();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        int formX = left + 230;

        // 入力フィールドの初期化
        idEdit = new MoveEarthTextField(font, formX + 68, top + 38, 144, 20, Component.literal("ID"));
        nameEdit = new MoveEarthTextField(font, formX + 68, top + 62, 184, 20, Component.literal("表示名"));
        descEdit = new MoveEarthTextField(font, formX + 68, top + 86, 294, 20, Component.literal("説明"));
        weaponSummaryEdit = new MoveEarthTextField(font, formX + 68, top + 110, 294, 20,
                Component.literal("武器概要"));
        attachSummaryEdit = new MoveEarthTextField(font, formX + 68, top + 134, 294, 20,
                Component.literal("アタッチメント概要"));
        ttkEdit = new MoveEarthTextField(font, formX + 68, top + 158, 104, 20, Component.literal("TTK"));
        colorEdit = new MoveEarthTextField(font, formX + 228, top + 158, 94, 20, Component.literal("Color"));

        for (MoveEarthTextField field : fields()) {
            addRenderableWidget(field);
        }

        loadFieldsFromEditingCopy();
    }

    private void loadFieldsFromEditingCopy() {
        if (editingCopy == null) return;
        idEdit.setValue(editingCopy.id());
        idEdit.setEditable(isCreatingNew);
        nameEdit.setValue(editingCopy.displayName());
        descEdit.setValue(editingCopy.description());
        weaponSummaryEdit.setValue(editingCopy.weaponSummary());
        attachSummaryEdit.setValue(editingCopy.attachmentSummary());
        ttkEdit.setValue(editingCopy.bodyTtk());
        colorEdit.setValue(String.format("#%06X", (0xFFFFFF & editingCopy.color())));
    }

    private void syncFieldsToEditingCopy() {
        if (editingCopy == null) return;
        if (isCreatingNew) {
            editingCopy.setId(idEdit.getValue().trim().toLowerCase().replaceAll("[^a-z0-9_]", "_"));
        }
        editingCopy.setDisplayName(nameEdit.getValue().trim());
        editingCopy.setDescription(descEdit.getValue().trim());
        editingCopy.setWeaponSummary(weaponSummaryEdit.getValue().trim());
        editingCopy.setAttachmentSummary(attachSummaryEdit.getValue().trim());
        editingCopy.setBodyTtk(ttkEdit.getValue().trim());

        try {
            String colorStr = colorEdit.getValue().trim().replace("#", "");
            int parsedColor = (int) Long.parseLong(colorStr, 16);
            editingCopy.setColor(0xFF000000 | parsedColor);
        } catch (Exception ignored) {}
    }

    private void createNew() {
        syncFieldsToEditingCopy();
        isCreatingNew = true;
        String newId = "custom_" + (loadouts.size() + 1);
        editingCopy = new PvpLoadoutDefinition(newId, "新規ロードアウト", "説明文", "", "", "350ms", 0xFF5DCBFF, new ArrayList<>());
        loadFieldsFromEditingCopy();
    }

    private void duplicateSelected() {
        if (editingCopy == null) return;
        syncFieldsToEditingCopy();
        isCreatingNew = true;
        PvpLoadoutDefinition dup = editingCopy.copy();
        dup.setId(dup.id() + "_copy");
        dup.setDisplayName(dup.displayName() + " (コピー)");
        editingCopy = dup;
        loadFieldsFromEditingCopy();
    }

    private void deleteSelected() {
        if (loadouts.size() <= 1) return;
        if (selectedIndex >= 0 && selectedIndex < loadouts.size()) {
            String targetId = loadouts.get(selectedIndex).id();
            PacketDistributor.sendToServer(new C2S_DeleteLoadoutPacket(targetId));
            loadouts.remove(selectedIndex);
            selectedIndex = Math.max(0, selectedIndex - 1);
            if (!loadouts.isEmpty()) {
                editingCopy = loadouts.get(selectedIndex).copy();
                isCreatingNew = false;
                loadFieldsFromEditingCopy();
            }
            showToast(Component.literal("削除要求を送信しました"), DANGER);
        }
    }

    private void moveOrder(int delta) {
        if (loadouts.size() <= 1) return;
        int target = selectedIndex + delta;
        if (target >= 0 && target < loadouts.size()) {
            PvpLoadoutDefinition item = loadouts.remove(selectedIndex);
            loadouts.add(target, item);
            selectedIndex = target;

            List<String> ids = new ArrayList<>();
            for (PvpLoadoutDefinition d : loadouts) {
                ids.add(d.id());
            }
            PacketDistributor.sendToServer(new C2S_ReorderLoadoutsPacket(ids));
        }
    }

    private void captureFromInventory() {
        Player player = Minecraft.getInstance().player;
        if (player == null || editingCopy == null) return;

        List<PvpLoadoutDefinition.WeaponDefinition> capturedWeapons = new ArrayList<>();
        List<String> weaponNames = new ArrayList<>();
        List<String> attachNames = new ArrayList<>();

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            IGun gun = IGun.getIGunOrNull(stack);
            if (gun != null) {
                PvpLoadoutDefinition.WeaponDefinition weaponDef = PvpLoadoutDefinition.WeaponDefinition.fromItemStack(slot, stack);
                if (weaponDef != null) {
                    capturedWeapons.add(weaponDef);
                    weaponNames.add(stack.getHoverName().getString());

                    for (ResourceLocation att : weaponDef.attachments()) {
                        attachNames.add(att.getPath());
                    }
                }
            }
        }

        if (!capturedWeapons.isEmpty()) {
            editingCopy.setWeapons(capturedWeapons);
            if (weaponSummaryEdit.getValue().isEmpty()) {
                weaponSummaryEdit.setValue(String.join(" + ", weaponNames));
            }
            if (attachSummaryEdit.getValue().isEmpty()) {
                attachSummaryEdit.setValue(String.join(" · ", attachNames));
            }
            showToast(Component.literal("手持ちの銃を反映しました"), ACCENT);
        } else {
            showToast(Component.literal("ホットバーに銃がありません"), GOLD);
        }
    }

    private void saveCurrent() {
        if (editingCopy == null) return;
        syncFieldsToEditingCopy();

        if (editingCopy.id().isEmpty()) {
            showToast(Component.literal("IDを入力してください"), DANGER);
            return;
        }

        PacketDistributor.sendToServer(new C2S_SaveLoadoutPacket(editingCopy));
        isCreatingNew = false;
        idEdit.setEditable(false);
        showToast(Component.literal("保存要求を送信しています"), GOLD);
    }

    private void showToast(Component message, int color) {
        toastMessage = message;
        toastColor = color;
        toastTicks = 80;
    }

    @Override
    public void tick() {
        super.tick();
        if (toastTicks > 0) toastTicks--;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 独自背景を描画するため、Minecraft標準の背景ブラーは適用しない。
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        drawBackground(graphics, width, height);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        // 背景パネル
        drawPanel(graphics, new MoveEarthUi.Rect(left, top, PANEL_WIDTH, PANEL_HEIGHT));

        // タイトル
        graphics.drawString(font, title, left + 16, top + 14, TEXT, false);
        graphics.drawString(font, "登録数: " + loadouts.size(), left + 140, top + 14, MUTED, false);

        // 左右分割線
        graphics.fill(left + 220, top + 35, left + 221, top + PANEL_HEIGHT - 40, BORDER);

        // 左ペイン：ロードアウト一覧
        renderLoadoutList(graphics, left + 12, top + 35, 200, PANEL_HEIGHT - 75, mouseX, mouseY);

        // 右ペイン：編集ラベル
        int formX = left + 230;
        graphics.drawString(font, "ID:", formX, top + 44, MUTED, false);
        graphics.drawString(font, "表示名:", formX, top + 68, MUTED, false);
        graphics.drawString(font, "説明:", formX, top + 92, MUTED, false);
        graphics.drawString(font, "武器概要:", formX, top + 116, MUTED, false);
        graphics.drawString(font, "アタッチメント:", formX, top + 140, MUTED, false);
        graphics.drawString(font, "TTK:", formX, top + 164, MUTED, false);
        graphics.drawString(font, "Color:", formX + 180, top + 164, MUTED, false);

        // 武器スロット構成のプレビュー
        renderWeaponsPreview(graphics, formX, top + 188);
        renderActions(graphics, left, top, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);

        if (deleteConfirmation) {
            renderDeleteConfirmation(graphics, mouseX, mouseY);
        } else if (toastTicks > 0 && toastMessage != null) {
            drawToast(graphics, font, width, height, toastMessage, toastColor);
        }
    }

    private void renderActions(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        drawAction(graphics, newBounds(left, top), Component.literal("+ 新規"), ACCENT, true, mouseX, mouseY);
        drawAction(graphics, duplicateBounds(left, top), Component.literal("複製"), ACCENT,
                editingCopy != null, mouseX, mouseY);
        drawAction(graphics, deleteBounds(left, top), Component.literal("削除"), DANGER,
                loadouts.size() > 1, mouseX, mouseY);
        drawAction(graphics, moveUpBounds(left, top), Component.literal("▲"), ACCENT,
                selectedIndex > 0 && !isCreatingNew, mouseX, mouseY);
        drawAction(graphics, moveDownBounds(left, top), Component.literal("▼"), ACCENT,
                selectedIndex + 1 < loadouts.size() && !isCreatingNew, mouseX, mouseY);
        drawAction(graphics, captureBounds(left, top),
                Component.literal("★ 手持ちの銃・アタッチメントを自動反映"), ACCENT,
                editingCopy != null, mouseX, mouseY);
        drawAction(graphics, cancelBounds(left, top), Component.literal("キャンセル"), MUTED,
                true, mouseX, mouseY);
        drawAction(graphics, saveBounds(left, top), Component.literal("保存"), SUCCESS,
                editingCopy != null, mouseX, mouseY);
    }

    private void drawAction(GuiGraphics graphics, MoveEarthUi.Rect bounds, Component label, int accent,
                            boolean enabled, int mouseX, int mouseY) {
        drawButton(graphics, font, bounds, label, accent, enabled && bounds.contains(mouseX, mouseY), enabled);
    }

    private void renderDeleteConfirmation(GuiGraphics graphics, int mouseX, int mouseY) {
        drawModalBackdrop(graphics, width, height);
        MoveEarthUi.Rect modal = deleteModalBounds();
        drawPanel(graphics, modal);
        graphics.drawString(font, "ロードアウトを削除", modal.x() + 16, modal.y() + 15, DANGER, false);
        String name = editingCopy == null ? "選択中のロードアウト" : editingCopy.displayName();
        graphics.drawString(font, font.plainSubstrByWidth(name + " を削除します。元に戻せません。", modal.width() - 32),
                modal.x() + 16, modal.y() + 38, TEXT, false);
        MoveEarthUi.Rect cancel = modalCancelBounds(modal);
        MoveEarthUi.Rect confirm = modalConfirmBounds(modal);
        drawButton(graphics, font, cancel, Component.literal("戻る"), MUTED,
                cancel.contains(mouseX, mouseY), true);
        drawButton(graphics, font, confirm, Component.literal("削除する"), DANGER,
                confirm.contains(mouseX, mouseY), true);
    }

    private void renderLoadoutList(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY) {
        graphics.enableScissor(x, y, x + width, y + height);
        int itemHeight = 36;
        int currentY = y - scrollOffset;

        for (int i = 0; i < loadouts.size(); i++) {
            PvpLoadoutDefinition def = loadouts.get(i);
            boolean isSelected = (!isCreatingNew && i == selectedIndex);
            boolean isHovered = mouseX >= x && mouseX <= x + width && mouseY >= currentY && mouseY < currentY + itemHeight;

            drawCard(graphics, new MoveEarthUi.Rect(x, currentY, width, itemHeight - 3),
                    def.color(), isSelected, isHovered);

            // テキスト
            graphics.drawString(font, (i + 1) + ". " + def.displayName(), x + 8, currentY + 5,
                    isSelected ? ACCENT : TEXT, false);
            String summary = def.weaponSummary().isEmpty() ? def.id() : def.weaponSummary();
            graphics.drawString(font, font.plainSubstrByWidth(summary, width - 16),
                    x + 8, currentY + 18, MUTED, false);

            currentY += itemHeight;
        }

        graphics.disableScissor();
    }

    private void renderWeaponsPreview(GuiGraphics graphics, int x, int y) {
        graphics.drawString(font, "◆ 登録武器スロット一覧:", x, y, TEXT, false);
        if (editingCopy == null || editingCopy.weapons().isEmpty()) {
            graphics.drawString(font, "(武器が未設定です。手持ち銃自動反映を使用してください)",
                    x + 10, y + 14, MUTED, false);
            return;
        }

        int startY = y + 14;
        for (PvpLoadoutDefinition.WeaponDefinition w : editingCopy.weapons()) {
            String info = "スロット " + w.slot() + ": §b" + w.gunId().getPath() + " §7(" + w.attachments().size() + " atts)";
            graphics.drawString(font, info, x + 10, startY, TEXT, false);
            startY += 11;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        if (deleteConfirmation) {
            MoveEarthUi.Rect modal = deleteModalBounds();
            if (modalCancelBounds(modal).contains(mouseX, mouseY)) {
                deleteConfirmation = false;
            } else if (modalConfirmBounds(modal).contains(mouseX, mouseY)) {
                deleteConfirmation = false;
                deleteSelected();
            }
            return true;
        }

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        if (newBounds(left, top).contains(mouseX, mouseY)) {
            createNew();
            return true;
        }
        if (duplicateBounds(left, top).contains(mouseX, mouseY)) {
            if (editingCopy != null) duplicateSelected();
            return true;
        }
        if (deleteBounds(left, top).contains(mouseX, mouseY)) {
            if (loadouts.size() > 1) {
                deleteConfirmation = true;
                setFocused(null);
            }
            return true;
        }
        if (moveUpBounds(left, top).contains(mouseX, mouseY)) {
            if (selectedIndex > 0 && !isCreatingNew) moveOrder(-1);
            return true;
        }
        if (moveDownBounds(left, top).contains(mouseX, mouseY)) {
            if (selectedIndex + 1 < loadouts.size() && !isCreatingNew) moveOrder(1);
            return true;
        }
        if (captureBounds(left, top).contains(mouseX, mouseY)) {
            if (editingCopy != null) captureFromInventory();
            return true;
        }
        if (cancelBounds(left, top).contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (saveBounds(left, top).contains(mouseX, mouseY)) {
            if (editingCopy != null) saveCurrent();
            return true;
        }

        int listX = left + 12;
        int listY = top + 35;
        int listWidth = 200;
        int listHeight = PANEL_HEIGHT - 75;

        if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + listHeight) {
            int itemHeight = 36;
            int clickedIndex = (int) (mouseY - listY + scrollOffset) / itemHeight;
            if (clickedIndex >= 0 && clickedIndex < loadouts.size()) {
                syncFieldsToEditingCopy();
                selectedIndex = clickedIndex;
                isCreatingNew = false;
                editingCopy = loadouts.get(selectedIndex).copy();
                loadFieldsFromEditingCopy();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = scroll(scrollOffset, scrollY, 18, loadouts.size() * 36, PANEL_HEIGHT - 75);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (deleteConfirmation && keyCode == 256) {
            deleteConfirmation = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private List<MoveEarthTextField> fields() {
        return List.of(idEdit, nameEdit, descEdit, weaponSummaryEdit, attachSummaryEdit, ttkEdit, colorEdit);
    }

    private static MoveEarthUi.Rect newBounds(int left, int top) {
        return new MoveEarthUi.Rect(left + 15, top + PANEL_HEIGHT - 32, 48, 20);
    }

    private static MoveEarthUi.Rect duplicateBounds(int left, int top) {
        return new MoveEarthUi.Rect(left + 67, top + PANEL_HEIGHT - 32, 44, 20);
    }

    private static MoveEarthUi.Rect deleteBounds(int left, int top) {
        return new MoveEarthUi.Rect(left + 115, top + PANEL_HEIGHT - 32, 44, 20);
    }

    private static MoveEarthUi.Rect moveUpBounds(int left, int top) {
        return new MoveEarthUi.Rect(left + 163, top + PANEL_HEIGHT - 32, 24, 20);
    }

    private static MoveEarthUi.Rect moveDownBounds(int left, int top) {
        return new MoveEarthUi.Rect(left + 191, top + PANEL_HEIGHT - 32, 24, 20);
    }

    private static MoveEarthUi.Rect captureBounds(int left, int top) {
        return new MoveEarthUi.Rect(left + 230, top + 225, 270, 20);
    }

    private static MoveEarthUi.Rect cancelBounds(int left, int top) {
        return new MoveEarthUi.Rect(left + PANEL_WIDTH - 175, top + PANEL_HEIGHT - 32, 75, 22);
    }

    private static MoveEarthUi.Rect saveBounds(int left, int top) {
        return new MoveEarthUi.Rect(left + PANEL_WIDTH - 92, top + PANEL_HEIGHT - 32, 80, 22);
    }

    private MoveEarthUi.Rect deleteModalBounds() {
        return new MoveEarthUi.Rect((width - 320) / 2, (height - 112) / 2, 320, 112);
    }

    private static MoveEarthUi.Rect modalCancelBounds(MoveEarthUi.Rect modal) {
        return new MoveEarthUi.Rect(modal.right() - 174, modal.bottom() - 34, 74, 22);
    }

    private static MoveEarthUi.Rect modalConfirmBounds(MoveEarthUi.Rect modal) {
        return new MoveEarthUi.Rect(modal.right() - 92, modal.bottom() - 34, 80, 22);
    }
}
