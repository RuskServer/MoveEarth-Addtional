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
        Session session = new Session(core, world, range, maxVisited, maxPath);
        while (true) {
            Progress progress = session.advance(4_096);
            if (progress.result() != null) return progress.result();
        }
    }

    /** Mutable flood-fill state which can be resumed with a bounded amount of work each tick. */
    static final class Session {
        private final Cell core;
        private final WorldView world;
        private final int range;
        private final int maxVisited;
        private final int maxPath;
        private final ArrayDeque<Cell> queue = new ArrayDeque<>();
        private final Set<Cell> visited = new HashSet<>();
        private final Map<Cell, Cell> parents = new HashMap<>();
        private int interiorIndex;
        private boolean interiorValidated;
        private Cell current;
        private int directionIndex;
        private Result result;

        Session(Cell core, WorldView world, int range, int maxVisited, int maxPath) {
            this.core = core;
            this.world = world;
            this.range = Math.max(2, range);
            this.maxVisited = Math.max(1, maxVisited);
            this.maxPath = Math.max(1, maxPath);
        }

        Progress advance(int cellBudget) {
            if (result != null) return new Progress(result, 0);
            int budget = Math.max(1, cellBudget);
            int examined = 0;

            while (!interiorValidated && examined < budget) {
                if (interiorIndex >= 27) {
                    interiorValidated = true;
                    queue.add(core);
                    visited.add(core);
                    break;
                }
                int index = interiorIndex++;
                int x = index / 9 - 1;
                int y = index / 3 % 3;
                int z = index % 3 - 1;
                if (x == 0 && y == 0 && z == 0) continue;
                Cell interior = core.offset(x, y, z);
                examined++;
                if (!world.isLoaded(interior)) {
                    result = result(Status.UNLOADED, 0, interior);
                    return new Progress(result, examined);
                }
                if (world.isBarrier(interior)) {
                    result = result(Status.TOO_SMALL, 0, interior);
                    return new Progress(result, examined);
                }
            }
            if (!interiorValidated) return new Progress(null, examined);

            while (examined < budget) {
                if (current == null) {
                    current = queue.pollFirst();
                    directionIndex = 0;
                    if (current == null) {
                        result = new Result(Status.SEALED, visited.size(), List.of());
                        return new Progress(result, examined);
                    }
                    if (atSearchEdge(core, current, range)) {
                        result = new Result(Status.OPEN, visited.size(),
                                path(core, current, parents, maxPath));
                        return new Progress(result, examined);
                    }
                }
                while (directionIndex < DIRECTIONS.length && examined < budget) {
                    int[] direction = DIRECTIONS[directionIndex++];
                    Cell next = current.offset(direction[0], direction[1], direction[2]);
                    examined++;
                    if (visited.contains(next)) continue;
                    parents.put(next, current);
                    if (!world.isLoaded(next)) {
                        result = new Result(Status.UNLOADED, visited.size(),
                                path(core, next, parents, maxPath));
                        return new Progress(result, examined);
                    }
                    if (world.isBarrier(next)) {
                        parents.remove(next);
                        continue;
                    }
                    visited.add(next);
                    if (visited.size() > maxVisited) {
                        result = new Result(Status.LIMIT_EXCEEDED, visited.size(),
                                path(core, next, parents, maxPath));
                        return new Progress(result, examined);
                    }
                    queue.addLast(next);
                }
                if (directionIndex >= DIRECTIONS.length) current = null;
            }
            return new Progress(null, examined);
        }
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

    record Progress(Result result, int examinedCells) {
        boolean complete() { return result != null; }
    }

    interface WorldView {
        boolean isLoaded(Cell cell);
        boolean isBarrier(Cell cell);
    }
}
