package com.ruskserver.moveearth_addtional.s2.territory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * How many layers of wall stand between a core and the outside at its thinnest point: the fewest closure
 * barriers anyone must break to reach the edge of the closure search from the core. The closure check
 * only says whether a wall is whole, so a nation with a single skin of wall learned that one breach would
 * expose its core only once it happened.
 *
 * <p>A 0-1 breadth-first search over a box around the core: open cells cost nothing, barrier cells cost
 * one, and reaching the box's side, top or bottom is getting out. The box can be wider than the closure
 * search's, so an outer town wall counts. Counting stops at the cap the caller asks for (at most
 * {@link #CAP}), so a fortress that already has enough layers costs no more than proving it. The arrays are
 * allocated on the first call to {@link Session#advance}, so waiting sessions hold no memory.
 * Minecraft-independent and resumable a bounded amount of work at a time.
 */
final class TerritoryWallLayerSearch {
    /** Layers are counted exactly below this; at or above it the result only says "at least". */
    static final int CAP = 4;
    private static final int[][] DIRECTIONS = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1},
            {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };
    private static final byte UNSEEN = Byte.MAX_VALUE;
    private static final byte NO_PARENT = -1;

    private TerritoryWallLayerSearch() { }

    static Result search(TerritoryClosureSearch.Cell core, TerritoryClosureSearch.WorldView world,
                         int horizontalRange, int verticalRange, int cap) {
        Session session = new Session(core, world, horizontalRange, verticalRange, cap);
        while (true) {
            Result result = session.advance(4_096);
            if (result != null) return result;
        }
    }

    enum Status { COUNTED, UNLOADED }

    /**
     * {@code layers} is exact when {@code atLeast} is false. {@code weakPoints} are the barriers on the
     * thinnest way out, from the outside in; empty when there is none to show.
     */
    record Result(Status status, int layers, boolean atLeast, List<TerritoryClosureSearch.Cell> weakPoints) {
        Result {
            weakPoints = weakPoints == null ? List.of() : List.copyOf(weakPoints);
        }
    }

    static final class Session {
        private final TerritoryClosureSearch.Cell core;
        private final TerritoryClosureSearch.WorldView world;
        private final int range;
        private final int height;
        private final int side;
        private final int tall;
        private final int cap;
        private byte[] cost;
        private byte[] parentDirection;
        private final ArrayDeque<Integer> queue = new ArrayDeque<>();
        private Result result;

        Session(TerritoryClosureSearch.Cell core, TerritoryClosureSearch.WorldView world, int horizontalRange,
                int verticalRange, int cap) {
            this.core = core;
            this.world = world;
            this.range = Math.max(2, horizontalRange);
            this.height = Math.max(2, verticalRange);
            this.side = this.range * 2 + 1;
            this.tall = this.height * 2 + 1;
            this.cap = Math.max(1, Math.min(CAP, cap));
        }

        /** Works through up to {@code cellBudget} neighbour checks; returns the result once there is one. */
        Result advance(int cellBudget) {
            if (result != null) return result;
            if (cost == null) {
                int cells = side * tall * side;
                cost = new byte[cells];
                parentDirection = new byte[cells];
                Arrays.fill(cost, UNSEEN);
                Arrays.fill(parentDirection, NO_PARENT);
                int start = index(0, 0, 0);
                cost[start] = 0;
                queue.add(start);
            }
            int examined = 0;
            while (examined < Math.max(1, cellBudget)) {
                Integer polled = queue.pollFirst();
                if (polled == null) {
                    result = finish(new Result(Status.COUNTED, cap, true, List.of()));
                    return result;
                }
                int current = polled;
                int dx = dx(current), dy = dy(current), dz = dz(current);
                if (Math.abs(dx) >= range || Math.abs(dy) >= height || Math.abs(dz) >= range) {
                    result = finish(new Result(Status.COUNTED, cost[current], false, weakPoints(current)));
                    return result;
                }
                for (int direction = 0; direction < DIRECTIONS.length; direction++) {
                    int[] step = DIRECTIONS[direction];
                    int nx = dx + step[0], ny = dy + step[1], nz = dz + step[2];
                    int next = index(nx, ny, nz);
                    examined++;
                    if (cost[next] != UNSEEN && cost[next] <= cost[current]) continue;
                    TerritoryClosureSearch.Cell cell = core.offset(nx, ny, nz);
                    if (!world.isLoaded(cell)) {
                        result = finish(new Result(Status.UNLOADED, 0, false, List.of()));
                        return result;
                    }
                    boolean barrier = world.isBarrier(cell);
                    int nextCost = cost[current] + (barrier ? 1 : 0);
                    if (nextCost >= cap || cost[next] != UNSEEN && cost[next] <= nextCost) continue;
                    cost[next] = (byte) nextCost;
                    parentDirection[next] = (byte) direction;
                    if (barrier) queue.addLast(next);
                    else queue.addFirst(next);
                }
            }
            return null;
        }

        /** Lets go of the arrays once there is a result. */
        private Result finish(Result found) {
            cost = null;
            parentDirection = null;
            queue.clear();
            return found;
        }

        private List<TerritoryClosureSearch.Cell> weakPoints(int edge) {
            List<TerritoryClosureSearch.Cell> barriers = new ArrayList<>();
            int cursor = edge;
            int steps = 0;
            while (parentDirection[cursor] != NO_PARENT && steps++ < side * tall) {
                int parent = parentOf(cursor);
                if (cost[cursor] > cost[parent]) {
                    barriers.add(core.offset(dx(cursor), dy(cursor), dz(cursor)));
                }
                cursor = parent;
            }
            return barriers;
        }

        private int parentOf(int index) {
            int[] step = DIRECTIONS[parentDirection[index]];
            return index(dx(index) - step[0], dy(index) - step[1], dz(index) - step[2]);
        }

        private int index(int dx, int dy, int dz) {
            return ((dx + range) * tall + (dy + height)) * side + (dz + range);
        }

        private int dx(int index) {
            return index / (tall * side) - range;
        }

        private int dy(int index) {
            return index / side % tall - height;
        }

        private int dz(int index) {
            return index % side - range;
        }
    }
}
