package com.ruskserver.moveearth_addtional.client.wiki;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

/**
 * Lays a wiki page out for a given width: every block becomes an {@link Element} at a fixed offset, so
 * scrolling only shifts where they are drawn. Laid out again when the page or the width changes.
 */
final class WikiLayout {
    static final int LINK = 0xFF7FD4FF;
    static final int CODE = 0xFFE6B87A;
    private static final int GAP = 6;

    private final Font font;
    private final WikiLibrary library;
    private final List<Placed> placed = new ArrayList<>();
    private int height;

    record Placed(Element element, int x, int y) { }

    /** A laid-out piece of a page; {@code x} and {@code y} are where it is drawn this frame. */
    interface Element {
        int height();

        void render(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY);

        /** Handles a click inside the element; {@code open} follows a link. */
        default boolean click(Font font, int x, int y, double mouseX, double mouseY, Consumer<String> open) {
            return false;
        }
    }

    WikiLayout(Font font, WikiLibrary library, WikiMarkdown.Document document, int width) {
        this.font = font;
        this.library = library;
        int y = 0;
        if (!document.title().isBlank()) {
            y = add(new Lines(font.split(Component.literal(document.title()).withStyle(style -> style.withBold(true)),
                    width), 0, 0, TEXT, 12), 0, y, GAP - 2);
        }
        if (!document.description().isBlank()) {
            y = add(new Lines(font.split(Component.literal(document.description()), width), 0, 0, MUTED, 11),
                    0, y, GAP);
        }
        y = add(new Rule(width, BORDER), 0, y, GAP);
        y = blocks(document.blocks(), 0, y, width);
        this.height = y;
    }

    List<Placed> placed() {
        return placed;
    }

    int height() {
        return height;
    }

    private int add(Element element, int x, int y, int after) {
        placed.add(new Placed(element, x, y));
        return y + element.height() + after;
    }

    private int blocks(List<WikiMarkdown.Block> blocks, int x, int y, int width) {
        for (WikiMarkdown.Block block : blocks) y = block(block, x, y, width);
        return y;
    }

    private int block(WikiMarkdown.Block block, int x, int y, int width) {
        return switch (block) {
            case WikiMarkdown.Heading heading -> {
                int top = y + (heading.level() <= 2 ? 6 : 3);
                int color = heading.level() <= 2 ? ACCENT : heading.level() == 3 ? TEXT : MUTED;
                Component text = component(heading.text(), color).copy().withStyle(style -> style.withBold(true));
                int next = add(new Lines(font.split(text, width), 0, 0, color, 11), x, top, 2);
                yield heading.level() <= 2 ? add(new Rule(width, 0x553A4A5C), x, next, GAP) : next + GAP - 2;
            }
            case WikiMarkdown.Paragraph paragraph ->
                    add(new Lines(font.split(component(paragraph.text(), TEXT), width), 0, 0, TEXT, 11), x, y, GAP);
            case WikiMarkdown.ListBlock list -> {
                int next = y;
                for (WikiMarkdown.ListItem item : list.items()) {
                    int indent = 4 + item.depth() * 12;
                    String marker = item.ordered() ? item.marker() : item.depth() == 0 ? "•" : "◦";
                    int markerWidth = Math.max(10, font.width(marker) + 4);
                    List<FormattedCharSequence> lines = font.split(component(item.text(), TEXT),
                            Math.max(20, width - indent - markerWidth));
                    next = add(new Lines(lines, indent + markerWidth, indent, TEXT, 11, marker), x, next, 2);
                }
                yield next + GAP - 2;
            }
            case WikiMarkdown.Table table -> add(new TableElement(font, table, width), x, y, GAP);
            case WikiMarkdown.Quote quote -> boxed(quote.blocks(), null, MUTED, x, y, width);
            case WikiMarkdown.Aside aside -> {
                int color = switch (aside.kind()) {
                    case "tip" -> SUCCESS;
                    case "caution" -> GOLD;
                    case "danger" -> DANGER;
                    default -> ACCENT;
                };
                String title = !aside.title().isBlank() ? aside.title() : switch (aside.kind()) {
                    case "tip" -> "ヒント";
                    case "caution" -> "注意";
                    case "danger" -> "危険";
                    default -> "メモ";
                };
                yield boxed(aside.blocks(), title, color, x, y, width);
            }
            case WikiMarkdown.Rule rule -> add(new Rule(width, BORDER), x, y + 2, GAP + 2);
            case WikiMarkdown.Code code -> {
                List<FormattedCharSequence> lines = new ArrayList<>();
                for (String line : code.text().split("\n", -1)) {
                    lines.addAll(font.split(Component.literal(line.isEmpty() ? " " : line), Math.max(20, width - 16)));
                }
                yield add(new Panel(new Lines(lines, 0, 0, CODE, 10), width, 0xFF0E1318, BORDER), x, y, GAP);
            }
            case WikiMarkdown.Voxel voxel -> add(new WikiDiagrams.VoxelElement(font, voxel.scene(), width), x, y, GAP);
            case WikiMarkdown.Rounds rounds -> add(new WikiDiagrams.RoundsElement(font, rounds.chart(), width), x, y, GAP);
            case WikiMarkdown.Mermaid mermaid ->
                    add(new WikiDiagrams.MermaidElement(font, mermaid.key(), library.diagramSize(mermaid.key()), width),
                            x, y, GAP);
        };
    }

