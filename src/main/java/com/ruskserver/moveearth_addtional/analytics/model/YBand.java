package com.ruskserver.moveearth_addtional.analytics.model;

/**
 * 空間分析におけるY座標の高度帯区分
 */
public enum YBand {
    /** 地下深部 (y < -16) */
    DEEP_UNDERGROUND("deep_underground", "地下深部"),
    /** 地下 (-16 <= y < 62) */
    UNDERGROUND("underground", "地下"),
    /** 地表 (62 <= y < 192) */
    SURFACE("surface", "地表"),
    /** 高高度 (y >= 192) */
    HIGH_ALTITUDE("high_altitude", "高高度");

    private final String id;
    private final String displayName;

    YBand(String id, String displayName) {
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
     * Y座標値から該当する高度帯を判定
     */
    public static YBand fromY(double y) {
        if (y < -16.0D) {
            return DEEP_UNDERGROUND;
        } else if (y < 62.0D) {
            return UNDERGROUND;
        } else if (y < 192.0D) {
            return SURFACE;
        } else {
            return HIGH_ALTITUDE;
        }
    }
}
