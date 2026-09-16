package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.compat.JeiTechnologyBridge;
import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.network.C2S_RequestS2HubPacket;
import com.ruskserver.moveearth_addtional.network.C2S_RequestTechnologyPacket;
import com.ruskserver.moveearth_addtional.network.C2S_SetTechnologyTrackedPacket;
import com.ruskserver.moveearth_addtional.network.C2S_TechnologyActionPacket;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import com.ruskserver.moveearth_addtional.s2.technology.TechnologyDefinition;
import com.ruskserver.moveearth_addtional.s2.technology.TechnologySnapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

/** Personal onboarding guide presented as familiar item-icon quests. */
public final class TechnologyScreen extends Screen implements SuppressesChatOverlay {
    private static final ResourceLocation STORAGE_RULES = ResourceLocation.fromNamespaceAndPath(
            "moveearth_addtional", "personal/storage_rules");
    private static final ResourceLocation CIVIC_READINESS = ResourceLocation.fromNamespaceAndPath(
            "moveearth_addtional", "personal/civic_readiness");
    private static final int NODE_SIZE = 56;
    private static final int NODE_STEP_X = 104;
    private static final int NODE_STEP_Y = 82;

    private TechnologySnapshot snapshot;
    private ResourceLocation selected;
    private double panX = 12.0D;
    private double panY = 12.0D;
    private double zoom = 1.0D;
    private boolean dragging;
    private EditBox search;

    public TechnologyScreen(TechnologySnapshot snapshot) {
        super(Component.translatable("screen.moveearth_addtional.technology.title"));
        update(snapshot);
    }

    public void update(TechnologySnapshot value) {
        snapshot = value;
        if (selected == null || node(selected) == null) {
            selected = visible().stream().findFirst().map(TechnologySnapshot.Node::id).orElse(null);
        }
    }

