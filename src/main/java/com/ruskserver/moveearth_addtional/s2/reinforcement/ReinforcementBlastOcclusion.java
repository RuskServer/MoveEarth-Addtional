package com.ruskserver.moveearth_addtional.s2.reinforcement;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.LinkedHashSet;
import java.util.function.Predicate;

/** Voxel ray test that lets the first reinforced wall absorb a blast without damaging armor behind it. */
public final class ReinforcementBlastOcclusion {
    private static final int MAX_STEPS = 512;

    private ReinforcementBlastOcclusion() { }

    public static Set<BlockPos> barriersAround(ServerLevel level, ReinforcementSavedData data,
                                                BlockPos center, int radius) {
        Set<BlockPos> result = new LinkedHashSet<>();
        for (ReinforcementSavedData.LocatedEntry located : data.around(
                level, center, Math.max(0, radius))) {
            if (located.entry().enabled()) result.add(located.pos().immutable());
        }
        return Set.copyOf(result);
    }

    public static boolean blocked(Vec3 origin, BlockPos target, Set<BlockPos> barriers) {
        if (origin == null || target == null || barriers == null || barriers.isEmpty()) return false;
        return blocked(origin.x, origin.y, origin.z, target.getX(), target.getY(), target.getZ(),
                cell -> barriers.contains(new BlockPos(cell.x(), cell.y(), cell.z())));
    }

    public static boolean blocked(double originX, double originY, double originZ,
                                  int targetX, int targetY, int targetZ,
                                  Predicate<Cell> barrier) {
        if (barrier == null) return false;
        int x = floor(originX);
        int y = floor(originY);
        int z = floor(originZ);
        if (x == targetX && y == targetY && z == targetZ) return false;
        if (barrier.test(new Cell(x, y, z))) return true;

        double endX = targetX + 0.5D;
        double endY = targetY + 0.5D;
        double endZ = targetZ + 0.5D;
        double dx = endX - originX;
        double dy = endY - originY;
        double dz = endZ - originZ;
        int stepX = Double.compare(dx, 0.0D);
        int stepY = Double.compare(dy, 0.0D);
        int stepZ = Double.compare(dz, 0.0D);
        double deltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dx);
        double deltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dy);
        double deltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dz);
        double maxX = initialBoundary(originX, x, stepX, dx);
        double maxY = initialBoundary(originY, y, stepY, dy);
        double maxZ = initialBoundary(originZ, z, stepZ, dz);

        for (int step = 0; step < MAX_STEPS; step++) {
            if (maxX <= maxY && maxX <= maxZ) {
                x += stepX;
                maxX += deltaX;
            } else if (maxY <= maxZ) {
                y += stepY;
                maxY += deltaY;
            } else {
                z += stepZ;
                maxZ += deltaZ;
            }
            if (x == targetX && y == targetY && z == targetZ) return false;
            if (barrier.test(new Cell(x, y, z))) return true;
        }
        return false;
    }

    private static double initialBoundary(double coordinate, int block, int step, double delta) {
        if (step == 0) return Double.POSITIVE_INFINITY;
        double boundary = step > 0 ? block + 1.0D : block;
        return (boundary - coordinate) / delta;
    }

    private static int floor(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    public record Cell(int x, int y, int z) { }
}
