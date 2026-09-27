package com.ruskserver.moveearth_addtional.compat.vehicle;

import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementSavedData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.Set;

/** Owned by one merged tracker, so unloading/rebuilding a body cannot retain or duplicate its mass. */
public final class SableArmorMassCache {
    private int[] bounds;
    private long worldRevision = -1L;
    private long localRevision = -1L;
    private long checkedAt = Long.MIN_VALUE;
    private ArmorMassProperties properties = new ArmorMassProperties();
    private Set<BlockPos> present = Set.of();
    private Set<BlockPos> absent = Set.of();

    public double merge(ServerSubLevel body, double mass, Vector3d center, Matrix3d inertia) {
        var level = body.getLevel();
        var box = body.getPlot().getBoundingBox();
        boolean boundsChanged = bounds == null || bounds[0] != box.minX() || bounds[1] != box.minY()
                || bounds[2] != box.minZ() || bounds[3] != box.maxX() || bounds[4] != box.maxY()
                || bounds[5] != box.maxZ();
        int[] current = boundsChanged
                ? new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()} : bounds;
        var data = ReinforcementSavedData.get(level);
        boolean changed = boundsChanged;
        if (changed || worldRevision != data.massRevision()) {
            long revision = data.massRevisionInside(current[0], current[2], current[3], current[5]);
            changed |= revision != localRevision;
            localRevision = revision;
            worldRevision = data.massRevision();
        }
        // Safety net for external destruction before reinforcement cleanup: check armor positions only.
        long now = level.getGameTime();
        if (checkedAt == Long.MIN_VALUE || now < checkedAt || now - checkedAt >= 20L) {
            for (BlockPos pos : present) {
                if (level.hasChunkAt(pos) && level.getBlockState(pos).isAir()) {
                    changed = true;
                    break;
                }
            }
            for (BlockPos pos : absent) {
                if (level.hasChunkAt(pos) && !level.getBlockState(pos).isAir()) {
                    changed = true;
                    break;
                }
            }
            checkedAt = now;
        }
        if (changed) {
            properties = new ArmorMassProperties();
            Set<BlockPos> positions = new HashSet<>();
            Set<BlockPos> missing = new HashSet<>();
            data.forEachInside(current[0], current[1], current[2], current[3], current[4], current[5],
                    (pos, entry) -> {
                        if (!level.hasChunkAt(pos) || level.getBlockState(pos).isAir()) {
                            missing.add(pos);
                            return;
                        }
                        positions.add(pos);
                        properties.add(entry.material().addedMass(),
                                (double) pos.getX() - current[0] + 0.5,
                                (double) pos.getY() - current[1] + 0.5,
                                (double) pos.getZ() - current[2] + 0.5);
                    });
            present = positions;
            absent = missing;
            bounds = current;
        }
        return properties.merge(mass, center, inertia, current[0], current[1], current[2]);
    }
}