    @Override
    protected void init() {
        Rect panel = panel();
        Rect searchBounds = searchBounds(panel);
        search = new EditBox(font, searchBounds.x(), searchBounds.y(), searchBounds.width(), searchBounds.height(),
                Component.translatable("screen.moveearth_addtional.technology.search"));
        search.setHint(Component.translatable("screen.moveearth_addtional.technology.search"));
        search.setMaxLength(64);
        addRenderableWidget(search);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(graphics, width, height);
        Rect panel = panel();
        drawPanel(graphics, panel);
        graphics.drawString(font, title, panel.x() + 18, panel.y() + 13, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.technology.guide_subtitle"),
                panel.x() + 18, panel.y() + 29, MUTED, false);

        Rect close = new Rect(panel.right() - 28, panel.y() + 8, 20, 20);
        drawClose(graphics, font, close, close.contains(mouseX, mouseY));
        Rect refresh = refreshButton(panel);
        drawButton(graphics, font, refresh, Component.translatable("screen.moveearth_addtional.s2.refresh"),
                ACCENT, refresh.contains(mouseX, mouseY), true);

        TechnologySnapshot.Node hovered = drawQuestCanvas(graphics, listArea(panel), mouseX, mouseY);
        drawDetails(graphics, detailArea(panel), mouseX, mouseY);
        drawTracked(graphics, panel);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hovered != null) drawNodeTooltip(graphics, hovered, mouseX, mouseY);
    }

    private TechnologySnapshot.Node drawQuestCanvas(GuiGraphics graphics, Rect graph, int mouseX, int mouseY) {
        List<TechnologySnapshot.Node> nodes = visible();
        TechnologySnapshot.Node hovered = null;
        graphics.fill(graph.x(), graph.y(), graph.right(), graph.bottom(), 0xA00E1319);
        drawBorder(graphics, graph, BORDER);
        graphics.fill(graph.x() + 1, graph.y() + 1, graph.right() - 1, graph.y() + 3, 0x805DCBFF);
        graphics.enableScissor(graph.x() + 2, graph.y() + 2, graph.right() - 2, graph.bottom() - 2);
        for (TechnologySnapshot.Node node : nodes) {
            Rect card = nodeBounds(graph, node);
            for (ResourceLocation prerequisiteId : node.prerequisites()) {
                TechnologySnapshot.Node prerequisite = node(prerequisiteId);
                if (prerequisite == null || !nodes.contains(prerequisite)) continue;
                Rect from = nodeBounds(graph, prerequisite);
                drawGuideConnection(graphics, from, card, 0xFF000000 | stateColor(node.state()));
            }
        }
        for (TechnologySnapshot.Node node : nodes) {
            Rect card = nodeBounds(graph, node);
            boolean isHovered = card.contains(mouseX, mouseY);
            drawIconNode(graphics, card, node, stateColor(node.state()), node.id().equals(selected), isHovered);
            if (isHovered) hovered = node;
        }
        graphics.disableScissor();
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.technology.zoom",
                Math.round(zoom * 100.0D)), graph.x() + 4, graph.bottom() - 11, MUTED, false);
        return hovered;
    }

    private static void drawGuideConnection(GuiGraphics graphics, Rect from, Rect to, int color) {
        int fromCenterX = from.x() + from.width() / 2;
        int fromCenterY = from.y() + from.height() / 2;
        int toCenterX = to.x() + to.width() / 2;
        int toCenterY = to.y() + to.height() / 2;
        int deltaX = toCenterX - fromCenterX;
        int deltaY = toCenterY - fromCenterY;
        int gap = 5;
        int startX;
        int startY;
        int endX;
        int endY;
        if (Math.abs(deltaX) >= Math.abs(deltaY)) {
            boolean right = deltaX >= 0;
            startX = right ? from.right() + gap : from.x() - gap;
            endX = right ? to.x() - gap : to.right() + gap;
            startY = fromCenterY;
            endY = toCenterY;
        } else {
            boolean down = deltaY >= 0;
            startX = fromCenterX;
            endX = toCenterX;
            startY = down ? from.bottom() + gap : from.y() - gap;
            endY = down ? to.y() - gap : to.bottom() + gap;
        }
        drawGuideLine(graphics, startX, startY, endX, endY, 0xD0080C10, 5);
        drawGuideLine(graphics, startX, startY, endX, endY, color, 2);
    }

    private static void drawGuideLine(GuiGraphics graphics, int startX, int startY, int endX, int endY,
                                      int color, int thickness) {
        int middleX = (startX + endX) / 2;
        int half = thickness / 2;
        graphics.fill(Math.min(startX, middleX), startY - half,
                Math.max(startX, middleX) + 1, startY - half + thickness, color);
        graphics.fill(middleX - half, Math.min(startY, endY),
                middleX - half + thickness, Math.max(startY, endY) + 1, color);
        graphics.fill(Math.min(middleX, endX), endY - half,
                Math.max(middleX, endX) + 1, endY - half + thickness, color);
    }

    private void drawIconNode(GuiGraphics graphics, Rect card, TechnologySnapshot.Node node, int color,
                              boolean selectedNode, boolean hovered) {
        int background = selectedNode ? 0xF02A3632 : hovered ? 0xEC263038 : 0xE0181E25;
        graphics.fill(card.x() + 3, card.y() + 4, card.right() + 3, card.bottom() + 4, 0x78000000);
        graphics.fill(card.x(), card.y(), card.right(), card.bottom(), selectedNode ? color : BORDER);
        graphics.fill(card.x() + 2, card.y() + 2, card.right() - 2, card.bottom() - 2,
                selectedNode || hovered ? 0xFF000000 | color : BORDER_HOVER);
        graphics.fill(card.x() + 4, card.y() + 4, card.right() - 4, card.bottom() - 4, background);
        ResourceLocation icon = BuiltInRegistries.ITEM.containsKey(node.icon())
                ? node.icon() : ResourceLocation.withDefaultNamespace("knowledge_book");
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(icon));
        float iconScale = Math.min(2.0F, (card.width() - 12) / 16.0F);
        int iconSize = Math.round(16.0F * iconScale);
        graphics.pose().pushPose();
        graphics.pose().translate(card.x() + (card.width() - iconSize) / 2.0F,
                card.y() + (card.height() - iconSize) / 2.0F, 100);
        graphics.pose().scale(iconScale, iconScale, 1.0F);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
        if (node.tracked()) graphics.drawString(font, "★", card.right() - 12, card.y() + 4, GOLD, false);
        if (node.state() == TechnologySnapshot.State.COMPLETED) {
            graphics.drawString(font, "✓", card.right() - 12, card.bottom() - 12, SUCCESS, false);
        } else {
            long current = node.objectives().stream().mapToLong(o -> Math.min(o.current(), o.required())).sum();
            long required = node.objectives().stream().mapToLong(TechnologySnapshot.Objective::required).sum();
            int filled = required <= 0 ? 0 : Math.round((card.width() - 8) * Math.min(1F, (float) current / required));
            graphics.fill(card.x() + 4, card.bottom() - 6, card.right() - 4, card.bottom() - 3, 0xA0343A40);
            graphics.fill(card.x() + 4, card.bottom() - 6, card.x() + 4 + filled, card.bottom() - 3,
                    0xFF000000 | color);
        }
    }

    private void drawNodeTooltip(GuiGraphics graphics, TechnologySnapshot.Node node, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(node.titleKey()).withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable("screen.moveearth_addtional.technology.state." +
                node.state().name().toLowerCase(Locale.ROOT)).withStyle(stateFormatting(node.state())));
        font.split(description(node), 250).forEach(line -> lines.add(asComponent(line)
                .withStyle(ChatFormatting.GRAY)));
        if (!node.objectives().isEmpty()) lines.add(Component.empty());
        for (TechnologySnapshot.Objective objective : node.objectives()) {
            boolean complete = objective.current() >= objective.required();
            lines.add(Component.translatable(objective.descriptionKey(), objective.current(), objective.required())
                    .withStyle(complete ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        }
        lines.add(Component.translatable("screen.moveearth_addtional.technology.hover_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private void drawTracked(GuiGraphics graphics, Rect panel) {
        List<TechnologySnapshot.Node> tracked = snapshot.nodes().stream()
                .filter(node -> node.scope() == TechnologyDefinition.Scope.PERSONAL)
                .filter(TechnologySnapshot.Node::tracked).limit(3).toList();
        if (tracked.isEmpty()) return;
        int available = panel.width() - 48;
        int cardWidth = Math.max(110, Math.min(220, (available - 8 * (tracked.size() - 1)) / tracked.size()));
        int x = panel.x() + 16;
        int y = panel.bottom() - 30;
        for (TechnologySnapshot.Node node : tracked) {
            Rect card = new Rect(x, y, cardWidth, 20);
            drawCard(graphics, card, stateColor(node.state()), false, false);
            graphics.drawString(font, Component.translatable(node.titleKey()), x + 6, y + 6,
                    stateColor(node.state()), false);
            x += cardWidth + 8;
        }
    }

    private void drawDetails(GuiGraphics graphics, Rect area, int mouseX, int mouseY) {
        TechnologySnapshot.Node node = node(selected);
        drawCard(graphics, area, node == null ? MUTED : stateColor(node.state()), false, false);
        if (node == null) return;
        int color = stateColor(node.state());
        if (BuiltInRegistries.ITEM.containsKey(node.icon())) {
            graphics.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(node.icon())), area.x() + 12, area.y() + 11);
        }
        graphics.drawString(font, Component.translatable(node.titleKey()), area.x() + 34, area.y() + 14, color, false);
        Rect pin = pinButton(area);
        drawButton(graphics, font, pin, Component.translatable(node.tracked()
                        ? "screen.moveearth_addtional.technology.untrack" : "screen.moveearth_addtional.technology.track"),
                node.tracked() ? SUCCESS : ACCENT, pin.contains(mouseX, mouseY), true);

        int y = area.y() + 39;
        for (var line : font.split(description(node), area.width() - 24)) {
            graphics.drawString(font, line, area.x() + 12, y, MUTED, false);
            y += 10;
        }
        y += 8;
        for (TechnologySnapshot.Objective objective : node.objectives()) {
            Component text = Component.translatable(objective.descriptionKey(), objective.current(), objective.required());
            graphics.drawString(font, text, area.x() + 12, y,
                    objective.current() >= objective.required() ? SUCCESS : TEXT, false);
            y += 13;
        }
        GuideAction action = guideAction(node);
        if (action != null) {
            Rect button = guideActionButton(area);
            drawButton(graphics, font, button, Component.translatable(action.labelKey()), SUCCESS,
                    button.contains(mouseX, mouseY), true);
        }
        if (!node.jeiItems().isEmpty()) {
            y += 6;
            graphics.drawString(font, Component.translatable("screen.moveearth_addtional.technology.jei"),
                    area.x() + 12, y, MUTED, false);
            y += 13;
            int x = area.x() + 12;
            for (ResourceLocation id : node.jeiItems().stream().limit(8).toList()) {
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
                Rect item = new Rect(x, y, 22, 22);
                drawCard(graphics, item, ACCENT, false, item.contains(mouseX, mouseY));
                graphics.renderItem(stack, x + 3, y + 3);
                x += 26;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        Rect panel = panel();
        if (new Rect(panel.right() - 28, panel.y() + 8, 20, 20).contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (refreshButton(panel).contains(mouseX, mouseY)) {
            PacketDistributor.sendToServer(new C2S_RequestTechnologyPacket());
            return true;
        }
        Rect graph = listArea(panel);
        for (TechnologySnapshot.Node node : visible()) {
            if (nodeBounds(graph, node).contains(mouseX, mouseY)) {
                selected = node.id();
                return true;
            }
        }
        TechnologySnapshot.Node node = node(selected);
        Rect details = detailArea(panel);
        if (node != null && pinButton(details).contains(mouseX, mouseY)) {
            PacketDistributor.sendToServer(new C2S_SetTechnologyTrackedPacket(node.id(), !node.tracked()));
            return true;
        }
        GuideAction guideAction = node == null ? null : guideAction(node);
        if (guideAction != null && guideActionButton(details).contains(mouseX, mouseY)) {
            PacketDistributor.sendToServer(new C2S_TechnologyActionPacket(guideAction.action()));
            return true;
        }
        if (node != null && !node.jeiItems().isEmpty()) {
            int itemY = jeiItemY(node, details);
            if (mouseY >= itemY && mouseY < itemY + 22) {
                int itemIndex = (int) ((mouseX - details.x() - 12) / 26);
                if (itemIndex >= 0 && itemIndex < Math.min(8, node.jeiItems().size())
                        && mouseX >= details.x() + 12
                        && JeiTechnologyBridge.showRecipes(node.jeiItems().get(itemIndex))) {
                    PacketDistributor.sendToServer(new C2S_TechnologyActionPacket("jei_recipe_opened"));
                    return true;
                }
            }
        }
        if (graph.contains(mouseX, mouseY)) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double sx, double sy) {
        Rect graph = listArea(panel());
        if (graph.contains(mouseX, mouseY)) {
            double before = zoom;
            zoom = Math.max(0.65D, Math.min(1.5D, zoom + sy * 0.1D));
            double localX = mouseX - graph.x() - panX;
            double localY = mouseY - graph.y() - panY;
            panX -= localX * (zoom / before - 1.0D);
            panY -= localY * (zoom / before - 1.0D);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, sx, sy);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && listArea(panel()).contains(mouseX, mouseY)) {
            panX += dragX;
            panY += dragY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private List<TechnologySnapshot.Node> visible() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        return snapshot.nodes().stream().filter(node -> node.scope() == TechnologyDefinition.Scope.PERSONAL)
                .filter(node -> query.isEmpty()
                        || Component.translatable(node.titleKey()).getString().toLowerCase(Locale.ROOT).contains(query)
                        || node.id().toString().toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private TechnologySnapshot.Node node(ResourceLocation id) {
        return id == null ? null : snapshot.nodes().stream().filter(node -> node.id().equals(id)).findFirst().orElse(null);
    }

    private Component description(TechnologySnapshot.Node node) {
        return I18n.exists(node.descriptionKey()) ? Component.translatable(node.descriptionKey())
                : Component.translatable("technology.moveearth_addtional.description.default");
    }

    private static MutableComponent asComponent(FormattedCharSequence line) {
        StringBuilder text = new StringBuilder();
        line.accept((index, style, codePoint) -> {
            text.appendCodePoint(codePoint);
            return true;
        });
        return Component.literal(text.toString());
    }

    private int jeiItemY(TechnologySnapshot.Node node, Rect area) {
        int descriptionLines = font.split(description(node), area.width() - 24).size();
        return area.y() + 39 + descriptionLines * 10 + 8 + node.objectives().size() * 13 + 19;
    }

    private Rect panel() {
        int w = Math.min(1080, width - 12);
        int h = Math.min(620, height - 12);
        return new Rect((width - w) / 2, (height - h) / 2, w, h);
    }

    private static Rect refreshButton(Rect panel) { return new Rect(panel.right() - 120, panel.y() + 10, 82, 20); }
    private static Rect searchBounds(Rect panel) {
        int x = panel.x() + Math.min(320, Math.max(190, panel.width() / 4));
        int right = refreshButton(panel).x() - 10;
        return new Rect(x, panel.y() + 10, Math.max(110, right - x), 20);
    }
    private static Rect listArea(Rect panel) {
        int details = Math.min(320, Math.max(250, panel.width() / 3));
        return new Rect(panel.x() + 16, panel.y() + 58, panel.width() - 48 - details, panel.height() - 108);
    }
    private static Rect detailArea(Rect panel) {
        int details = Math.min(320, Math.max(250, panel.width() / 3));
        return new Rect(panel.right() - details - 16, panel.y() + 58, details, panel.height() - 108);
    }

    private Rect nodeBounds(Rect graph, TechnologySnapshot.Node node) {
        int size = Math.max(38, (int) Math.round(NODE_SIZE * zoom));
        return new Rect(graph.x() + (int) panX + (int) Math.round(Math.max(0, node.x()) * NODE_STEP_X * zoom),
                graph.y() + (int) panY + (int) Math.round(Math.max(0, node.y()) * NODE_STEP_Y * zoom), size, size);
    }

    private static int stateColor(TechnologySnapshot.State state) {
        return switch (state) {
            case COMPLETED -> SUCCESS;
            case INHERITED -> 0x78DCCA;
            case AVAILABLE -> ACCENT;
            case IN_PROGRESS -> GOLD;
            case LOCKED -> MUTED;
            case DISABLED -> 0xA85A5A;
        };
    }

    private static ChatFormatting stateFormatting(TechnologySnapshot.State state) {
        return switch (state) {
            case COMPLETED -> ChatFormatting.GREEN;
            case AVAILABLE -> ChatFormatting.AQUA;
            case IN_PROGRESS -> ChatFormatting.GOLD;
            case INHERITED -> ChatFormatting.DARK_AQUA;
            case LOCKED -> ChatFormatting.GRAY;
            case DISABLED -> ChatFormatting.RED;
        };
    }

    private static Rect pinButton(Rect area) { return new Rect(area.right() - 84, area.y() + 7, 72, 18); }
    private static Rect guideActionButton(Rect area) {
        return new Rect(area.x() + 12, area.bottom() - 30, area.width() - 24, 20);
    }

    private static GuideAction guideAction(TechnologySnapshot.Node node) {
        if (node.state() == TechnologySnapshot.State.LOCKED
                || node.state() == TechnologySnapshot.State.COMPLETED
                || node.state() == TechnologySnapshot.State.INHERITED
                || node.state() == TechnologySnapshot.State.DISABLED) return null;
        if (STORAGE_RULES.equals(node.id())) {
            return new GuideAction("storage_rules_viewed",
                    "screen.moveearth_addtional.technology.confirm_storage_rules");
        }
        if (CIVIC_READINESS.equals(node.id())) {
            return new GuideAction("founder_rules_confirmed",
                    "screen.moveearth_addtional.technology.confirm_founding");
        }
        return null;
    }

    private record GuideAction(String action, String labelKey) { }
    private void resetView() { panX = 12.0D; panY = 12.0D; zoom = 1.0D; }
    @Override public void onClose() { PacketDistributor.sendToServer(new C2S_RequestS2HubPacket(S2HubTab.OVERVIEW)); }
    @Override public boolean isPauseScreen() { return false; }
}
