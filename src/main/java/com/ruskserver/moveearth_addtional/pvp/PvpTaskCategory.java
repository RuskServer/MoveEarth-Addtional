package com.ruskserver.moveearth_addtional.pvp;

public enum PvpTaskCategory {
    DAILY("デイリー"),
    EVENT("イベント");

    private final String displayName;

    PvpTaskCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
