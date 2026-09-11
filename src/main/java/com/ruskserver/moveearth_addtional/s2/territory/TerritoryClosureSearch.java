package com.ruskserver.moveearth_addtional.s2.territory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Minecraft-independent bounded flood fill, kept separately so closure rules can be unit tested. */
final class TerritoryClosureSearch {
    private static final int[][] DIRECTIONS = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1},
            {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };

    private TerritoryClosureSearch() {
    }

    static Result scan(Cell core, WorldView world, int range, int maxVisited, int maxPath) {
        int safeRange = Math.max(2, range);
        int safeLimit = Math.max(1, maxVisited);
        // A core is a floor-mounted block: require a 3x3x3 room from its base upward,
        // while allowing the reinforced support floor directly below that room.
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    Cell interior = core.offset(x, y, z);
                    if (!world.isLoaded(interior)) return result(Status.UNLOADED, 0, interior);
                    if (world.isBarrier(interior)) return result(Status.TOO_SMALL, 0, interior);
                }
            }
        }

        ArrayDeque<Cell> queue = new ArrayDeque<>();
        Set<Cell> visited = new HashSet<>();
        Map<Cell, Cell> parents = new HashMap<>();
        queue.add(core);
        visited.add(core);
        while (!queue.isEmpty()) {
            Cell current = queue.removeFirst();
            if (atSearchEdge(core, current, safeRange)) {
                return new Result(Status.OPEN, visited.size(), path(core, current, parents, maxPath));
            }
            for (int[] direction : DIRECTIONS) {
                Cell next = current.offset(direction[0], direction[1], direction[2]);
                if (visited.contains(next)) continue;
                parents.put(next, current);
                if (!world.isLoaded(next)) {
                    return new Result(Status.UNLOADED, visited.size(), path(core, next, parents, maxPath));
                }
                if (world.isBarrier(next)) {
                    parents.remove(next);
                    continue;
                }
                visited.add(next);
                if (visited.size() > safeLimit) {
                    return new Result(Status.LIMIT_EXCEEDED, visited.size(), path(core, next, parents, maxPath));
                }
                queue.addLast(next);
            }
        }
        return new Result(Status.SEALED, visited.size(), List.of());
    }

    private static Result result(Status status, int visited, Cell cell) {
        return new Result(status, visited, List.of(cell));
    }

    private static boolean atSearchEdge(Cell core, Cell cell, int range) {
        return Math.abs(cell.x - core.x) >= range || Math.abs(cell.y - core.y) >= range
                || Math.abs(cell.z - core.z) >= range;
    }

    private static List<Cell> path(Cell core, Cell end, Map<Cell, Cell> parents, int maxPath) {
        List<Cell> reversed = new ArrayList<>();
        Cell cursor = end;
        while (!cursor.equals(core) && reversed.size() < maxPath) {
            reversed.add(cursor);
            Cell parent = parents.get(cursor);
            if (parent == null) break;
            cursor = parent;
        }
        java.util.Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    enum Status { SEALED, OPEN, TOO_SMALL, LIMIT_EXCEEDED, UNLOADED }

    record Cell(int x, int y, int z) {
        Cell offset(int dx, int dy, int dz) {
            return new Cell(x + dx, y + dy, z + dz);
        }
    }

    record Result(Status status, int visited, List<Cell> escapePath) { }

    interface WorldView {
        boolean isLoaded(Cell cell);
        boolean isBarrier(Cell cell);
    }
}
