package com.ruskserver.moveearth_addtional.client.menu;

import java.util.ArrayList;
import java.util.List;

final class MenuChangelog {
    private MenuChangelog() {
    }

    static List<String> currentRelease(List<String> lines) {
        List<String> result = new ArrayList<>();
        boolean foundHeading = false;
        for (String line : lines) {
            if (line.startsWith("# ")) {
                if (foundHeading) break;
                foundHeading = true;
            }
            if (foundHeading) result.add(line);
        }
        return result.isEmpty() ? List.copyOf(lines) : List.copyOf(result);
    }

    static String displayText(String markdown) {
        String text = markdown.strip();
        if (text.startsWith("### ")) text = text.substring(4);
        else if (text.startsWith("## ")) text = text.substring(3);
        else if (text.startsWith("# ")) text = text.substring(2);
        else if (text.startsWith("- ")) text = "• " + text.substring(2);
        return text.replace("**", "").replace("`", "");
    }
}
