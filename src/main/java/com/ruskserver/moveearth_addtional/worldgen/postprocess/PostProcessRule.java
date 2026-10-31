package com.ruskserver.moveearth_addtional.worldgen.postprocess;

/** The decision behind {@link PostProcessCheck}, free of Minecraft so it is unit tested. */
public final class PostProcessRule {
    static final String FULL = "minecraft:full";

    private PostProcessRule() { }

    /**
     * @param status        the saved chunk's generation status
     * @param sectionSizes  how many blocks each section has waiting for post-processing
     */
    public static boolean needed(String status, int[] sectionSizes) {
        return FULL.equals(status) && anyWaiting(sectionSizes);
    }

    public static boolean anyWaiting(int[] sectionSizes) {
        if (sectionSizes == null) return false;
        for (int size : sectionSizes) {
            if (size > 0) return true;
        }
        return false;
    }
}
