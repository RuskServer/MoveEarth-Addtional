package com.ruskserver.moveearth_addtional.client.wiki;

import com.ruskserver.moveearth_addtional.client.HubTabs;
import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

/**
 * The wiki in the game: the site's pages, bundled at build time, with its sidebar beside them. Reached from
 * the hub tab strip, the wiki key, and the wiki key over an item that a page explains. Closing returns to the
 * screen it was opened from.
 */
public final class WikiScreen extends Screen implements SuppressesChatOverlay {
    private static final int PANEL_WIDTH = 760;
    private static final int PANEL_HEIGHT = 440;
    private static final int FIT_WIDTH = 560;
    private static final int FIT_HEIGHT = 320;
    private static final int SIDEBAR_WIDTH = 150;
    private static final int ENTRY_HEIGHT = 13;

    private final Screen parent;
    /** How it was opened and for which item, reported once for analytics. */
    private final String source;
    private final String item;
    private final WikiLibrary library;
    private final Deque<String> history = new ArrayDeque<>();
    private String slug;
    private WikiLayout layout;
    private int layoutWidth = -1;
    private int scroll;
    private int sidebarScroll;
    private boolean sidebarOpen;
    private boolean reported;

    private WikiScreen(Screen parent, String slug, String source, ItemStack item) {
        super(Component.translatable("screen.moveearth_addtional.wiki.title"));
        this.parent = parent;
        this.source = source;
        this.item = item.isEmpty() ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
        this.library = WikiLibrary.get();
        this.slug = slug;
    }

