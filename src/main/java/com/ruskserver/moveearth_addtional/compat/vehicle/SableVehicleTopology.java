package com.ruskserver.moveearth_addtional.compat.vehicle;

import com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementSavedData;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Resolves one vehicle core across Sable bodies connected by hinges, bearings and swivels. */
public final class SableVehicleTopology {
    private static final String VEHICLE_ID = "MoveEarthVehicleId";
    private SableVehicleTopology() { }

    public static Optional<VehicleContext> at(ServerLevel level, BlockPos plotPos) {
        SubLevel containing = Sable.HELPER.getContaining(level, plotPos);
        if (!(containing instanceof ServerSubLevel serverSubLevel)) return Optional.empty();
        Set<UUID> bodies = new LinkedHashSet<>();
        Set<UUID> candidates = new LinkedHashSet<>();
        for (SubLevel body : SubLevelHelper.getConnectedChain(serverSubLevel)) {
            if (!(body instanceof ServerSubLevel serverBody) || body.isRemoved()) continue;
            bodies.add(body.getUniqueId());
            CompoundTag userData = serverBody.getUserDataTag();
            if (userData == null) continue;
            if (userData.hasUUID(VEHICLE_ID)) candidates.add(userData.getUUID(VEHICLE_ID));
        }
        if (candidates.size() != 1) return Optional.empty();
        UUID vehicleId = candidates.iterator().next();
        VehicleSavedData.VehicleRecord record = VehicleSavedData.get(level.getServer())
                .vehicle(vehicleId).orElse(null);
        // Detached armored fragments retain metadata, but not protection: the chain must still contain the core body.
        if (record == null || record.subLevelId() == null || !bodies.contains(record.subLevelId())) {
            return Optional.empty();
        }
        return Optional.of(new VehicleContext(record, Set.copyOf(bodies)));
    }

    public static void bind(ServerSubLevel subLevel, UUID vehicleId) {
        CompoundTag current = subLevel.getUserDataTag();
        CompoundTag tag = current == null ? new CompoundTag() : current.copy();
        tag.putUUID(VEHICLE_ID, vehicleId);
        subLevel.setUserDataTag(tag);
    }

    /** Resolves plot-space placement to the craft's current physical world position. */
    public static Placement placement(ServerLevel level, BlockPos plotPos) {
        try {
            SubLevel containing = Sable.HELPER.getContaining(level, plotPos);
            if (containing instanceof ServerSubLevel serverSubLevel && !serverSubLevel.isRemoved()) {
                Vec3 world = serverSubLevel.logicalPose().transformPosition(Vec3.atCenterOf(plotPos));
                return new Placement(BlockPos.containing(world), serverSubLevel);
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Treat an unavailable Sable mapping as ordinary main-world placement.
        }
        return new Placement(plotPos.immutable(), null);
    }

    /** Attaches a core placed directly onto an already assembled Sable body. */
    public static VehicleSavedData.VehicleRecord bindPlacedCore(ServerLevel level, BlockPos plotPos,
                                                                 VehicleSavedData.VehicleRecord record) {
        Placement placement = placement(level, plotPos);
        if (placement.subLevel() == null) return record;
        bind(placement.subLevel(), record.id());
        return VehicleSavedData.get(level.getServer()).move(record.id(), level.dimension().location(),
                plotPos, placement.subLevel().getUniqueId());
    }

    public static double distanceSquared(ServerLevel level, Entity entity, BlockPos pos) {
        return Sable.HELPER.distanceSquaredWithSubLevels(level, entity.position(),
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    public static java.util.List<ReinforcementSavedData.LocatedEntry> entriesForPlayer(
            ServerPlayer player, ReinforcementSavedData data) {
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(player.serverLevel());
        if (container == null) return java.util.List.of();
        UUID nationId = com.ruskserver.moveearth_addtional.s2.nation.NationSavedData.get(player.server)
                .nationIdFor(player.getUUID()).orElse(null);
        if (nationId == null) return java.util.List.of();
        java.util.List<ReinforcementSavedData.LocatedEntry> result = new java.util.ArrayList<>();
        for (ServerSubLevel serverBody : container.getAllSubLevels()) {
            if (serverBody.isRemoved()) continue;
            var worldBounds = serverBody.boundingBox();
            var playerPos = player.position();
            if (playerPos.x < worldBounds.minX() - 68 || playerPos.x > worldBounds.maxX() + 68
                    || playerPos.y < worldBounds.minY() - 68 || playerPos.y > worldBounds.maxY() + 68
                    || playerPos.z < worldBounds.minZ() - 68 || playerPos.z > worldBounds.maxZ() + 68) continue;
            var context = at(player.serverLevel(), serverBody.getPlot().getCenterBlock()).orElse(null);
            if (context == null || !nationId.equals(context.vehicle().nationId())) continue;
            var bounds = serverBody.getPlot().getBoundingBox();
            for (var entry : data.inside(player.serverLevel(), bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
                if (distanceSquared(player.serverLevel(), player, entry.pos()) <= 68.0D * 68.0D) result.add(entry);
                if (result.size() >= 8192) break;
            }
            if (result.size() >= 8192) break;
        }
        return result.size() <= 8192 ? java.util.List.copyOf(result) : java.util.List.copyOf(result.subList(0, 8192));
    }

    public static VehicleStatistics statistics(ServerLevel level, BlockPos plotPos) {
        SubLevel containing = Sable.HELPER.getContaining(level, plotPos);
        VehicleContext context = at(level, plotPos).orElse(null);
        if (!(containing instanceof ServerSubLevel serverContaining) || context == null) {
            return new VehicleStatistics(1, 0);
        }
        ReinforcementSavedData data = ReinforcementSavedData.get(level);
        int reinforced = 0;
        for (SubLevel body : SubLevelHelper.getConnectedChain(serverContaining)) {
            if (!(body instanceof ServerSubLevel serverBody)
                    || !context.connectedBodies().contains(body.getUniqueId())) continue;
            var bounds = serverBody.getPlot().getBoundingBox();
            reinforced += data.inside(level, bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ()).size();
        }
        return new VehicleStatistics(context.connectedBodies().size(), reinforced);
    }

    public record VehicleContext(VehicleSavedData.VehicleRecord vehicle, Set<UUID> connectedBodies) { }
    public record VehicleStatistics(int connectedBodies, int reinforcedBlocks) { }
    public record Placement(BlockPos worldPos, ServerSubLevel subLevel) { }
}
