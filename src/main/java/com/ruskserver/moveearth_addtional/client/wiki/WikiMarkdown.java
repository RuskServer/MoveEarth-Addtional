package com.ruskserver.moveearth_addtional.client.wiki;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a wiki page's Markdown into blocks the in-game wiki can lay out.
 *
 * <p>Covers what the site's pages use: front matter, headings, paragraphs, nested lists, tables, block
 * quotes, Starlight asides ({@code :::note}), rules and fenced code, with the site's {@code voxel},
 * {@code rounds} and {@code mermaid} diagrams kept apart from plain code. Inline text keeps bold, italics,
 * code and links. MDX imports and component tags are dropped rather than shown as source. Pure, so it is
 * unit tested.
 */
public final class WikiMarkdown {
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*?)\\s*#*\\s*$");
    private static final Pattern LIST_ITEM = Pattern.compile("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)$");
    private static final Pattern FENCE = Pattern.compile("^\\s*(```+|~~~+)\\s*([A-Za-z0-9_-]*)\\s*$");
    private static final Pattern ASIDE_OPEN = Pattern.compile("^:::(note|tip|caution|danger)(?:\\[(.*)])?\\s*$");
    private static final Pattern RULE = Pattern.compile("^\\s*(-{3,}|\\*{3,}|_{3,})\\s*$");
    private static final Pattern TABLE_DIVIDER = Pattern.compile("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$");

    private WikiMarkdown() { }

    // ------------------------------------------------------------------ model

    public record Document(String title, String description, List<String> items, List<Block> blocks) { }

    public sealed interface Block permits Heading, Paragraph, ListBlock, Table, Quote, Aside, Rule, Code,
            Voxel, Rounds, Mermaid { }

    public record Heading(int level, List<Span> text) implements Block { }

    public record Paragraph(List<Span> text) implements Block { }

    public record ListItem(int depth, boolean ordered, String marker, List<Span> text) { }

    public record ListBlock(List<ListItem> items) implements Block { }

    public record Table(List<List<Span>> header, List<List<List<Span>>> rows) implements Block { }

    public record Quote(List<Block> blocks) implements Block { }

    /** A Starlight aside: {@code kind} is note, tip, caution or danger. */
    public record Aside(String kind, String title, List<Block> blocks) implements Block { }

    public record Rule() implements Block { }

    public record Code(String language, String text) implements Block { }

    public record Voxel(VoxelScene scene) implements Block { }

    public record Rounds(RoundsChart chart) implements Block { }

    /** {@code key} names the diagram's PNG; see {@link #mermaidKey(String)}. */
    public record Mermaid(String key, String source) implements Block { }

    public enum Style { PLAIN, BOLD, ITALIC, BOLD_ITALIC, CODE }

    /** A run of text; {@code link} is the raw target of a Markdown link, or null. */
    public record Span(String text, Style style, String link) { }

    // ------------------------------------------------------------------ parsing

    public static Document parse(String markdown) {
        List<String> lines = new ArrayList<>(List.of(markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)));
        String title = "";
        String description = "";
        List<String> items = new ArrayList<>();
        if (!lines.isEmpty() && lines.getFirst().trim().equals("---")) {
            int end = -1;
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).trim().equals("---")) {
                    end = i;
                    break;
                }
            }
            if (end > 0) {
                FrontMatter front = frontMatter(lines.subList(1, end));
                title = front.title();
                description = front.description();
                items.addAll(front.items());
                lines = new ArrayList<>(lines.subList(end + 1, lines.size()));
            }
        }
        return new Document(title, description, List.copyOf(items), blocks(lines));
    }

    private record FrontMatter(String title, String description, List<String> items) { }

    private static FrontMatter frontMatter(List<String> lines) {
        String title = "";
        String description = "";
        List<String> items = new ArrayList<>();
        boolean inItems = false;
        for (String line : lines) {
            if (inItems) {
                Matcher entry = Pattern.compile("^\\s+-\\s*(.+?)\\s*$").matcher(line);
                if (entry.matches()) {
                    items.add(unquote(entry.group(1)));
                    continue;
                }
                inItems = false;
            }
            int colon = line.indexOf(':');
            if (colon <= 0 || Character.isWhitespace(line.charAt(0))) continue;
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            switch (key) {
                case "title" -> title = unquote(value);
                case "description" -> description = unquote(value);
                case "items" -> {
                    if (value.isEmpty()) {
                        inItems = true;
                    } else if (value.startsWith("[") && value.endsWith("]")) {
                        for (String part : value.substring(1, value.length() - 1).split(",")) {
                            String item = unquote(part.trim());
                            if (!item.isEmpty()) items.add(item);
                        }
                    }
                }
                default -> { }
            }
        }
        return new FrontMatter(title, description, items);
    }

    private static String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && (trimmed.startsWith("\"") && trimmed.endsWith("\"")
                || trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static List<Block> blocks(List<String> lines) {
        List<Block> blocks = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                i++;
                continue;
            }
            Matcher fence = FENCE.matcher(line);
            if (fence.matches()) {
                String marker = fence.group(1);
                String language = fence.group(2).toLowerCase(java.util.Locale.ROOT);
                List<String> body = new ArrayList<>();
                i++;
                while (i < lines.size() && !lines.get(i).trim().startsWith(marker.substring(0, 3))) {
                    body.add(lines.get(i));
                    i++;
                }
                i++;
                blocks.add(code(language, body));
                continue;
            }
            Matcher aside = ASIDE_OPEN.matcher(trimmed);
            if (aside.matches()) {
                List<String> body = new ArrayList<>();
                i++;
                int depth = 1;
                while (i < lines.size()) {
                    String inner = lines.get(i).trim();
                    if (ASIDE_OPEN.matcher(inner).matches()) depth++;
                    if (inner.equals(":::") && --depth == 0) break;
                    body.add(lines.get(i));
                    i++;
                }
                i++;
                blocks.add(new Aside(aside.group(1), aside.group(2) == null ? "" : aside.group(2).trim(), blocks(body)));
                continue;
            }
            if (trimmed.startsWith("import ") || trimmed.startsWith("export ")
                    || trimmed.startsWith("<") && !trimmed.startsWith("<br")) {
                i++;
                continue;
            }
            Matcher heading = HEADING.matcher(trimmed);
            if (heading.matches()) {
                blocks.add(new Heading(heading.group(1).length(), inline(heading.group(2))));
                i++;
                continue;
            }
            if (RULE.matcher(line).matches()) {
                blocks.add(new Rule());
                i++;
                continue;
            }
            if (trimmed.startsWith(">")) {
                List<String> body = new ArrayList<>();
                while (i < lines.size() && lines.get(i).trim().startsWith(">")) {
                    body.add(lines.get(i).trim().substring(1).replaceFirst("^ ", ""));
                    i++;
                }
                blocks.add(new Quote(blocks(body)));
                continue;
            }
            if (trimmed.startsWith("|") && i + 1 < lines.size() && TABLE_DIVIDER.matcher(lines.get(i + 1)).matches()) {
                List<List<Span>> header = cells(trimmed);
                List<List<List<Span>>> rows = new ArrayList<>();
                i += 2;
                while (i < lines.size() && lines.get(i).trim().startsWith("|")) {
                    rows.add(cells(lines.get(i).trim()));
                    i++;
                }
                blocks.add(new Table(header, rows));
                continue;
            }
            if (LIST_ITEM.matcher(line).matches()) {
                List<ListItem> items = new ArrayList<>();
                while (i < lines.size()) {
                    Matcher item = LIST_ITEM.matcher(lines.get(i));
                    if (item.matches()) {
                        String marker = item.group(2);
                        items.add(new ListItem(indent(item.group(1)), Character.isDigit(marker.charAt(0)),
                                marker, inline(item.group(3).trim())));
                        i++;
                        continue;
                    }
                    String next = lines.get(i);
                    // A wrapped line of the item above continues it.
                    if (!next.trim().isEmpty() && Character.isWhitespace(next.charAt(0)) && !items.isEmpty()
                            && !FENCE.matcher(next).matches()) {
                        ListItem last = items.removeLast();
                        List<Span> joined = new ArrayList<>(last.text());
                        joined.add(new Span(" ", Style.PLAIN, null));
                        joined.addAll(inline(next.trim()));
                        items.add(new ListItem(last.depth(), last.ordered(), last.marker(), joined));
                        i++;
                        continue;
                    }
                    break;
                }
                blocks.add(new ListBlock(items));
                continue;
            }
            StringBuilder paragraph = new StringBuilder(trimmed);
            i++;
            while (i < lines.size()) {
                String next = lines.get(i);
                String nextTrimmed = next.trim();
                if (nextTrimmed.isEmpty() || HEADING.matcher(nextTrimmed).matches() || FENCE.matcher(next).matches()
                        || LIST_ITEM.matcher(next).matches() || nextTrimmed.startsWith(">")
                        || nextTrimmed.startsWith("|") || nextTrimmed.startsWith(":::")
                        || RULE.matcher(next).matches()) break;
                // Lines of one paragraph are joined without a space: Japanese text wraps mid-sentence.
                paragraph.append(endsAscii(paragraph) && startsAscii(nextTrimmed) ? " " : "").append(nextTrimmed);
                i++;
            }
            blocks.add(new Paragraph(inline(paragraph.toString())));
        }
        return List.copyOf(blocks);
    }

    private static boolean endsAscii(CharSequence text) {
        return text.length() > 0 && text.charAt(text.length() - 1) < 0x80;
    }

    private static boolean startsAscii(String text) {
        return !text.isEmpty() && text.charAt(0) < 0x80;
    }

    private static int indent(String whitespace) {
        int columns = 0;
        for (char c : whitespace.toCharArray()) columns += c == '\t' ? 4 : 1;
        return columns / 2;
    }

    private static Block code(String language, List<String> body) {
        String text = String.join("\n", body);
        return switch (language) {
            case "voxel" -> new Voxel(VoxelScene.parse(text));
            case "rounds" -> new Rounds(RoundsChart.parse(text));
            case "mermaid" -> {
                String source = text.replaceAll("\\s+$", "");
                yield new Mermaid(mermaidKey(source), source);
            }
            default -> new Code(language, text);
        };
    }

    private static List<List<Span>> cells(String row) {
        String inner = row.trim();
        if (inner.startsWith("|")) inner = inner.substring(1);
        if (inner.endsWith("|")) inner = inner.substring(0, inner.length() - 1);
        List<List<Span>> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean code = false;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '`') code = !code;
            if (c == '\\' && i + 1 < inner.length() && inner.charAt(i + 1) == '|') {
                cell.append('|');
                i++;
                continue;
            }
            if (c == '|' && !code) {
                cells.add(inline(cell.toString().trim()));
                cell.setLength(0);
                continue;
            }
            cell.append(c);
        }
        cells.add(inline(cell.toString().trim()));
        return cells;
    }

    /** Inline Markdown: {@code **bold**}, {@code *italic*}, {@code `code`}, {@code [text](link)}, {@code <br>}. */
    public static List<Span> inline(String text) {
        List<Span> spans = new ArrayList<>();
        inline(text, Style.PLAIN, null, spans);
        return merge(spans);
    }

    private static void inline(String text, Style style, String link, List<Span> out) {
        StringBuilder plain = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                plain.append(text.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '`') {
                int end = text.indexOf('`', i + 1);
                if (end > i) {
                    flush(plain, style, link, out);
                    out.add(new Span(text.substring(i + 1, end), Style.CODE, link));
                    i = end + 1;
                    continue;
                }
            }
            if (text.startsWith("**", i) || text.startsWith("__", i)) {
                String marker = text.substring(i, i + 2);
                int end = text.indexOf(marker, i + 2);
                if (end > i + 2) {
                    flush(plain, style, link, out);
                    inline(text.substring(i + 2, end), bold(style), link, out);
                    i = end + 2;
                    continue;
                }
            }
            if ((c == '*' || c == '_') && i + 1 < text.length() && text.charAt(i + 1) != ' ') {
                int end = text.indexOf(c, i + 1);
                boolean wordUnderscore = c == '_' && i > 0 && Character.isLetterOrDigit(text.charAt(i - 1));
                if (end > i + 1 && !wordUnderscore) {
                    flush(plain, style, link, out);
                    inline(text.substring(i + 1, end), italic(style), link, out);
                    i = end + 1;
                    continue;
                }
            }
            if (c == '[') {
                int close = matching(text, i, '[', ']');
                if (close > i && close + 1 < text.length() && text.charAt(close + 1) == '(') {
                    int end = text.indexOf(')', close + 2);
                    if (end > close) {
                        flush(plain, style, link, out);
                        String target = text.substring(close + 2, end).trim();
                        int space = target.indexOf(' ');
                        if (space > 0) target = target.substring(0, space);
                        inline(text.substring(i + 1, close), style, target, out);
                        i = end + 1;
                        continue;
                    }
                }
            }
            if (text.startsWith("<br", i)) {
                int end = text.indexOf('>', i);
                if (end > i) {
                    plain.append('\n');
                    i = end + 1;
                    continue;
                }
            }
            plain.append(c);
            i++;
        }
        flush(plain, style, link, out);
    }

    private static int matching(String text, int open, char opening, char closing) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            if (text.charAt(i) == opening) depth++;
            else if (text.charAt(i) == closing && --depth == 0) return i;
        }
        return -1;
    }

    private static Style bold(Style style) {
        return style == Style.ITALIC || style == Style.BOLD_ITALIC ? Style.BOLD_ITALIC : Style.BOLD;
    }

    private static Style italic(Style style) {
        return style == Style.BOLD || style == Style.BOLD_ITALIC ? Style.BOLD_ITALIC : Style.ITALIC;
    }

    private static void flush(StringBuilder plain, Style style, String link, List<Span> out) {
        if (plain.isEmpty()) return;
        out.add(new Span(plain.toString(), style, link));
        plain.setLength(0);
    }

    private static List<Span> merge(List<Span> spans) {
        List<Span> merged = new ArrayList<>();
        for (Span span : spans) {
            if (!merged.isEmpty()) {
                Span last = merged.getLast();
                if (last.style() == span.style() && java.util.Objects.equals(last.link(), span.link())) {
                    merged.set(merged.size() - 1, new Span(last.text() + span.text(), span.style(), span.link()));
                    continue;
                }
            }
            merged.add(span);
        }
        return List.copyOf(merged);
    }

    /**
     * The key a Mermaid block's PNG is published under: the first 16 hex digits of the SHA-256 of its
     * source with trailing whitespace removed. Must match moveearth-web's src/lib/mermaid/blocks.mjs.
     */
    public static String mermaidKey(String source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.replaceAll("\\s+$", "").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * Resolves a link on page {@code fromSlug} to a wiki slug, as the site would: {@code /guide/x/},
     * {@code ../x/}, {@code ./x/#part} and the site's own base path all lead to pages. Returns null for
     * anything that leaves the wiki.
     */
    public static String resolveLink(String fromSlug, String target) {
        if (target == null || target.isBlank()) return null;
        String link = target.trim();
        if (link.startsWith("http://") || link.startsWith("https://")) {
            String site = "https://rusklabo.github.io/moveearth-web";
            if (!link.startsWith(site)) return null;
            link = link.substring(site.length());
        }
        if (link.startsWith("mailto:")) return null;
        int hash = link.indexOf('#');
        if (hash >= 0) link = link.substring(0, hash);
        int query = link.indexOf('?');
        if (query >= 0) link = link.substring(0, query);
        if (link.isEmpty()) return fromSlug;
        List<String> path = new ArrayList<>();
        if (!link.startsWith("/")) {
            // Pages are served as directories (/guide/siege/), so a relative link starts from the page itself.
            for (String part : fromSlug.split("/")) if (!part.isEmpty()) path.add(part);
        }
        for (String part : link.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (!path.isEmpty()) path.removeLast();
                continue;
            }
            path.add(part);
        }
        if (!path.isEmpty() && path.getFirst().equals("moveearth-web")) path.removeFirst();
        if (!path.isEmpty()) {
            String last = path.getLast();
            if (last.endsWith(".md") || last.endsWith(".mdx")) path.set(path.size() - 1, last.replaceFirst("\\.mdx?$", ""));
            if (path.getLast().equals("index")) path.removeLast();
        }
        return String.join("/", path);
    }
}
