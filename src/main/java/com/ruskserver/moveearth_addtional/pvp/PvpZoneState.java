package com.ruskserver.moveearth_addtional.pvp;

/** Stable wire-level state of the KoTH capture zone. */
public enum PvpZoneState {
    NEUTRAL(0, "overlay.moveearth_addtional.pvp.hardpoint.neutral"),
    RED(1, "overlay.moveearth_addtional.pvp.hardpoint.red"),
    BLUE(2, "overlay.moveearth_addtional.pvp.hardpoint.blue"),
    CONTESTED(3, "overlay.moveearth_addtional.pvp.hardpoint.contested");

    private final int networkId;
    private final String translationKey;

    PvpZoneState(int networkId, String translationKey) {
        this.networkId = networkId;
        this.translationKey = translationKey;
    }

    public int networkId() {
        return networkId;
    }

    public String translationKey() {
        return translationKey;
    }

    public static PvpZoneState fromCounts(int redPlayers, int bluePlayers) {
        if (redPlayers > 0 && bluePlayers > 0) return CONTESTED;
        if (redPlayers > 0) return RED;
        if (bluePlayers > 0) return BLUE;
        return NEUTRAL;
    }

    public static PvpZoneState byNetworkId(int id) {
        for (PvpZoneState state : values()) {
            if (state.networkId == id) return state;
        }
        return NEUTRAL;
    }
}
