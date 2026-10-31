package com.ruskserver.moveearth_addtional.client.wiki;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@code voxel} diagram: layers of characters, bottom first, and a legend giving each character a colour
 * and a name. A port of moveearth-web's src/lib/voxel/parse.js, so a diagram reads the same in the game as
 * on the site. Pure, so it is unit tested.
 */
public record VoxelScene(String title, String caption, List<Kind> legend, List<Block> blocks) {
    private static final Pattern LEGEND_LINE =
            Pattern.compile("^\\s*(\\S)\\s*=\\s*([^#]*?)\\s*(#[0-9a-fA-F]{3,8})?\\s*(ghost|cutaway)?\\s*$");
    private static final Pattern HEAD = Pattern.compile("^\\s*(title|caption|legend|layers)\\s*:\\s*(.*)$");
    private static final Pattern TRAILING = Pattern.compile("^\\s*(title|caption)\\s*:\\s*(.*)$");

    /**
     * {@code ghost} blocks are drawn see-through because they are gone (a hole); {@code cutaway} ones are
     * there but drawn see-through to look past them, and can be hidden with a switch.
     */
    public record Kind(String label, int rgb, boolean ghost, boolean cutaway) { }

    public record Block(int x, int y, int z, Kind kind) { }

    public static VoxelScene parse(String source) {
        Map<Character, Kind> legend = new LinkedHashMap<>();
        List<List<String>> layers = new ArrayList<>();
        String title = "";
        String caption = "";
        String section = "head";
        List<String> current = null;
        for (String raw : source.split("\n", -1)) {
            String line = raw.replaceAll("\\s+$", "");
            if (line.isBlank() && !section.equals("layers")) continue;
            if (!section.equals("layers")) {
                Matcher head = HEAD.matcher(line);
                if (head.matches()) {
                    switch (head.group(1)) {
                        case "title" -> title = head.group(2).trim();
                        case "caption" -> caption = head.group(2).trim();
                        case "legend" -> section = "legend";
                        default -> {
                            section = "layers";
                            current = new ArrayList<>();
                        }
                    }
                    continue;
                }
                if (section.equals("legend")) {
                    Matcher entry = LEGEND_LINE.matcher(line);
                    if (entry.matches()) {
                        String modifier = entry.group(4);
                        legend.put(entry.group(1).charAt(0), new Kind(entry.group(2).trim(),
                                color(entry.group(3) == null ? "#4e5a48" : entry.group(3)),
                                "ghost".equals(modifier) || "cutaway".equals(modifier), "cutaway".equals(modifier)));
                    }
                }
                continue;
            }
            Matcher trailing = TRAILING.matcher(line);
            if (trailing.matches()) {
                if (trailing.group(1).equals("title")) title = trailing.group(2).trim();
                else caption = trailing.group(2).trim();
                continue;
            }
            if (line.matches("^\\s*---+\\s*$")) {
                if (current != null && !current.isEmpty()) layers.add(current);
                current = new ArrayList<>();
                continue;
            }
            if (line.isBlank()) continue;
            if (current != null) current.add(line.replaceFirst("^\\s\\s?", ""));
        }
        if (current != null && !current.isEmpty()) layers.add(current);

        List<Block> blocks = new ArrayList<>();
        for (int y = 0; y < layers.size(); y++) {
            List<String> rows = layers.get(y);
            for (int z = 0; z < rows.size(); z++) {
                int[] chars = rows.get(z).codePoints().toArray();
                for (int x = 0; x < chars.length; x++) {
                    if (chars[x] > 0xFFFF) continue;
                    Kind kind = legend.get((char) chars[x]);
                    if (kind != null) blocks.add(new Block(x, y, z, kind));
                }
            }
        }
        return new VoxelScene(title, caption, List.copyOf(legend.values()), List.copyOf(blocks));
    }

    /** {@code #rgb}, {@code #rrggbb} or longer: the first six digits after expanding the short form. */
    static int color(String hex) {
        String value = hex.replace("#", "");
        if (value.length() == 3) {
            StringBuilder full = new StringBuilder();
            for (char c : value.toCharArray()) full.append(c).append(c);
            value = full.toString();
        }
        return Integer.parseInt(value.substring(0, 6), 16);
    }

    public boolean hasCutaway() {
        return blocks.stream().anyMatch(block -> block.kind().cutaway());
    }
}
