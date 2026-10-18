package com.ruskserver.moveearth_addtional.s2.siege;

/** Which broken blocks on another nation's land count as an attack. Pure, so it is tested without a level. */
public final class TerritoryBreakPolicy {
    private TerritoryBreakPolicy() {
    }

    /**
     * Anything that takes a moment to break counts. Grass, flowers, crops, snow and whatever a placed block
     * would simply replace do not, so walking through someone's land and brushing the grass is not a war.
     */
    public static boolean counts(float destroySpeed, boolean replaceable) {
        return destroySpeed > 0.0F && !replaceable;
    }
}
