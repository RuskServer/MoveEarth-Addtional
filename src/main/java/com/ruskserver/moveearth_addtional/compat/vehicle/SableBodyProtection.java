package com.ruskserver.moveearth_addtional.compat.vehicle;

import com.ruskserver.moveearth_addtional.block.entity.VehicleCoreBlockEntity;
import com.ruskserver.moveearth_addtional.raid.RaidShipSweeper;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Physical core presence matters: an inherited vehicle id on detached debris is not a core. */
public final class SableBodyProtection {
    private SableBodyProtection() { }

    public record Chain(Set<UUID> ids, boolean protectedBody) { }

    public static Chain inspect(ServerLevel level, ServerSubLevel root) {
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return new Chain(Set.of(root.getUniqueId()), true);
        Set<UUID> ids = new HashSet<>();
        boolean protect = false;
        for (var connected : SubLevelHelper.getConnectedChain(root)) {
            if (!(connected instanceof ServerSubLevel body) || body.isRemoved()) continue;
            ids.add(body.getUniqueId());
            protect |= RaidShipSweeper.isActiveBody(container, body) || occupied(level, body);
            // Inspect only already loaded plot chunks. No world/chunk loads during a safety sweep.
            // If a holder is not ready, fail closed until it can be checked.
            if (body.getPlot().getLoadedChunks().isEmpty()) protect = true;
            for (var holder : body.getPlot().getLoadedChunks()) {
                var chunk = holder.getChunk();
                if (chunk == null) { protect = true; continue; }
                for (var blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof VehicleCoreBlockEntity && !blockEntity.isRemoved()) protect = true;
                }
            }
        }
        if (ids.isEmpty()) return new Chain(Set.of(root.getUniqueId()), true);
        return new Chain(Set.copyOf(ids), protect);
    }

    private static boolean occupied(ServerLevel level, ServerSubLevel body) {
        var bounds = body.boundingBox();
        for (var player : level.players()) {
            var containing = Sable.HELPER.getContaining(player);
            if (containing == body) return true;
            var point = worldPosition(player);
            if (point.x >= bounds.minX() - 2 && point.x <= bounds.maxX() + 2
                    && point.y >= bounds.minY() - 2 && point.y <= bounds.maxY() + 2
                    && point.z >= bounds.minZ() - 2 && point.z <= bounds.maxZ() + 2) return true;
        }
        return false;
    }

    public static Vec3 worldPosition(ServerPlayer player) {
        var containing = Sable.HELPER.getContaining(player);
        return containing == null ? player.position() : containing.logicalPose().transformPosition(player.position());
    }
}
