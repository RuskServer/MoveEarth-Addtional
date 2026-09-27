package com.ruskserver.moveearth_addtional.chat;

/** Pure range and display rules for proximity chat. */
public final class LocalChatRules {
    private LocalChatRules() { }

    public static boolean canReceive(boolean sameDimension, double distanceSquared, int radiusBlocks) {
        return sameDimension && radiusBlocks > 0 && distanceSquared >= 0
                && distanceSquared <= (double) radiusBlocks * radiusBlocks;
    }

    /** How far the sender is from one recipient; the wording comes from the recipient's language file. */
    public record Distance(Kind kind, long blocks) {
        public enum Kind { SELF, UNDER_ONE_BLOCK, BLOCKS }
    }

    public static Distance distance(double distanceBlocks, boolean self) {
        if (self) return new Distance(Distance.Kind.SELF, 0L);
        if (distanceBlocks < 1.0D) return new Distance(Distance.Kind.UNDER_ONE_BLOCK, 0L);
        return new Distance(Distance.Kind.BLOCKS, Math.round(distanceBlocks));
    }
}
