package com.ruskserver.moveearth_addtional.warehouse;

/**
 * What a player is shown about a Warehouse: the building's footprint, inside which
 * a guard squad is called out, drawn as a force field, and a warning just
 * before they step in. Without it, a newcomer walking into the building found
 * out only by being shot. Pure, shared by the server and the client.
 */
public final class WarehouseZoneView {
    /** Players this close to the footprint, but not inside it, are warned. */
    public static final int WARNING_MARGIN = 8;

    public enum State {
        /** Entering calls out the guards. */
        ARMED(0xFF3030),
        /** A fight is under way. */
        FIGHTING(0xFF9A1F),
        /** Entering does nothing: closed hours, loot, or cooldown. */
        QUIET(0x20A0FF);

        private final int color;

        State(int color) { this.color = color; }

        public int color() { return color; }
    }

    private WarehouseZoneView() { }

    /**
     * @param dormant  no fight, loot or cooldown is in progress
     * @param fighting the guards are out
     */
    public static State state(boolean dormant, boolean fighting, boolean serverOpen) {
        if (fighting) return State.FIGHTING;
        return dormant && serverOpen ? State.ARMED : State.QUIET;
    }

    /** About to walk in: near the footprint at roughly building height, but not yet inside. */
    public static boolean approaching(int minX, int minY, int minZ, int x, int y, int z) {
        return WarehouseSitePolicy.within(minX, minZ, x, z, WARNING_MARGIN)
                && y >= (long) minY - WarehouseSitePolicy.FIGHT_VERTICAL_MARGIN
                && y < (long) minY + WarehouseSitePolicy.HEIGHT + WarehouseSitePolicy.FIGHT_VERTICAL_MARGIN
                && !WarehouseSitePolicy.insideStructure(minX, minY, minZ, x, y, z);
    }

    /**
     * Force field opacity, 0 to 1, for a viewer {@code distance} blocks from the
     * footprint's edge: solid inside and up close, gone past {@code fadeDistance}.
     */
    public static float fieldAlpha(double distance, double fadeDistance) {
        if (distance <= 0.0D) return 1.0F;
        if (distance >= fadeDistance) return 0.0F;
        double closeness = 1.0D - distance / fadeDistance;
        return (float) (closeness * closeness);
    }

    /** Horizontal distance from (x, z) to the footprint; 0 inside it. */
    public static double distanceToFootprint(int minX, int minZ, double x, double z) {
        double maxX = (double) minX + WarehouseSitePolicy.WIDTH;
        double maxZ = (double) minZ + WarehouseSitePolicy.LENGTH;
        double dx = Math.max(Math.max(minX - x, 0.0D), x - maxX);
        double dz = Math.max(Math.max(minZ - z, 0.0D), z - maxZ);
        return Math.sqrt(dx * dx + dz * dz);
    }
}
