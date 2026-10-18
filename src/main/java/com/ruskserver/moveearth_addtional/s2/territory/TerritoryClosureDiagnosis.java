package com.ruskserver.moveearth_addtional.s2.territory;

import com.ruskserver.moveearth_addtional.s2.territory.TerritoryClosureSearch.Cell;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Finds the holes in a core's enclosure, all of them at once. Minecraft-independent so it is unit tested.
 *
 * <p>The closure search ({@link TerritoryClosureSearch}) only proves that some way out exists, and the
 * way it reports runs on past the hole through the terrain outside to the edge of the search. Here each
 * way out is followed back to the one cell on it that is most like a gap in a wall, that cell is plugged
 * as if it were reinforced, and the search runs again until the core is sealed. Holes that turn out to be
 * unneeded once the rest are plugged are dropped, so what is left is a set that seals the core.
 *
 * <p>Where a way out has several candidates, the one most like a wall gap wins: a block that is not a
 * barrier set between barriers on opposite sides (unreinforced stone in a wall, a stair, a door), then
 * empty space so set (a missing block), then anything else; among equals, the one nearest the core, so the
 * inner shell of a layered fortress is reported rather than its outer walls. Each later search stops as
 * soon as it reaches ground already shown to lead out, so it only has to cover the inside again.
 */
final class TerritoryClosureDiagnosis {
    static final int MAX_HOLES = 16;
    /** Neighbour checks a whole diagnosis may spend before it reports what it has. */
    static final int MAX_WORK = 6_000_000;

