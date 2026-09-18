package com.ruskserver.moveearth_addtional.worldgen.tree;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The proportions a big tree has to hold to read as a tree.
 *
 * <p>These are the things that are wrong when a generated tree looks like a
 * prop: a trunk that keeps its full girth to the top, a limb with gaps in it, a
 * canopy that is a sphere. Each has its own test.
 */
class TreeShapeTest {

    @Test
    void trunkKeepsMostOfItsGirthLowAndEndsThin() {
        int height = 16;
        assertEquals(3, TreeShape.trunkGirth(0, height, 3, 0));
        assertEquals(1, TreeShape.trunkGirth(height - 1, height, 3, 0));
        // The widest setting has to actually appear on the trunk, not only in the
        // roots -- a taper that sheds it in the first few blocks is why this is
        // asserted at full girth rather than merely "thick".
        assertEquals(3, TreeShape.trunkGirth(height / 4, height, 3, 0),
                "a girth of three should still be three a quarter of the way up");
        assertEquals(2, TreeShape.trunkGirth(height * 3 / 4, height, 3, 0),
                "and should have given up one block of it by three quarters");
    }

    @Test
    void trunkNeverGrowsBackAboveTheFlare() {
        int height = 20;
        int previous = Integer.MAX_VALUE;
        for (int y = 1; y < height; y++) {
            int girth = TreeShape.trunkGirth(y, height, 3, 1);
            assertTrue(girth <= previous, "girth grew again at y=" + y);
            previous = girth;
        }
    }

    @Test
    void theFlareIsWiderThanTheTrunkAndOnlyAtTheBase() {
        assertEquals(4, TreeShape.trunkGirth(0, 16, 3, 2));
        assertEquals(4, TreeShape.trunkGirth(1, 16, 3, 2));
        assertTrue(TreeShape.trunkGirth(2, 16, 3, 2) <= 3);
    }

    @Test
    void girthOffsetsCoverASquareAroundTheSapling() {
        assertEquals(List.of(0, 0), List.of(TreeShape.girthOffsets(1).get(0)[0],
                TreeShape.girthOffsets(1).get(0)[1]));
        assertEquals(4, TreeShape.girthOffsets(2).size());
        assertEquals(9, TreeShape.girthOffsets(3).size());
        boolean centred = TreeShape.girthOffsets(3).stream().anyMatch(o -> o[0] == 0 && o[1] == 0);
        assertTrue(centred, "an odd girth has to sit on the sapling");
    }

    @Test
    void aLimbHasNoGapsInIt() {
        for (int degrees = 0; degrees < 360; degrees += 17) {
            List<TreeShape.Step> steps = TreeShape.limb(Math.toRadians(degrees), 0.5, 7);
            TreeShape.Step previous = null;
            for (TreeShape.Step step : steps) {
                if (previous != null) {
                    int dx = Math.abs(step.x() - previous.x());
                    int dy = Math.abs(step.y() - previous.y());
                    int dz = Math.abs(step.z() - previous.z());
                    assertTrue(dx <= 1 && dy <= 1 && dz <= 1,
                            "gap at " + degrees + " degrees: " + previous + " -> " + step);
                    assertTrue(dx + dy + dz > 0, "repeated block at " + degrees + " degrees");
                }
                previous = step;
            }
        }
    }

    @Test
    void aLimbLeavesTheTrunkAndClimbsAsItGoes() {
        List<TreeShape.Step> steps = TreeShape.limb(0.0, 0.5, 8);
        TreeShape.Step tip = steps.get(steps.size() - 1);
        assertEquals(0, steps.get(0).x());
        assertTrue(tip.x() >= 7, "limb fell short: " + tip);
        assertTrue(tip.y() > 0, "limb should rise, not stick out level: " + tip);
        assertTrue(tip.y() < tip.x(), "a limb that climbs faster than it spreads is a second trunk");
    }

    @Test
    void theCanopyIsWiderThanItIsTall() {
        int radius = 6;
        int height = 4;
        assertTrue(TreeShape.inCanopy(0, 1, 0, radius, height, 1));
        assertTrue(TreeShape.inCanopy(radius - 1, 1, 0, radius, height, 1), "should reach out to its radius");
        assertFalse(TreeShape.inCanopy(radius + 1, 1, 0, radius, height, 1));
        assertFalse(TreeShape.inCanopy(0, 1 + height + 1, 0, radius, height, 1), "should not reach up to its radius");
    }

    @Test
    void theCanopyEnclosesItsAttachmentRatherThanSittingOnTop() {
        // leaves below the limb tip, or the mass looks like a hat on a pole
        assertTrue(TreeShape.inCanopy(0, -1, 0, 5, 4, 1));
    }

    /** Printed for eyeballing; the assertions above are what actually hold. */
    @Test
    void silhouette() {
        int height = 15;
        int girth = 3;
        int flare = 1;
        int radius = 6;
        int foliage = 4;
        int lift = 1;
        StringBuilder out = new StringBuilder("\n");
        for (int y = height + foliage + lift; y >= 0; y--) {
            StringBuilder row = new StringBuilder();
            for (int x = -radius - 1; x <= radius + 1; x++) {
                int side = y < height ? TreeShape.trunkGirth(y, height, girth, flare) : 0;
                int low = -(side - 1) / 2;
                boolean trunk = side > 0 && x >= low && x < low + side;
                boolean leaf = TreeShape.inCanopy(x, y - height, 0, radius, foliage, lift);
                row.append(trunk ? '#' : leaf ? '*' : ' ');
            }
            out.append(row.toString().stripTrailing()).append('\n');
        }
        System.out.println(out);
        assertTrue(out.indexOf("#") > 0);
    }
}
