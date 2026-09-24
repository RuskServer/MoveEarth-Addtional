package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.s2.S2HubTab;

import java.util.EnumMap;
import java.util.List;

/** Client-only information architecture layered over the stable S2 network tabs. */
final class S2HubNavigation {
    enum Section { HOME, DOMESTIC, DIPLOMACY, WAR, REGION }

    enum Page {
        HOME(Section.HOME),
        TERRITORY(Section.DOMESTIC), FINANCE(Section.DOMESTIC),
        MEMBERS(Section.DOMESTIC), ROLES(Section.DOMESTIC),
        RELATIONS(Section.DIPLOMACY), PEACE(Section.DIPLOMACY),
        SIEGES(Section.WAR), PRISONERS(Section.WAR), RECOVERY(Section.WAR),
        REGION(Section.REGION);

        private final Section section;

        Page(Section section) {
            this.section = section;
        }

        Section section() {
            return section;
        }
    }

    private static final EnumMap<Section, Page> DEFAULT_PAGES = new EnumMap<>(Section.class);
    private static final EnumMap<Section, List<Page>> PAGES = new EnumMap<>(Section.class);

    static {
        DEFAULT_PAGES.put(Section.HOME, Page.HOME);
        DEFAULT_PAGES.put(Section.DOMESTIC, Page.TERRITORY);
        DEFAULT_PAGES.put(Section.DIPLOMACY, Page.RELATIONS);
        DEFAULT_PAGES.put(Section.WAR, Page.SIEGES);
        DEFAULT_PAGES.put(Section.REGION, Page.REGION);
        for (Section section : Section.values()) {
            PAGES.put(section, List.of(Page.values()).stream()
                    .filter(page -> page.section() == section).toList());
        }
    }

    private final EnumMap<Section, Page> selectedPages = new EnumMap<>(DEFAULT_PAGES);
    private final EnumMap<Page, Integer> scrollOffsets = new EnumMap<>(Page.class);
    private Section section;

    S2HubNavigation(S2HubTab initialTab) {
        Page initial = fromNetworkTab(initialTab);
        section = initial.section();
        selectedPages.put(section, initial);
    }

    Section section() {
        return section;
    }

    Page page() {
        return selectedPages.get(section);
    }

    List<Page> pages() {
        return PAGES.get(section);
    }

    void selectSection(Section next) {
        section = next == null ? Section.HOME : next;
    }

    void selectPage(Page next) {
        if (next == null || next.section() != section) return;
        selectedPages.put(section, next);
    }

    int scrollOffset() {
        return scrollOffsets.getOrDefault(page(), 0);
    }

    void saveScrollOffset(int offset) {
        scrollOffsets.put(page(), Math.max(0, offset));
    }

    S2HubTab networkTab() {
        return switch (page()) {
            case HOME, TERRITORY, FINANCE -> S2HubTab.OVERVIEW;
            case MEMBERS -> S2HubTab.MEMBERS;
            case ROLES -> S2HubTab.ROLES;
            case RELATIONS -> S2HubTab.DIPLOMACY;
            case PEACE, SIEGES, PRISONERS, RECOVERY -> S2HubTab.SIEGE;
            case REGION -> S2HubTab.REGION;
        };
    }

    static Page fromNetworkTab(S2HubTab tab) {
        if (tab == null) return Page.HOME;
        return switch (tab) {
            case OVERVIEW -> Page.HOME;
            case MEMBERS -> Page.MEMBERS;
            case ROLES -> Page.ROLES;
            case DIPLOMACY -> Page.RELATIONS;
            case SIEGE -> Page.SIEGES;
            case REGION -> Page.REGION;
        };
    }
}
