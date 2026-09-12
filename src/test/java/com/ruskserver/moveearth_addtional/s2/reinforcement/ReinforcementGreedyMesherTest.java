package com.ruskserver.moveearth_addtional.s2.reinforcement;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReinforcementGreedyMesherTest {
    @Test
    void solidWallCollapsesItsFrontAndBackIntoTwoQuads() {
        List<ReinforcementGreedyMesher.Cell> cells = java.util.stream.IntStream.range(0, 4)
                .boxed().flatMap(x -> java.util.stream.IntStream.range(0, 3)
                        .mapToObj(y -> new ReinforcementGreedyMesher.Cell(x, y, 0, 7L))).toList();
        Set<Point> occupied = occupied(cells);
        var quads = ReinforcementGreedyMesher.mesh(cells,
                (x, y, z) -> occupied.contains(new Point(x, y, z)));

        assertEquals(1, quads.stream().filter(q -> q.face() == ReinforcementGreedyMesher.Face.NORTH).count());
        assertEquals(1, quads.stream().filter(q -> q.face() == ReinforcementGreedyMesher.Face.SOUTH).count());
        assertEquals(4 * 3 * 2, quads.stream()
                .filter(q -> q.face() == ReinforcementGreedyMesher.Face.NORTH
                        || q.face() == ReinforcementGreedyMesher.Face.SOUTH)
                .mapToInt(ReinforcementGreedyMesherTest::area).sum());
    }

    @Test
    void differentStatesNeverMerge() {
        List<ReinforcementGreedyMesher.Cell> cells = List.of(
                new ReinforcementGreedyMesher.Cell(0, 0, 0, 1L),
                new ReinforcementGreedyMesher.Cell(1, 0, 0, 2L));
        Set<Point> occupied = occupied(cells);
        var north = ReinforcementGreedyMesher.mesh(cells,
                        (x, y, z) -> occupied.contains(new Point(x, y, z))).stream()
                .filter(q -> q.face() == ReinforcementGreedyMesher.Face.NORTH).toList();

        assertEquals(2, north.size());
    }

    @Test
    void adjacentCellsDoNotEmitTheirInternalFaces() {
        List<ReinforcementGreedyMesher.Cell> cells = List.of(
                new ReinforcementGreedyMesher.Cell(0, 0, 0, 1L),
                new ReinforcementGreedyMesher.Cell(1, 0, 0, 1L));
        Set<Point> occupied = occupied(cells);
        var quads = ReinforcementGreedyMesher.mesh(cells,
                (x, y, z) -> occupied.contains(new Point(x, y, z)));

        assertEquals(6, quads.size());
        assertEquals(10, quads.stream().mapToInt(ReinforcementGreedyMesherTest::area).sum());
    }

    @Test
    void solidChunkCollapsesToSixRectangles() {
        List<ReinforcementGreedyMesher.Cell> cells = java.util.stream.IntStream.range(0, 16)
                .boxed().flatMap(x -> java.util.stream.IntStream.range(0, 16).boxed()
                        .flatMap(y -> java.util.stream.IntStream.range(0, 16)
                                .mapToObj(z -> new ReinforcementGreedyMesher.Cell(x, y, z, 3L))))
                .toList();
        Set<Point> occupied = occupied(cells);

        assertEquals(6, ReinforcementGreedyMesher.mesh(cells,
                (x, y, z) -> occupied.contains(new Point(x, y, z))).size());
    }

    private static int area(ReinforcementGreedyMesher.Quad quad) {
        return switch (quad.face()) {
            case NORTH, SOUTH -> quad.sizeX() * quad.sizeY();
            case WEST, EAST -> quad.sizeZ() * quad.sizeY();
            case DOWN, UP -> quad.sizeX() * quad.sizeZ();
        };
    }

    private static Set<Point> occupied(List<ReinforcementGreedyMesher.Cell> cells) {
        Set<Point> result = new HashSet<>();
        cells.forEach(cell -> result.add(new Point(cell.x(), cell.y(), cell.z())));
        return result;
    }

    private record Point(int x, int y, int z) { }
}
