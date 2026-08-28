package com.ruskserver.moveearth_addtional.analytics.group;

/**
 * ある地点・拠点範囲に対するプレイヤーの立場・関係性
 */
public enum GroupRelation {
    /** 自身が所属する検知グループの拠点領域内 */
    MEMBER("member", "自領域"),
    /** 自身が所属していない他グループの拠点領域内 */
    OUTSIDER("outsider", "他領域"),
    /** どの検知グループの拠点範囲にも含まれない荒野 */
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
}
