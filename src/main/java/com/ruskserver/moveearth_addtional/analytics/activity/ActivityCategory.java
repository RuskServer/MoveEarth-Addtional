package com.ruskserver.moveearth_addtional.analytics.activity;

/**
 * プレイヤー分析における活動種別カテゴリ
 */
public enum ActivityCategory {
    /** 採掘・ブロック破壊 */
    MINING("mining", "採掘"),
    /** 建築・ブロック設置 */
    BUILDING("building", "建築"),
    /** 農業・収穫・繁殖 */
    FARMING("farming", "農業"),
    /** アイテムクラフト */
    CRAFTING("crafting", "クラフト"),
    /** Mobとの戦闘・討伐 */
    COMBAT_PVE("combat_pve", "PvE戦闘"),
    /** プレイヤー間戦闘 */
    COMBAT_PVP("combat_pvp", "PvP戦闘"),
    /** Jobs XP獲得 */
    JOBS("jobs", "Jobs活動"),
    /** 2ブロック以上の有効移動・探索 */
    MOVEMENT("movement", "移動"),
    /** TPAによるテレポート */
    TPA("tpa", "TPA移動"),
    /** その他有効活動 */
    OTHER("other", "その他");

    private final String id;
    private final String displayName;

    ActivityCategory(String id, String displayName) {
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
