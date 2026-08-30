package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.network.C2S_DeleteLoadoutPacket;
import com.ruskserver.moveearth_addtional.network.C2S_ReorderLoadoutsPacket;
import com.ruskserver.moveearth_addtional.network.C2S_SaveLoadoutPacket;
import com.ruskserver.moveearth_addtional.pvp.PvpLoadoutDefinition;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理者向け PvP ロードアウト・インゲームエディターGUI。
 */
public final class PvpLoadoutEditorScreen extends Screen {

    private static final int PANEL_WIDTH = 620;
    private static final int PANEL_HEIGHT = 350;

    private static final int BG_COLOR = 0xF010141C;
    private static final int BORDER_COLOR = 0xFF354150;
    private static final int CARD_BG = 0xFF19202B;
    private static final int CARD_SELECTED = 0xFF2A3647;
    private static final int TEXT_MAIN = 0xFFE8EDF3;
    private static final int TEXT_MUTED = 0xFF8F9AA8;
    private static final int ACCENT_CYAN = 0xFF5DCBFF;
    private static final int BTN_SAVE = 0xFF38B000;
    private static final int BTN_DANGER = 0xFFFF4D4D;

    private final List<PvpLoadoutDefinition> loadouts = new ArrayList<>();
    private int selectedIndex = 0;
    private int scrollOffset = 0;

    // 編集用入力ボックス
    private EditBox idEdit;
    private EditBox nameEdit;
    private EditBox descEdit;
    private EditBox weaponSummaryEdit;
    private EditBox attachSummaryEdit;
    private EditBox ttkEdit;
    private EditBox colorEdit;

