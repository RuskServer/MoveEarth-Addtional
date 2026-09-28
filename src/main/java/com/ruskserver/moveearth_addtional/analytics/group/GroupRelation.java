package com.ruskserver.moveearth_addtional.analytics.group;

import java.util.UUID;

/**
 * Where a player stands relative to Season 2 territory. The stored name keeps the
 * Season 1 ids so older rows still read back; MEMBER now means the player's own
 * nation and OUTSIDER a foreign one.
 */
public enum GroupRelation {
    /** Territory of the player's own nation. */
    MEMBER("member", "自国領"),
    /** Territory of a nation allied with the player's. */
    ALLIED("allied", "同盟国領"),
    /** Territory of any other nation, including when the player has none. */
    OUTSIDER("outsider", "他国領"),
    /** Land no nation controls. */
    WILDERNESS("wilderness", "荒野");

    private final String id;
    private final String displayName;

    GroupRelation(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * @param territoryNation nation controlling the spot, or null for wilderness
     * @param playerNation    the player's nation, or null
     * @param allied          whether the two nations are allied
     */
    public static GroupRelation of(UUID territoryNation, UUID playerNation, boolean allied) {
        if (territoryNation == null) return WILDERNESS;
        if (territoryNation.equals(playerNation)) return MEMBER;
        return playerNation != null && allied ? ALLIED : OUTSIDER;
    }
}
