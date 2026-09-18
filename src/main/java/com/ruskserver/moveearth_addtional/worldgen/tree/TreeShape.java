package com.ruskserver.moveearth_addtional.worldgen.tree;

import java.util.ArrayList;
import java.util.List;

/**
 * The geometry of a broad tree, with no Minecraft in it so it can be tested.
 *
 * <p>What makes a big tree read as real is not detail, it is proportion: a
 * trunk that is thick at the ground and thin at the crown, limbs that leave the
 * trunk well below the top, and a canopy several times wider than vanilla's.
 * Midgard (MIT) gets the last of the way there with a fence-shaped branch block
 * for twigs; this deliberately does not, so nothing has to ship to clients, and
 * the limbs are kept inside the canopy where a full block does not read as
 * chunky.
 */
public final class TreeShape {

    /** One block of a limb, relative to where the limb left the trunk. */
    public record Step(int x, int y, int z) { }

    private TreeShape() { }

    /**
     * Side of the trunk's square cross-section at a height, in blocks.
     *
     * <p>Quadratic in the height, so the trunk holds its girth for the lower
     * half and gives it up near the crown where the limbs take over. A linear
     * taper is a cone and reads as a carrot; a square root one is worse still,
     * because it sheds the girth in the first few blocks and the widest setting
     * then never appears above the roots at all.
     */
    public static int trunkGirth(int localY, int height, int baseGirth, int flare) {
        int girth = Math.max(1, baseGirth);
        if (localY < flare) {
            return girth + 1;
        }
        if (height <= 1) {
            return girth;
        }
        double t = Math.min(1.0, Math.max(0.0, localY / (double) (height - 1)));
        return Math.max(1, (int) Math.round(girth - (girth - 1) * t * t));
    }

    /**
     * Column offsets of a trunk of this girth, centred on the sapling.
     *
     * <p>Even girths cannot be centred on one block, so they straddle it; that
     * is what vanilla's 2x2 trees do and it keeps the trunk under the canopy.
     */
    public static List<int[]> girthOffsets(int girth) {
        List<int[]> out = new ArrayList<>();
        int low = -(girth - 1) / 2;
        for (int dx = low; dx < low + girth; dx++) {
            for (int dz = low; dz < low + girth; dz++) {
                out.add(new int[]{dx, dz});
            }
        }
        return out;
    }

    /**
     * A limb leaving the trunk, as connected blocks from its base outwards.
     *
     * <p>Stepped one block at a time along its longest axis rather than sampled,
     * because a sampled line leaves diagonal gaps and a limb full of holes looks
     * like a mistake rather than a branch.
     */
    public static List<Step> limb(double yaw, double rise, int length) {
        int endX = (int) Math.round(Math.cos(yaw) * length);
        int endZ = (int) Math.round(Math.sin(yaw) * length);
        int endY = (int) Math.round(rise * length);
        return line(endX, endY, endZ);
    }

    /** Connected blocks from the origin to (x, y, z), inclusive of both ends. */
    public static List<Step> line(int x, int y, int z) {
        int steps = Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.abs(z));
        List<Step> out = new ArrayList<>(steps + 1);
        if (steps == 0) {
            out.add(new Step(0, 0, 0));
            return out;
        }
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            out.add(new Step((int) Math.round(x * t), (int) Math.round(y * t), (int) Math.round(z * t)));
        }
        return out;
    }

    /**
     * Whether a position falls inside the canopy shell.
     *
     * <p>A flattened ellipsoid: wider than it is tall, and cut off below its
     * own centre so the underside is a dome rather than a sphere hanging in the
     * air. {@code lift} is how far above the attachment the widest part sits.
     */
    public static boolean inCanopy(int dx, int dy, int dz, int radius, int height, int lift) {
        if (radius <= 0 || height <= 0) {
            return false;
        }
        double h = (dy - lift) / (double) height;
        double r = Math.sqrt((double) dx * dx + (double) dz * dz) / radius;
        return r * r + h * h <= 1.0;
    }
}
