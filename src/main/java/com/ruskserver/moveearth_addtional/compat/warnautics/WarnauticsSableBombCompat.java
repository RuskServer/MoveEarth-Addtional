package com.ruskserver.moveearth_addtional.compat.warnautics;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Maps persistent bomb-rack plot coordinates through the current Sable pose. */
final class WarnauticsSableBombCompat {
    private WarnauticsSableBombCompat() { }

    static UUID subLevelId(ServerLevel level, BlockPos plotPos) {
        try {
            return Sable.HELPER.getContaining(level, plotPos) instanceof ServerSubLevel subLevel
                    ? subLevel.getUniqueId() : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    static Vec3 worldPosition(ServerLevel level, UUID subLevelId, BlockPos plotPos) {
        if (subLevelId == null) return Vec3.atCenterOf(plotPos);
        try {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null || !(container.getSubLevel(subLevelId) instanceof ServerSubLevel subLevel)
                    || subLevel.isRemoved()) return null;
            return subLevel.logicalPose().transformPosition(Vec3.atCenterOf(plotPos));
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }
}
