package com.ruskserver.moveearth_addtional.territory.overlay;

public final class TerritoryOverlayCell {
    public static final int RELATION_NONE = 0;
    public static final int RELATION_FRIENDLY = 1;
    public static final int RELATION_HOSTILE = 2;
    public static final int RELATION_CONTESTED = 3;

    private static final int STRENGTH_MASK = 0xFFFF;
    private static final int RELATION_SHIFT = 16;
    private static final int TIER_SHIFT = 18;
    private static final double STRENGTH_SCALE = 16.0D;

    private TerritoryOverlayCell() {
    }

    public static int pack(int relation, int protectionTier, double influence) {
        if (relation < RELATION_NONE || relation > RELATION_CONTESTED) {
            throw new IllegalArgumentException("invalid relation");
        }
        if (protectionTier < 0 || protectionTier > 7) {
            throw new IllegalArgumentException("invalid protection tier");
        }
        int strength = !Double.isFinite(influence) || influence <= 0.0D
                ? 0
                : (int) Math.min(STRENGTH_MASK, Math.round(influence * STRENGTH_SCALE));
        return strength | (relation << RELATION_SHIFT) | (protectionTier << TIER_SHIFT);
    }

    public static int relation(int packed) {
        return (packed >>> RELATION_SHIFT) & 0x3;
    }

    public static int protectionTier(int packed) {
        return (packed >>> TIER_SHIFT) & 0x7;
    }

    public static double influence(int packed) {
        return (packed & STRENGTH_MASK) / STRENGTH_SCALE;
    }
}
