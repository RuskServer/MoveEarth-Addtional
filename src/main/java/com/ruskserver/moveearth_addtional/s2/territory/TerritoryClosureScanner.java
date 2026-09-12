package com.ruskserver.moveearth_addtional.s2.territory;

import net.minecraft.core.BlockPos;

import java.util.List;

/** Minecraft adapter for the bounded flood fill used to prove a reinforced enclosure. */
public final class TerritoryClosureScanner {
    public static final int SEARCH_RANGE = 32;
    public static final int MAX_VISITED = 32_768;
    public static final int MAX_PATH = 256;

    private TerritoryClosureScanner() {
    }

    public static Result scan(BlockPos core, WorldView world) {
        return scan(core, world, SEARCH_RANGE, MAX_VISITED);
    }

    static Result scan(BlockPos core, WorldView world, int range, int maxVisited) {
        Session session = begin(core, world, range, maxVisited);
        while (true) {
            Progress progress = session.advance(4_096);
            if (progress.result() != null) return progress.result();
        }
    }

    public static Session begin(BlockPos core, WorldView world) {
        return begin(core, world, SEARCH_RANGE, MAX_VISITED);
    }

    static Session begin(BlockPos core, WorldView world, int range, int maxVisited) {
        TerritoryClosureSearch.WorldView adapter = new TerritoryClosureSearch.WorldView() {
            @Override
            public boolean isLoaded(TerritoryClosureSearch.Cell cell) {
                return world.isLoaded(blockPos(cell));
            }

            @Override
            public boolean isBarrier(TerritoryClosureSearch.Cell cell) {
                return world.isBarrier(blockPos(cell));
            }
        };
        return new Session(new TerritoryClosureSearch.Session(
                cell(core), adapter, range, maxVisited, MAX_PATH));
    }

    private static Result convert(TerritoryClosureSearch.Result found) {
        List<BlockPos> path = found.escapePath().stream()
                .map(TerritoryClosureScanner::blockPos).toList();
        return new Result(Status.valueOf(found.status().name()), found.visited(), path);
    }

    public static final class Session {
        private final TerritoryClosureSearch.Session search;

        private Session(TerritoryClosureSearch.Session search) {
            this.search = search;
        }

        public Progress advance(int cellBudget) {
            TerritoryClosureSearch.Progress progress = search.advance(cellBudget);
            return new Progress(progress.result() == null ? null : convert(progress.result()),
                    progress.examinedCells());
        }
    }

    private static TerritoryClosureSearch.Cell cell(BlockPos pos) {
        return new TerritoryClosureSearch.Cell(pos.getX(), pos.getY(), pos.getZ());
    }

    private static BlockPos blockPos(TerritoryClosureSearch.Cell cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }

    public enum Status { SEALED, OPEN, TOO_SMALL, LIMIT_EXCEEDED, UNLOADED }

    public record Result(Status status, int visited, List<BlockPos> escapePath) {
        public Result {
            escapePath = escapePath == null ? List.of() : List.copyOf(escapePath);
        }

        public boolean sealed() {
            return status == Status.SEALED;
        }
    }

    public record Progress(Result result, int examinedCells) {
        public boolean complete() { return result != null; }
    }

    public interface WorldView {
        boolean isLoaded(BlockPos pos);
        boolean isBarrier(BlockPos pos);
    }
}
