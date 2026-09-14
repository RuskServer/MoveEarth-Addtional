package com.ruskserver.moveearth_addtional.client.menu;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MenuChangelogTest {
    @Test
    void currentReleaseStopsBeforeOlderVersion() {
        List<String> result = MenuChangelog.currentRelease(List.of(
                "# v3.1", "", "## Added", "- **Menu**: New", "# v3.0", "old"));

        assertEquals(List.of("# v3.1", "", "## Added", "- **Menu**: New"), result);
    }

    @Test
    void displayTextRemovesBasicMarkdown() {
        assertEquals("• Menu: New", MenuChangelog.displayText("- **Menu**: `New`"));
    }
}
