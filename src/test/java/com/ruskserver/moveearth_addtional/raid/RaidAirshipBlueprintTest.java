package com.ruskserver.moveearth_addtional.raid;

import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class RaidAirshipBlueprintTest {
    @Test void positionsAreUniqueAndWithinActualBounds() {
        var blocks = RaidAirshipBlueprint.blocks();
        assertEquals(blocks.size(), blocks.stream().map(RaidAirshipBlueprint.Block::position).distinct().count());
        var b = RaidAirshipBlueprint.bounds();
        for (var block : blocks) {
            var p = block.position();
            assertTrue(p.x() >= b.minX() && p.x() <= b.maxX());
            assertTrue(p.y() >= b.minY() && p.y() <= b.maxY());
            assertTrue(p.z() >= b.minZ() && p.z() <= b.maxZ());
        }
        assertTrue(blocks.size() < 4000, "Keep raid assembly cost bounded");
    }

    @Test void existingRewardAndDestructionCountsArePreserved() {
        assertEquals(6, count(RaidAirshipBlueprint.Part.CORE));
        assertEquals(2, count(RaidAirshipBlueprint.Part.BARREL));
        assertEquals(2, count(RaidAirshipBlueprint.Part.CANNON));
    }

    @Test void broadsideMachinesFaceOutwardAndAllBlocksAreConnected() {
        var blocks = RaidAirshipBlueprint.blocks();
        for (var block : blocks) {
            if (block.part() == RaidAirshipBlueprint.Part.CANNON || block.part() == RaidAirshipBlueprint.Part.PROPELLER) {
                assertEquals(block.position().z() < 0 ? RaidAirshipBlueprint.Facing.NORTH
                        : RaidAirshipBlueprint.Facing.SOUTH, block.facing());
            }
        }
        var positions = blocks.stream().map(RaidAirshipBlueprint.Block::position).collect(Collectors.toSet());
        Set<RaidAirshipBlueprint.Position> visited = new HashSet<>();
        var queue = new ArrayDeque<RaidAirshipBlueprint.Position>();
        queue.add(new RaidAirshipBlueprint.Position(0, 0, 0));
        while (!queue.isEmpty()) {
            var p = queue.removeFirst();
            if (!positions.contains(p) || !visited.add(p)) continue;
            for (int[] d : new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}}) {
                queue.add(new RaidAirshipBlueprint.Position(p.x()+d[0], p.y()+d[1], p.z()+d[2]));
            }
        }
        assertEquals(positions.size(), visited.size(), () -> "Disconnected: " + blocks.stream()
                .filter(b -> !visited.contains(b.position())).limit(30).toList());
    }

    @Test void balloonIsClosedAgainstAxisAlignedFloodFill() {
        var positions = RaidAirshipBlueprint.blocks().stream().map(RaidAirshipBlueprint.Block::position)
                .collect(Collectors.toSet());
        var queue = new ArrayDeque<RaidAirshipBlueprint.Position>();
        Set<RaidAirshipBlueprint.Position> visited = new HashSet<>();
        queue.add(new RaidAirshipBlueprint.Position(0, 12, 0));
        while (!queue.isEmpty()) {
            var p = queue.removeFirst();
            if (positions.contains(p) || !visited.add(p)) continue;
            assertTrue(Math.abs(p.x()) <= 20 && p.y() >= 6 && p.y() <= 18 && Math.abs(p.z()) <= 6,
                    "Balloon has a hole at " + p);
            for (int[] d : new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}}) {
                queue.add(new RaidAirshipBlueprint.Position(p.x()+d[0], p.y()+d[1], p.z()+d[2]));
            }
        }
        assertTrue(visited.size() > 1000);
    }

    private static long count(RaidAirshipBlueprint.Part part) {
        return RaidAirshipBlueprint.blocks().stream().filter(b -> b.part() == part).count();
    }
}
