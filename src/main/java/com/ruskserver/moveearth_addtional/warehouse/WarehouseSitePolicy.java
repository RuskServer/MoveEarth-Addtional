package com.ruskserver.moveearth_addtional.warehouse;

/** Pure geometry shared by placement, claims and block protection. */
public final class WarehouseSitePolicy {
    public static final int WIDTH = 37;
    public static final int HEIGHT = 11;
    public static final int LENGTH = 17;
    public static final int BUILD_MARGIN = 64;
    public static final int CLAIM_MARGIN = 128;
    /**
     * Attackers must stand this close to the building for their damage to count. Inside the raiders'
     * 32-block firing range, so nobody can shoot them from where they cannot answer; the 48-block
     * leash otherwise let a player kill the captain from ~80 blocks away untouched.
     */
    public static final int FIGHT_MARGIN = 24;
    public static final int FIGHT_VERTICAL_MARGIN = 16;

    private WarehouseSitePolicy() { }

    public static boolean within(int minX, int minZ, int x, int z, int margin) {
        return x >= (long) minX - margin && x <= (long) minX + WIDTH - 1 + margin
                && z >= (long) minZ - margin && z <= (long) minZ + LENGTH - 1 + margin;
    }

    public static boolean insideFightZone(int minX, int minY, int minZ, int x, int y, int z) {
        return within(minX, minZ, x, z, FIGHT_MARGIN)
                && y >= (long) minY - FIGHT_VERTICAL_MARGIN && y < (long) minY + HEIGHT + FIGHT_VERTICAL_MARGIN;
    }

    public static boolean insideStructure(int minX, int minY, int minZ, int x, int y, int z) {
        return within(minX, minZ, x, z, 0)
                && y >= minY && y < (long) minY + HEIGHT;
    }

    public static boolean intersectsClaim(int minX, int minZ, int claimMinX,
                                          int claimMaxXExclusive, int claimMinZ, int claimMaxZExclusive) {
        return (long) claimMinX <= (long) minX + WIDTH - 1 + CLAIM_MARGIN
                && (long) claimMaxXExclusive > (long) minX - CLAIM_MARGIN
                && (long) claimMinZ <= (long) minZ + LENGTH - 1 + CLAIM_MARGIN
                && (long) claimMaxZExclusive > (long) minZ - CLAIM_MARGIN;
    }

    public static boolean overlaps(int firstX, int firstZ, int secondX, int secondZ) {
        return (long) firstX - BUILD_MARGIN <= (long) secondX + WIDTH - 1 + BUILD_MARGIN
                && (long) firstX + WIDTH - 1 + BUILD_MARGIN >= (long) secondX - BUILD_MARGIN
                && (long) firstZ - BUILD_MARGIN <= (long) secondZ + LENGTH - 1 + BUILD_MARGIN
                && (long) firstZ + LENGTH - 1 + BUILD_MARGIN >= (long) secondZ - BUILD_MARGIN;
    }
}
