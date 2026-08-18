package com.ruskserver.moveearth_addtional.territory.raid;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

public final class SableRaidLocator {
    private SableRaidLocator() {
    }

    public static Optional<Location> locate(BlockEntity blockEntity) {
        if (blockEntity.getLevel() == null
                || !(Sable.HELPER.getContaining(blockEntity) instanceof ServerSubLevel subLevel)
                || subLevel.isRemoved()) {
            return Optional.empty();
        }
        Vec3 worldCenter = projectToWorld(blockEntity.getLevel(), blockEntity.getBlockPos());
        return Optional.of(new Location(subLevel.getUniqueId(), worldCenter));
    }

    public static Vec3 projectToWorld(Level level, BlockPos pos) {
        return Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(pos));
    }

    public record Location(UUID shipId, Vec3 worldPosition) {
    }
}
