package com.ruskserver.moveearth_addtional.pvp;

public enum PvpTeam {
    RED(0xFFE54848), BLUE(0xFF4C7DFF);

    private final int color;

    PvpTeam(int color) {
        this.color = color;
    }

    public int color() {
        return color;
    }
}