    /** A bordered box around nested blocks, with an optional title in its colour. */
    private int boxed(List<WikiMarkdown.Block> inner, String title, int color, int x, int y, int width) {
        int padding = 8;
        int start = placed.size();
        int innerY = y + padding + (title == null ? 0 : 13);
        int end = blocks(inner, x + padding + 3, innerY, width - padding * 2 - 3) - GAP + padding;
        List<Placed> children = new ArrayList<>(placed.subList(start, placed.size()));
        placed.subList(start, placed.size()).clear();
        placed.add(new Placed(new Box(width, end - y, color, title), x, y));
        placed.addAll(children);
        return end + GAP;
    }

    static Component component(List<WikiMarkdown.Span> spans, int baseColor) {
        MutableComponent out = Component.empty();
        for (WikiMarkdown.Span span : spans) {
            Style style = Style.EMPTY.withColor(baseColor & 0xFFFFFF);
            switch (span.style()) {
                case BOLD -> style = style.withBold(true);
                case ITALIC -> style = style.withItalic(true);
                case BOLD_ITALIC -> style = style.withBold(true).withItalic(true);
                case CODE -> style = style.withColor(CODE & 0xFFFFFF);
                default -> { }
            }
            if (span.link() != null) {
                style = style.withColor(LINK & 0xFFFFFF).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, span.link()));
            }
            out.append(Component.literal(span.text()).withStyle(style));
        }
        return out;
    }

    /** Wrapped lines of text, with an optional list marker hanging in the margin. */
    static final class Lines implements Element {
        private final List<FormattedCharSequence> lines;
        private final int indent;
        private final int markerX;
        private final int color;
        private final int lineHeight;
        private final String marker;

        Lines(List<FormattedCharSequence> lines, int indent, int markerX, int color, int lineHeight) {
            this(lines, indent, markerX, color, lineHeight, null);
        }

        Lines(List<FormattedCharSequence> lines, int indent, int markerX, int color, int lineHeight, String marker) {
            this.lines = lines;
            this.indent = indent;
            this.markerX = markerX;
            this.color = color;
            this.lineHeight = lineHeight;
            this.marker = marker;
        }

        @Override
        public int height() {
            return Math.max(1, lines.size()) * lineHeight;
        }

        @Override
        public void render(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
            if (marker != null) graphics.drawString(font, marker, x + markerX, y, MUTED, false);
            for (int i = 0; i < lines.size(); i++) {
                graphics.drawString(font, lines.get(i), x + indent, y + i * lineHeight, color, false);
            }
        }

        @Override
        public boolean click(Font font, int x, int y, double mouseX, double mouseY, Consumer<String> open) {
            int line = (int) Math.floor((mouseY - y) / lineHeight);
            if (line < 0 || line >= lines.size()) return false;
            Style style = font.getSplitter().componentStyleAtWidth(lines.get(line), (int) (mouseX - x - indent));
            if (style == null || style.getClickEvent() == null) return false;
            open.accept(style.getClickEvent().getValue());
            return true;
        }
    }

    static final class Rule implements Element {
        private final int width;
        private final int color;

        Rule(int width, int color) {
            this.width = width;
            this.color = color;
        }

        @Override
        public int height() {
            return 1;
        }

        @Override
        public void render(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
            graphics.fill(x, y, x + width, y + 1, color);
        }
    }

    /** The background of a {@link #boxed} block: drawn before its children. */
    static final class Box implements Element {
        private final int width;
        private final int height;
        private final int color;
        private final String title;

        Box(int width, int height, int color, String title) {
            this.width = width;
            this.height = height;
            this.color = color;
            this.title = title;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public void render(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
            graphics.fill(x, y, x + width, y + height, 0xC0151B22);
            graphics.fill(x, y, x + 3, y + height, color);
            drawBorder(graphics, new Rect(x, y, width, height), 0x803A4A5C);
            if (title != null) graphics.drawString(font, title, x + 11, y + 7, color, false);
        }
    }

    /** One element on a filled background, such as a code block. */
    static final class Panel implements Element {
        private static final int PADDING = 6;
        private final Element inner;
        private final int width;
        private final int fill;
        private final int border;

        Panel(Element inner, int width, int fill, int border) {
            this.inner = inner;
            this.width = width;
            this.fill = fill;
            this.border = border;
        }

        @Override
        public int height() {
            return inner.height() + PADDING * 2;
        }

        @Override
        public void render(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
            graphics.fill(x, y, x + width, y + height(), fill);
            drawBorder(graphics, new Rect(x, y, width, height()), border);
            inner.render(graphics, font, x + PADDING + 2, y + PADDING, mouseX, mouseY);
        }
    }

    /**
     * A table: columns share the width by how much they hold, every cell wraps, and the header row is
     * shaded. Links in cells work like links in text.
     */
    static final class TableElement implements Element {
        private static final int PAD = 4;
        private static final int LINE = 10;
        private final int[] columnX;
        private final int[] columnWidth;
        private final List<List<List<FormattedCharSequence>>> rows = new ArrayList<>();
        private final int[] rowHeight;
        private final int width;
        private final int height;

        TableElement(Font font, WikiMarkdown.Table table, int width) {
            List<List<List<WikiMarkdown.Span>>> source = new ArrayList<>();
            source.add(table.header());
            source.addAll(table.rows());
            int columns = source.stream().mapToInt(List::size).max().orElse(1);
            int[] natural = new int[columns];
            for (List<List<WikiMarkdown.Span>> row : source) {
                for (int c = 0; c < row.size(); c++) {
                    natural[c] = Math.max(natural[c], Math.min(220, font.width(component(row.get(c), TEXT)) + PAD * 2));
                }
            }
            int total = 0;
            for (int value : natural) total += Math.max(24, value);
            columnWidth = new int[columns];
            columnX = new int[columns];
            int used = 0;
            for (int c = 0; c < columns; c++) {
                int share = Math.max(24, natural[c]);
                columnWidth[c] = total <= width ? share : Math.max(24, share * width / total);
                columnX[c] = used;
                used += columnWidth[c];
            }
            if (used > width && columns > 0) columnWidth[columns - 1] = Math.max(24, columnWidth[columns - 1] - (used - width));
            this.width = Math.min(width, columnX[columns - 1] + columnWidth[columns - 1]);
            rowHeight = new int[source.size()];
            int y = 0;
            for (int r = 0; r < source.size(); r++) {
                List<List<FormattedCharSequence>> cells = new ArrayList<>();
                int lines = 1;
                for (int c = 0; c < columns; c++) {
                    List<WikiMarkdown.Span> cell = c < source.get(r).size() ? source.get(r).get(c) : List.of();
                    Component text = component(cell, r == 0 ? ACCENT : TEXT);
                    if (r == 0) text = text.copy().withStyle(style -> style.withBold(true));
                    List<FormattedCharSequence> wrapped = font.split(text, Math.max(8, columnWidth[c] - PAD * 2));
                    cells.add(wrapped);
                    lines = Math.max(lines, wrapped.size());
                }
                rows.add(cells);
                rowHeight[r] = lines * LINE + PAD * 2 - 2;
                y += rowHeight[r];
            }
            height = y;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public void render(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
            int rowY = y;
            for (int r = 0; r < rows.size(); r++) {
                graphics.fill(x, rowY, x + width, rowY + rowHeight[r], r == 0 ? 0xE0182230 : r % 2 == 1 ? 0x60151B22 : 0x30151B22);
                for (int c = 0; c < rows.get(r).size(); c++) {
                    List<FormattedCharSequence> lines = rows.get(r).get(c);
                    for (int i = 0; i < lines.size(); i++) {
                        graphics.drawString(font, lines.get(i), x + columnX[c] + PAD, rowY + PAD + i * LINE, TEXT, false);
                    }
                    if (c > 0) graphics.fill(x + columnX[c], rowY, x + columnX[c] + 1, rowY + rowHeight[r], 0x503A4A5C);
                }
                graphics.fill(x, rowY + rowHeight[r] - 1, x + width, rowY + rowHeight[r], 0x503A4A5C);
                rowY += rowHeight[r];
            }
            drawBorder(graphics, new Rect(x, y, width, height), 0x803A4A5C);
        }

        @Override
        public boolean click(Font font, int x, int y, double mouseX, double mouseY, Consumer<String> open) {
            int rowY = y;
            for (int r = 0; r < rows.size(); r++) {
                if (mouseY >= rowY && mouseY < rowY + rowHeight[r]) {
                    for (int c = 0; c < rows.get(r).size(); c++) {
                        if (mouseX < x + columnX[c] || mouseX >= x + columnX[c] + columnWidth[c]) continue;
                        int line = (int) Math.floor((mouseY - rowY - PAD) / LINE);
                        List<FormattedCharSequence> lines = rows.get(r).get(c);
                        if (line < 0 || line >= lines.size()) return false;
                        Style style = font.getSplitter().componentStyleAtWidth(lines.get(line),
                                (int) (mouseX - x - columnX[c] - PAD));
                        if (style == null || style.getClickEvent() == null) return false;
                        open.accept(style.getClickEvent().getValue());
                        return true;
                    }
                }
                rowY += rowHeight[r];
            }
            return false;
        }
    }
}
