package com.ruskserver.moveearth_addtional.s2;

public enum S2HubTab {
    OVERVIEW(0),
    MEMBERS(1),
    ROLES(2),
    DIPLOMACY(3),
    SIEGE(4),
    REGION(5);

    private final int networkId;

    S2HubTab(int networkId) {
        this.networkId = networkId;
    }

    public int networkId() {
        return networkId;
    }

    public static S2HubTab fromNetworkId(int id) {
        for (S2HubTab tab : values()) {
            if (tab.networkId == id) return tab;
        }
        return OVERVIEW;
    }
}