    private boolean isCreatingNew = false;
    private PvpLoadoutDefinition editingCopy;

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
    }

    @Override
    protected void init() {
        super.init();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        int formX = left + 230;

        // 入力フィールドの初期化
        idEdit = new EditBox(font, formX + 70, top + 40, 140, 16, Component.literal("ID"));
        nameEdit = new EditBox(font, formX + 70, top + 64, 180, 16, Component.literal("表示名"));
        descEdit = new EditBox(font, formX + 70, top + 88, 290, 16, Component.literal("説明"));
        weaponSummaryEdit = new EditBox(font, formX + 70, top + 112, 290, 16, Component.literal("武器概要"));
        attachSummaryEdit = new EditBox(font, formX + 70, top + 136, 290, 16, Component.literal("アタッチメント概要"));
        ttkEdit = new EditBox(font, formX + 70, top + 160, 100, 16, Component.literal("TTK"));
        colorEdit = new EditBox(font, formX + 230, top + 160, 90, 16, Component.literal("Color"));

        addRenderableWidget(idEdit);
        addRenderableWidget(nameEdit);
        addRenderableWidget(descEdit);
        addRenderableWidget(weaponSummaryEdit);
        addRenderableWidget(attachSummaryEdit);
        addRenderableWidget(ttkEdit);
        addRenderableWidget(colorEdit);

        // リスト操作ボタン（左ペイン下部）
        addRenderableWidget(Button.builder(Component.literal("+ 新規"), b -> createNew())
                .bounds(left + 15, top + PANEL_HEIGHT - 32, 48, 20).build());
        addRenderableWidget(Button.builder(Component.literal("複製"), b -> duplicateSelected())
                .bounds(left + 67, top + PANEL_HEIGHT - 32, 44, 20).build());
        addRenderableWidget(Button.builder(Component.literal("削除"), b -> deleteSelected())
                .bounds(left + 115, top + PANEL_HEIGHT - 32, 44, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▲"), b -> moveOrder(-1))
                .bounds(left + 163, top + PANEL_HEIGHT - 32, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▼"), b -> moveOrder(1))
                .bounds(left + 191, top + PANEL_HEIGHT - 32, 24, 20).build());

        // ワンクリックキャプチャボタン（右ペイン中央）
        addRenderableWidget(Button.builder(Component.literal("★ 手持ちの銃・アタッチメントを自動反映"), b -> captureFromInventory())
                .bounds(formX, top + 225, 270, 20).build());

        // フッター保存・閉じるボタン
        addRenderableWidget(Button.builder(Component.literal("キャンセル"), b -> onClose())
                .bounds(left + PANEL_WIDTH - 175, top + PANEL_HEIGHT - 32, 75, 22).build());
        addRenderableWidget(Button.builder(Component.literal("保存 (Save)"), b -> saveCurrent())
                .bounds(left + PANEL_WIDTH - 92, top + PANEL_HEIGHT - 32, 80, 22).build());

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
        }
    }

    private void saveCurrent() {
        if (editingCopy == null) return;
        syncFieldsToEditingCopy();

        if (editingCopy.id().isEmpty()) {
            return;
        }

        PacketDistributor.sendToServer(new C2S_SaveLoadoutPacket(editingCopy));
        isCreatingNew = false;
        idEdit.setEditable(false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        // 背景パネル
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, BG_COLOR);
        drawBorder(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT, BORDER_COLOR);

        // タイトル
        graphics.drawString(font, title, left + 16, top + 14, TEXT_MAIN, false);
        graphics.drawString(font, "§7登録数: " + loadouts.size(), left + 140, top + 14, TEXT_MUTED, false);

        // 左右分割線
        graphics.fill(left + 220, top + 35, left + 221, top + PANEL_HEIGHT - 40, BORDER_COLOR);

        // 左ペイン：ロードアウト一覧
        renderLoadoutList(graphics, left + 12, top + 35, 200, PANEL_HEIGHT - 75, mouseX, mouseY);

        // 右ペイン：編集ラベル
        int formX = left + 230;
        graphics.drawString(font, "ID:", formX, top + 44, TEXT_MUTED, false);
        graphics.drawString(font, "表示名:", formX, top + 68, TEXT_MUTED, false);
        graphics.drawString(font, "説明:", formX, top + 92, TEXT_MUTED, false);
        graphics.drawString(font, "武器概要:", formX, top + 116, TEXT_MUTED, false);
        graphics.drawString(font, "アタッチメント:", formX, top + 140, TEXT_MUTED, false);
        graphics.drawString(font, "TTK:", formX, top + 164, TEXT_MUTED, false);
        graphics.drawString(font, "Color:", formX + 180, top + 164, TEXT_MUTED, false);

        // 武器スロット構成のプレビュー
        renderWeaponsPreview(graphics, formX, top + 188);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderLoadoutList(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY) {
        graphics.enableScissor(x, y, x + width, y + height);
        int itemHeight = 36;
        int currentY = y - scrollOffset;

        for (int i = 0; i < loadouts.size(); i++) {
            PvpLoadoutDefinition def = loadouts.get(i);
            boolean isSelected = (!isCreatingNew && i == selectedIndex);
            boolean isHovered = mouseX >= x && mouseX <= x + width && mouseY >= currentY && mouseY < currentY + itemHeight;

            int bg = isSelected ? CARD_SELECTED : (isHovered ? 0xFF222B38 : CARD_BG);
            graphics.fill(x, currentY, x + width, currentY + itemHeight - 3, bg);

            // カラーバー
            graphics.fill(x, currentY, x + 4, currentY + itemHeight - 3, def.color());

            // テキスト
            graphics.drawString(font, (i + 1) + ". " + def.displayName(), x + 8, currentY + 5, isSelected ? ACCENT_CYAN : TEXT_MAIN, false);
            String summary = def.weaponSummary().isEmpty() ? def.id() : def.weaponSummary();
            graphics.drawString(font, font.plainSubstrByWidth(summary, width - 16), x + 8, currentY + 18, TEXT_MUTED, false);

            currentY += itemHeight;
        }

        graphics.disableScissor();
    }

    private void renderWeaponsPreview(GuiGraphics graphics, int x, int y) {
        graphics.drawString(font, "◆ 登録武器スロット一覧:", x, y, TEXT_MAIN, false);
        if (editingCopy == null || editingCopy.weapons().isEmpty()) {
            graphics.drawString(font, "§8(武器が未設定です。手持ち銃自動反映を使用してください)", x + 10, y + 14, TEXT_MUTED, false);
            return;
        }

        int startY = y + 14;
        for (PvpLoadoutDefinition.WeaponDefinition w : editingCopy.weapons()) {
            String info = "スロット " + w.slot() + ": §b" + w.gunId().getPath() + " §7(" + w.attachments().size() + " atts)";
            graphics.drawString(font, info, x + 10, startY, TEXT_MAIN, false);
            startY += 11;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
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
        int maxScroll = Math.max(0, loadouts.size() * 36 - (PANEL_HEIGHT - 75));
        scrollOffset = Mth.clamp(scrollOffset - (int) (scrollY * 18), 0, maxScroll);
        return true;
    }

    private void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