    private static final byte UNKNOWN = 0;
    private static final byte BARRIER = 1;
    private static final byte EMPTY = 2;
    private static final byte BLOCK = 3;
    private static final byte UNLOADED = 4;
    private static final int[][] DIRECTIONS = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };

    interface View {
        boolean isLoaded(Cell cell);
        boolean isBarrier(Cell cell);
        /** Whether nothing occupies the cell: air, or a block one can walk through such as grass. */
        boolean isEmpty(Cell cell);
    }

    enum Status { SEALED, OPEN, UNLOADED }

    /**
     * @param holes     cells to fill or fix, in the order found
     * @param paths     for each hole, the way from the core to it
     * @param complete  false when the search stopped early and more holes may remain
     */
    record Result(Status status, List<Cell> holes, List<List<Cell>> paths, boolean complete) { }

    static Result diagnose(Cell core, View view, int range) {
        Session session = new Session(core, view, range);
        while (true) {
            Result result = session.advance(1 << 16);
            if (result != null) return result;
        }
    }

    static final class Session {
        private final Cell core;
        private final View view;
        private final int range;
        private final int size;
        private final byte[] kind;
        private final boolean[] plugged;
        private final int[] parent;
        private final int[] depth;
        private final int[] queue;
        /** For ground shown to lead out: which tail it lies on (plus one) and where on it. */
        private final int[] outsideTail;
        private final int[] outsideIndex;
        private final List<int[]> tails = new ArrayList<>();
        private final List<Integer> holes = new ArrayList<>();
        private final List<int[]> holePaths = new ArrayList<>();
        private int head;
        private int tail;
        private int work;
        private boolean searching;
        /** -1 while finding holes; otherwise the hole being checked for whether it is needed. */
        private int pruning = -1;
        /** Set once the hole limit is reached: one more search tells whether that was all of them. */
        private boolean full;
        private Result result;

        Session(Cell core, View view, int range) {
            this.core = core;
            this.view = view;
            this.range = Math.max(2, range);
            this.size = 2 * this.range + 1;
            int cells = size * size * size;
            kind = new byte[cells];
            plugged = new boolean[cells];
            parent = new int[cells];
            depth = new int[cells];
            queue = new int[cells];
            outsideTail = new int[cells];
            outsideIndex = new int[cells];
        }

        /** Runs up to {@code budget} neighbour checks; returns the result once done. */
        Result advance(int budget) {
            if (result != null) return result;
            int spent = 0;
            while (spent < budget && result == null) {
                if (!searching) startSearch();
                spent += step(budget - spent);
            }
            return result;
        }

        private void startSearch() {
            Arrays.fill(parent, -1);
            int start = index(0, 0, 0);
            parent[start] = start;
            depth[start] = 0;
            head = 0;
            tail = 0;
            queue[tail++] = start;
            searching = true;
        }

        /** Advances the current search; returns the checks spent. */
        private int step(int budget) {
            int spent = 0;
            while (spent < budget) {
                if (head >= tail) {
                    searching = false;
                    sealed();
                    return spent + 1;
                }
                int current = queue[head++];
                for (int[] d : DIRECTIONS) {
                    int next = neighbour(current, d);
                    spent++;
                    if (++work > MAX_WORK) {
                        finish(false);
                        return spent;
                    }
                    if (next < 0 || parent[next] >= 0 || plugged[next]) continue;
                    byte k = kind(next);
                    if (k == UNLOADED) {
                        result = new Result(Status.UNLOADED, List.of(), List.of(), false);
                        return spent;
                    }
                    if (k == BARRIER) continue;
                    parent[next] = current;
                    depth[next] = depth[current] + 1;
                    if (atEdge(next) || outsideTail[next] > 0) {
                        searching = false;
                        escaped(next);
                        return spent;
                    }
                    queue[tail++] = next;
                }
            }
            return spent;
        }

        /** The search ran out of space to explore: whatever is plugged now seals the core. */
        private void sealed() {
            if (pruning < 0) {
                if (holes.isEmpty()) {
                    result = new Result(Status.SEALED, List.of(), List.of(), true);
                    return;
                }
                pruning = 0;
            } else {
                // The hole under test is not needed: the core stays sealed without it.
                plugged[holes.get(pruning)] = false;
                holes.remove(pruning);
                holePaths.remove(pruning);
            }
            nextPrune();
        }

        private void escaped(int exit) {
            int[] inside = pathTo(exit);
            if (pruning >= 0) {
                // The hole under test is needed: put it back and test the next one.
                plugged[holes.get(pruning)] = true;
                pruning++;
                nextPrune();
                return;
            }
            if (full) {
                finish(false);
                return;
            }
            // The cell that reached the edge, or ground already known to lead out, is past the hole.
            int candidates = inside.length - 1;
            int best = -1;
            long bestRank = Long.MAX_VALUE;
            for (int i = 0; i < candidates; i++) {
                if (coreSpace(inside[i])) continue;
                long rank = rank(inside[i], i);
                if (rank < bestRank) {
                    bestRank = rank;
                    best = i;
                }
            }
            if (best < 0) best = Math.max(0, candidates - 1);
            int hole = inside[best];
            plugged[hole] = true;
            holes.add(hole);
            holePaths.add(Arrays.copyOf(inside, best + 1));

            // A plug on ground thought to lead out cuts that tail where it sits.
            if (outsideTail[hole] > 0) {
                int[] cut = tails.get(outsideTail[hole] - 1);
                for (int i = 0; i <= outsideIndex[hole]; i++) outsideTail[cut[i]] = 0;
            }
            int[] fullTail = stitchTail(inside, exit, best);
            int id = tails.size();
            tails.add(fullTail);
            for (int i = 0; i < fullTail.length; i++) {
                if (outsideTail[fullTail[i]] == 0) {
                    outsideTail[fullTail[i]] = id + 1;
                    outsideIndex[fullTail[i]] = i;
                }
            }
            full = holes.size() >= MAX_HOLES;
        }

        /** The way out past the plug: the rest of this search's path, then the tail it joined. */
        private int[] stitchTail(int[] inside, int exit, int plugAt) {
            int[] own = Arrays.copyOfRange(inside, plugAt + 1, inside.length);
            if (outsideTail[exit] == 0) return own;
            int[] joined = tails.get(outsideTail[exit] - 1);
            int from = outsideIndex[exit] + 1;
            int[] all = Arrays.copyOf(own, own.length + joined.length - from);
            System.arraycopy(joined, from, all, own.length, joined.length - from);
            return all;
        }

        private void nextPrune() {
            if (pruning >= holes.size()) {
                finish(true);
                return;
            }
            plugged[holes.get(pruning)] = false;
        }

        private void finish(boolean complete) {
            List<Cell> cells = new ArrayList<>(holes.size());
            List<List<Cell>> paths = new ArrayList<>(holes.size());
            for (int i = 0; i < holes.size(); i++) {
                cells.add(cell(holes.get(i)));
                List<Cell> path = new ArrayList<>();
                for (int step : holePaths.get(i)) path.add(cell(step));
                paths.add(List.copyOf(path));
            }
            result = new Result(holes.isEmpty() && complete ? Status.SEALED : Status.OPEN,
                    List.copyOf(cells), List.copyOf(paths), complete);
        }

        /**
         * Lower is the likelier hole. A gap in a wall wins, nearest the core first. A way out with no
         * such gap leaves through a wide opening: then the cell hugging the most walls and floor wins,
         * farthest out first, which is where the opening starts.
         */
        private long rank(int cell, int step) {
            int cost = cost(cell);
            if (cost <= 2) return (long) cost << 40 | step;
            return (long) cost << 40 | (long) (26 - closedAround(cell)) << 20 | (0xFFFFF - step);
        }

        private int closedAround(int cell) {
            int x = cell / (size * size);
            int y = cell / size % size;
            int z = cell % size;
            int closed = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if ((dx | dy | dz) == 0) continue;
                        int nx = x + dx, ny = y + dy, nz = z + dz;
                        if (nx < 0 || ny < 0 || nz < 0 || nx >= size || ny >= size || nz >= size) continue;
                        if (closed((nx * size + ny) * size + nz)) closed++;
                    }
                }
            }
            return closed;
        }

        /** The 3x3x3 space the core must keep open, from its own level up, never a hole. */
        private boolean coreSpace(int cell) {
            int dx = cell / (size * size) - range;
            int dy = cell / size % size - range;
            int dz = cell % size - range;
            return Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && dy >= 0 && dy <= 2;
        }

        /** 1 to 4; lower is more like a gap in a wall. */
        private int cost(int cell) {
            boolean wall = false;
            for (int axis = 0; axis < 3 && !wall; axis++) {
                wall = closed(neighbour(cell, DIRECTIONS[axis * 2]))
                        && closed(neighbour(cell, DIRECTIONS[axis * 2 + 1]));
            }
            boolean block = kind(cell) == BLOCK;
            return block ? (wall ? 1 : 3) : (wall ? 2 : 4);
        }

        private boolean closed(int cell) {
            return cell >= 0 && (plugged[cell] || kind(cell) == BARRIER);
        }

        private int[] pathTo(int end) {
            int[] path = new int[depth[end]];
            int start = index(0, 0, 0);
            int cursor = end;
            for (int i = path.length - 1; i >= 0 && cursor != start; i--) {
                path[i] = cursor;
                cursor = parent[cursor];
            }
            return path;
        }

        private byte kind(int cell) {
            byte k = kind[cell];
            if (k != UNKNOWN) return k;
            Cell at = cell(cell);
            if (!view.isLoaded(at)) k = UNLOADED;
            else if (view.isBarrier(at)) k = BARRIER;
            else k = view.isEmpty(at) ? EMPTY : BLOCK;
            kind[cell] = k;
            return k;
        }

        private boolean atEdge(int cell) {
            int dx = cell / (size * size) - range;
            int dy = cell / size % size - range;
            int dz = cell % size - range;
            return Math.abs(dx) >= range || Math.abs(dy) >= range || Math.abs(dz) >= range;
        }

        private int neighbour(int cell, int[] d) {
            int x = cell / (size * size) + d[0];
            int y = cell / size % size + d[1];
            int z = cell % size + d[2];
            if (x < 0 || y < 0 || z < 0 || x >= size || y >= size || z >= size) return -1;
            return (x * size + y) * size + z;
        }

        private int index(int dx, int dy, int dz) {
            return ((dx + range) * size + dy + range) * size + dz + range;
        }

        private Cell cell(int index) {
            return core.offset(index / (size * size) - range, index / size % size - range, index % size - range);
        }
    }
}