    /** Opens the wiki at its first page. */
    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        WikiLibrary library = WikiLibrary.get();
        minecraft.setScreen(new WikiScreen(minecraft.screen instanceof WikiScreen wiki ? wiki.parent : minecraft.screen,
                library.home().orElse(null), "key", ItemStack.EMPTY));
    }

    /** Opens the wiki from the hub tab strip: like the other tabs, closing it leaves the hub. */
    public static void openFromTabs() {
        Minecraft.getInstance().setScreen(new WikiScreen(null, WikiLibrary.get().home().orElse(null), "tab",
                ItemStack.EMPTY));
    }

    /**
     * Opens the page that explains {@code stack}; false when there is none. {@code held} is the item in hand
     * rather than one pointed at, which analytics tells apart.
     */
    public static boolean openFor(ItemStack stack, boolean held) {
        List<String> pages = WikiLibrary.get().pagesFor(stack);
        if (pages.isEmpty()) return false;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new WikiScreen(minecraft.screen, pages.getFirst(), held ? "held_item" : "item", stack));
        return true;
    }

    @Override
    protected void init() {
        HubTabs.fit(this, FIT_WIDTH, FIT_HEIGHT);
        // Tells the server, for the tutorial step that asks for the wiki to be opened once.
        if (!reported && minecraft != null && minecraft.getConnection() != null) {
            reported = true;
            report(true);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    private Rect panel() {
        int panelWidth = Math.min(PANEL_WIDTH, width - 20);
        int panelHeight = Math.min(PANEL_HEIGHT, HubTabs.maxPanelHeight(height));
        return new Rect((width - panelWidth) / 2, HubTabs.panelTop(height, panelHeight), panelWidth, panelHeight);
    }

    private boolean wide(Rect panel) {
        return panel.width() >= 520;
    }

    private Rect sidebar(Rect panel) {
        int top = panel.y() + 38;
        return new Rect(panel.x() + 12, top, SIDEBAR_WIDTH, Math.max(0, panel.bottom() - 12 - top));
    }

    private Rect content(Rect panel) {
        int top = panel.y() + 38;
        int left = wide(panel) ? panel.x() + 12 + SIDEBAR_WIDTH + 12 : panel.x() + 14;
        return new Rect(left, top, Math.max(40, panel.right() - 14 - left), Math.max(0, panel.bottom() - 12 - top));
    }

    private Rect closeButton(Rect panel) {
        return new Rect(panel.right() - 28, panel.y() + 8, 20, 20);
    }

    private Rect webButton(Rect panel) {
        return new Rect(panel.right() - 28 - 86, panel.y() + 9, 80, 18);
    }

    private Rect backButton(Rect panel) {
        return new Rect(webButton(panel).x() - 56, panel.y() + 9, 50, 18);
    }

    private Rect tocButton(Rect panel) {
        return new Rect(backButton(panel).x() - 56, panel.y() + 9, 50, 18);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(graphics, width, height);
        Rect panel = panel();
        drawPanel(graphics, panel);
        boolean wide = wide(panel);
        boolean showSidebar = wide || sidebarOpen;
        int titleRight = (wide ? backButton(panel) : tocButton(panel)).x() - 8;
        WikiLibrary.Page page = slug == null ? null : library.page(slug).orElse(null);
        graphics.drawString(font, title, panel.x() + 14, panel.y() + 14, ACCENT, false);
        if (page != null) {
            int titleX = panel.x() + 14 + font.width(title) + 8;
            graphics.drawString(font, font.plainSubstrByWidth(page.title(), Math.max(0, titleRight - titleX)),
                    titleX, panel.y() + 14, MUTED, false);
        }
        drawClose(graphics, font, closeButton(panel), closeButton(panel).contains(mouseX, mouseY));
        Rect web = webButton(panel);
        drawButton(graphics, font, web, Component.translatable("screen.moveearth_addtional.wiki.web"), ACCENT,
                web.contains(mouseX, mouseY), true);
        Rect back = backButton(panel);
        drawButton(graphics, font, back, Component.translatable("screen.moveearth_addtional.wiki.back"), MUTED,
                back.contains(mouseX, mouseY), !history.isEmpty());
        if (!wide) {
            Rect toc = tocButton(panel);
            drawButton(graphics, font, toc, Component.translatable("screen.moveearth_addtional.wiki.contents"), GOLD,
                    toc.contains(mouseX, mouseY), true);
        }

        Rect content = content(panel);
        if (page == null) {
            Component message = Component.translatable(library.isEmpty()
                    ? "screen.moveearth_addtional.wiki.missing" : "screen.moveearth_addtional.wiki.not_found");
            drawWrapped(graphics, font, message, content.x(), content.y() + 8, content.width(), MUTED);
        } else if (!(showSidebar && !wide)) {
            renderPage(graphics, page, content, mouseX, mouseY);
        }
        if (showSidebar) renderSidebar(graphics, wide ? sidebar(panel) : content, mouseX, mouseY);
        HubTabs.render(graphics, font, width, HubTabs.Tab.WIKI, mouseX, mouseY);
    }

    private void renderPage(GuiGraphics graphics, WikiLibrary.Page page, Rect content, int mouseX, int mouseY) {
        int textWidth = content.width() - 8;
        if (layout == null || layoutWidth != textWidth) {
            layout = new WikiLayout(font, library, page.document(), textWidth);
            layoutWidth = textWidth;
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, layout.height() - content.height() + 8)));
        boolean inside = content.contains(mouseX, mouseY);
        int hoverX = inside ? mouseX : -1;
        int hoverY = inside ? mouseY : -1;
        graphics.enableScissor(content.x(), content.y(), content.right(), content.bottom());
        for (WikiLayout.Placed placed : layout.placed()) {
            int y = content.y() + placed.y() - scroll;
            if (y + placed.element().height() < content.y() || y > content.bottom()) continue;
            placed.element().render(graphics, font, content.x() + placed.x(), y, hoverX, hoverY);
        }
        graphics.disableScissor();
        drawScrollbar(graphics, new Rect(content.right() - 3, content.y(), 3, content.height()), content.height(),
                layout.height() + 8, scroll);
    }

    private void renderSidebar(GuiGraphics graphics, Rect area, int mouseX, int mouseY) {
        graphics.fill(area.x(), area.y(), area.right(), area.bottom(), 0xC0101419);
        drawBorder(graphics, area, BORDER);
        int total = sidebarHeight();
        sidebarScroll = Math.max(0, Math.min(sidebarScroll, Math.max(0, total - area.height() + 6)));
        graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        int y = area.y() + 4 - sidebarScroll;
        for (WikiLibrary.Group group : library.groups()) {
            graphics.drawString(font, font.plainSubstrByWidth(group.label(), area.width() - 10), area.x() + 6, y + 2,
                    MUTED, false);
            y += ENTRY_HEIGHT;
            for (WikiLibrary.Entry entry : group.entries()) {
                Rect row = new Rect(area.x() + 2, y, area.width() - 4, ENTRY_HEIGHT);
                boolean current = entry.slug() != null && entry.slug().equals(slug);
                boolean hovered = row.contains(mouseX, mouseY) && area.contains(mouseX, mouseY);
                if (current || hovered) graphics.fill(row.x(), row.y(), row.right(), row.bottom(), current ? CARD_SELECTED : CARD_HOVER);
                if (current) graphics.fill(row.x(), row.y(), row.x() + 2, row.bottom(), ACCENT);
                String label = (entry.url() != null ? "↗ " : "") + entry.label();
                graphics.drawString(font, font.plainSubstrByWidth(label, row.width() - 12), row.x() + 8, row.y() + 2,
                        current ? ACCENT : TEXT, false);
                y += ENTRY_HEIGHT;
            }
            y += 4;
        }
        graphics.disableScissor();
        drawScrollbar(graphics, new Rect(area.right() - 3, area.y(), 3, area.height()), area.height(), total + 6,
                sidebarScroll);
    }

    private int sidebarHeight() {
        int height = 4;
        for (WikiLibrary.Group group : library.groups()) height += ENTRY_HEIGHT * (group.entries().size() + 1) + 4;
        return height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (HubTabs.mouseClicked(font, width, HubTabs.Tab.WIKI, mouseX, mouseY, button)) return true;
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        Rect panel = panel();
        boolean wide = wide(panel);
        if (closeButton(panel).contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (webButton(panel).contains(mouseX, mouseY)) {
            openWeb(WikiLibrary.SITE + "/" + (slug == null || slug.isEmpty() ? "" : slug + "/"));
            return true;
        }
        if (backButton(panel).contains(mouseX, mouseY) && !history.isEmpty()) {
            show(history.pop(), false);
            return true;
        }
        if (!wide && tocButton(panel).contains(mouseX, mouseY)) {
            sidebarOpen = !sidebarOpen;
            return true;
        }
        boolean showSidebar = wide || sidebarOpen;
        Rect sidebarArea = wide ? sidebar(panel) : content(panel);
        if (showSidebar && sidebarArea.contains(mouseX, mouseY)) {
            int y = sidebarArea.y() + 4 - sidebarScroll;
            for (WikiLibrary.Group group : library.groups()) {
                y += ENTRY_HEIGHT;
                for (WikiLibrary.Entry entry : group.entries()) {
                    if (mouseY >= y && mouseY < y + ENTRY_HEIGHT) {
                        if (entry.url() != null) openWeb(entry.url());
                        else show(entry.slug(), true);
                        sidebarOpen = false;
                        return true;
                    }
                    y += ENTRY_HEIGHT;
                }
                y += 4;
            }
            return true;
        }
        Rect content = content(panel);
        if (layout != null && content.contains(mouseX, mouseY)) {
            for (WikiLayout.Placed placed : layout.placed()) {
                int x = content.x() + placed.x();
                int y = content.y() + placed.y() - scroll;
                if (mouseY < y || mouseY >= y + placed.element().height()) continue;
                if (placed.element().click(font, x, y, mouseX, mouseY, this::follow)) return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Rect panel = panel();
        boolean wide = wide(panel);
        if ((wide || sidebarOpen) && (wide ? sidebar(panel) : content(panel)).contains(mouseX, mouseY)) {
            sidebarScroll = Math.max(0, sidebarScroll - (int) Math.round(scrollY * 26));
            return true;
        }
        scroll = Math.max(0, scroll - (int) Math.round(scrollY * 30));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Backspace goes back a page, as in a browser.
        if (keyCode == 259 && !history.isEmpty()) {
            show(history.pop(), false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Follows a link in a page: another page stays here, anything else opens in the browser. */
    private void follow(String target) {
        String resolved = WikiMarkdown.resolveLink(slug == null ? "" : slug, target);
        if (resolved != null && library.page(resolved).isPresent()) {
            if (!resolved.equals(slug)) show(resolved, true);
            return;
        }
        if (target.startsWith("http://") || target.startsWith("https://")) {
            openWeb(target);
        } else if (resolved != null) {
            openWeb(WikiLibrary.SITE + "/" + resolved + "/");
        }
    }

    private void show(String next, boolean remember) {
        if (remember && slug != null) history.push(slug);
        slug = next;
        layout = null;
        scroll = 0;
        report(false);
    }

    /** Opening, and each page shown after it, for the tutorial and analytics. */
    private void report(boolean opened) {
        if (minecraft == null || minecraft.getConnection() == null) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.ruskserver.moveearth_addtional.network.c2s.other.C2S_WikiOpenedPacket(opened,
                        opened ? source : "", opened ? item : "", slug == null ? "" : slug));
    }

    private void openWeb(String url) {
        ConfirmLinkScreen.confirmLinkNow(this, url);
    }
}
