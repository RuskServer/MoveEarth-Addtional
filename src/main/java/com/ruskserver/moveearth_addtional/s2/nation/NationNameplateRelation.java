package com.ruskserver.moveearth_addtional.s2.nation;

/** Viewer-relative nation relationship used only for nameplate presentation. */
public enum NationNameplateRelation {
    OWN_NATION,
    ALLY,
    HOSTILE,
    NEUTRAL;

    public static NationNameplateRelation resolve(boolean sameNation, boolean allied, boolean hostile) {
        if (sameNation) return OWN_NATION;
        if (allied) return ALLY;
        if (hostile) return HOSTILE;
        return NEUTRAL;
    }

    public static NationNameplateRelation fromNetworkId(int id) {
        return id >= 0 && id < values().length ? values()[id] : NEUTRAL;
    }
}
