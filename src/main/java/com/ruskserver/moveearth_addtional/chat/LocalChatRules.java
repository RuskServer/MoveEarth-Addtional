package com.ruskserver.moveearth_addtional.chat;

/** Pure range and display rules for proximity chat. */
public final class LocalChatRules {
    private LocalChatRules() { }

    public static boolean canReceive(boolean sameDimension, double distanceSquared, int radiusBlocks) {
        return sameDimension && radiusBlocks > 0 && distanceSquared >= 0
                && distanceSquared <= (double) radiusBlocks * radiusBlocks;
    }

    public static String distanceLabel(double distanceBlocks, boolean self) {
        if (self) return "自分";
        if (distanceBlocks < 1.0D) return "1ブロック未満";
        return Math.round(distanceBlocks) + "ブロック先";
    }
}
