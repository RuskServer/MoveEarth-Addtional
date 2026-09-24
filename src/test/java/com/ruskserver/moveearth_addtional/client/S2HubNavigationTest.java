package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class S2HubNavigationTest {
    @Test
    void preservesStableNetworkTabsBehindNewPages() {
        S2HubNavigation navigation = new S2HubNavigation(S2HubTab.OVERVIEW);
        navigation.selectSection(S2HubNavigation.Section.DOMESTIC);
        navigation.selectPage(S2HubNavigation.Page.ROLES);
        assertEquals(S2HubTab.ROLES, navigation.networkTab());
        navigation.selectSection(S2HubNavigation.Section.WAR);
        navigation.selectPage(S2HubNavigation.Page.PRISONERS);
        assertEquals(S2HubTab.SIEGE, navigation.networkTab());
    }

    @Test
    void remembersPageAndScrollPerSection() {
        S2HubNavigation navigation = new S2HubNavigation(S2HubTab.OVERVIEW);
        navigation.selectSection(S2HubNavigation.Section.DOMESTIC);
        navigation.selectPage(S2HubNavigation.Page.MEMBERS);
        navigation.saveScrollOffset(84);
        navigation.selectSection(S2HubNavigation.Section.WAR);
        navigation.selectSection(S2HubNavigation.Section.DOMESTIC);
        assertEquals(S2HubNavigation.Page.MEMBERS, navigation.page());
        assertEquals(84, navigation.scrollOffset());
    }
}
