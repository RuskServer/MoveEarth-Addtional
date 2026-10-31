package com.ruskserver.moveearth_addtional.client.wiki;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@code rounds} chart: how many shots of one damage each material takes. A port of moveearth-web's
 * src/lib/chart/parse.js. Pure, so it is unit tested.
 */
public record RoundsChart(String title, String caption, int damage, List<Row> rows) {
    private static final Pattern HEAD = Pattern.compile("^\\s*(title|damage|caption)\\s*:\\s*(.*)$");
    private static final Pattern ROW = Pattern.compile("^\\s*(.+?)\\s*=\\s*(\\d+)\\s*$");

    public record Row(String label, int health) { }

    /** Shots needed, and how much of the last one is used. */
    public record Shots(int shots, int remainder) { }

    public static RoundsChart parse(String source) {
        String title = "";
        String caption = "";
        int damage = 0;
        List<Row> rows = new ArrayList<>();
        for (String raw : source.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            Matcher head = HEAD.matcher(line);
            if (head.matches()) {
                switch (head.group(1)) {
                    case "title" -> title = head.group(2).trim();
                    case "caption" -> caption = head.group(2).trim();
                    default -> damage = parseInt(head.group(2).trim());
                }
                continue;
            }
            Matcher row = ROW.matcher(line);
            if (row.matches()) rows.add(new Row(row.group(1), parseInt(row.group(2))));
        }
        return new RoundsChart(title, caption, damage, List.copyOf(rows));
    }

    public static Shots shotsFor(int health, int damage) {
        if (damage <= 0 || health <= 0) return new Shots(0, 0);
        int shots = (health + damage - 1) / damage;
        return new Shots(shots, health - (shots - 1) * damage);
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }
}
