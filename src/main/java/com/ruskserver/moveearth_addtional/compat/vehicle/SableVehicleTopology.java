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

import com.ruskserver.moveearth_addtional.s2.vehicle.VehicleIdentityPolicy;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Resolves one vehicle core across Sable bodies connected by hinges, bearings and swivels. */
public final class SableVehicleTopology {
    private static final String VEHICLE_ID = "MoveEarthVehicleId";
    /**
     * The nation a body belongs to, kept beside the vehicle id. The id stops meaning anything once its record is
     * gone (the core was broken or the craft lost), but the hull and its cargo are still that nation's: without
     * this, storage on a wreck or a split-off fragment answered to nobody and locked for everyone.
     */
    private static final String NATION_ID = "MoveEarthVehicleNation";
    private SableVehicleTopology() { }

    /** One connected chain as seen from one of its bodies. */
    private record Chain(ServerSubLevel own, Set<UUID> bodies, Map<ServerSubLevel, UUID> bound) {
        Map<UUID, UUID> boundByBody() {
            Map<UUID, UUID> result = new LinkedHashMap<>();
            bound.forEach((body, vehicle) -> result.put(body.getUniqueId(), vehicle));
            return result;
        }
    }

    private static Chain chain(ServerSubLevel own) {
        Set<UUID> bodies = new LinkedHashSet<>();
        Map<ServerSubLevel, UUID> bound = new LinkedHashMap<>();
        for (SubLevel body : SubLevelHelper.getConnectedChain(own)) {
            if (!(body instanceof ServerSubLevel serverBody) || body.isRemoved()) continue;
            bodies.add(body.getUniqueId());
            UUID vehicle = boundVehicleId(serverBody);
            if (vehicle != null) bound.put(serverBody, vehicle);
        }
        return new Chain(own, bodies, bound);
    }

    private static UUID coreBodyOf(VehicleSavedData vehicles, UUID vehicle) {
        return vehicles.vehicle(vehicle).map(VehicleSavedData.VehicleRecord::subLevelId).orElse(null);
    }

    public static Optional<VehicleContext> at(ServerLevel level, BlockPos plotPos) {
        SubLevel containing = Sable.HELPER.getContaining(level, plotPos);
        if (!(containing instanceof ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) return Optional.empty();
        Chain chain = chain(serverSubLevel);
        VehicleSavedData vehicles = VehicleSavedData.get(level.getServer());
        // Detached armored fragments keep their owner, but not protection: only a chain holding the core is governed.
        Map<UUID, UUID> boundByBody = chain.boundByBody();
        UUID vehicleId = VehicleIdentityPolicy.governing(serverSubLevel.getUniqueId(), boundByBody,
                chain.bodies(), vehicle -> coreBodyOf(vehicles, vehicle)).orElse(null);
        if (vehicleId == null) return Optional.empty();
        VehicleSavedData.VehicleRecord record = vehicles.vehicle(vehicleId).orElse(null);
        if (record == null) return Optional.empty();
        rebindStale(chain, record, vehicles);
        // Bodies of another vehicle tied on by a rope or docking connector are not part of this one.
        Set<UUID> own = new LinkedHashSet<>();
        for (UUID body : chain.bodies()) {
            UUID carried = boundByBody.get(body);
            if (carried == null || carried.equals(vehicleId) || vehicles.vehicle(carried).isEmpty()) own.add(body);
        }
        return Optional.of(new VehicleContext(record, Set.copyOf(own)));
    }

    /**
     * Hands bodies of the chain to the vehicle now governing it: those still carrying a dismantled core's id, and
     * same-nation fragments whose own core is elsewhere. Another nation's live id is never taken over.
     */
    private static void rebindStale(Chain chain, VehicleSavedData.VehicleRecord governing, VehicleSavedData vehicles) {
        for (Map.Entry<ServerSubLevel, UUID> entry : chain.bound().entrySet()) {
            UUID carried = entry.getValue();
            var carriedRecord = vehicles.vehicle(carried).orElse(null);
            if (!entry.getKey().isRemoved() && VehicleIdentityPolicy.rebindable(carried, governing.id(),
                    carriedRecord != null, carriedRecord == null ? null : carriedRecord.subLevelId(), chain.bodies(),
                    carriedRecord == null ? null : carriedRecord.nationId(), governing.nationId())) {
                bind(entry.getKey(), governing.id(), governing.nationId());
            }
        }
    }

    /** The vehicle id a body carries, live or not; null when it carries none. */
    public static UUID boundVehicleId(ServerSubLevel subLevel) {
        CompoundTag userData = subLevel.getUserDataTag();
        return userData != null && userData.hasUUID(VEHICLE_ID) ? userData.getUUID(VEHICLE_ID) : null;
    }

    /** The nation stamped on a body, whether or not its vehicle still exists; null when none was. */
    public static UUID taggedNation(ServerSubLevel subLevel) {
        CompoundTag userData = subLevel.getUserDataTag();
        return userData != null && userData.hasUUID(NATION_ID) ? userData.getUUID(NATION_ID) : null;
    }

    /**
     * The nation a body belongs to, governed or not: its live vehicle's, or else the nation stamped on it while
     * that nation exists. Null for a body no nation ever owned.
     */
    public static UUID owningNation(ServerLevel level, ServerSubLevel subLevel) {
        UUID vehicle = boundVehicleId(subLevel);
        var record = vehicle == null ? null : VehicleSavedData.get(level.getServer()).vehicle(vehicle).orElse(null);
        if (record != null) return record.nationId();
        UUID tagged = taggedNation(subLevel);
        return tagged != null && com.ruskserver.moveearth_addtional.s2.nation.NationSavedData.get(level.getServer())
                .nation(tagged).isPresent() ? tagged : null;
    }

    /** The nation the body at {@code plotPos} belongs to (see {@link #owningNation(ServerLevel, ServerSubLevel)}). */
    public static UUID owningNation(ServerLevel level, BlockPos plotPos) {
        return Sable.HELPER.getContaining(level, plotPos) instanceof ServerSubLevel body && !body.isRemoved()
                ? owningNation(level, body) : null;
    }

    /** Whether the body carries the id of a vehicle that still exists. */
    public static boolean boundToLiveVehicle(ServerLevel level, ServerSubLevel subLevel) {
        UUID vehicle = boundVehicleId(subLevel);
        return vehicle != null && VehicleSavedData.get(level.getServer()).vehicle(vehicle).isPresent();
    }

    /** Binds a body to a vehicle and stamps the vehicle's nation on it. */
    public static void bind(ServerLevel level, ServerSubLevel subLevel, UUID vehicleId) {
        bind(subLevel, vehicleId, VehicleSavedData.get(level.getServer()).vehicle(vehicleId)
                .map(VehicleSavedData.VehicleRecord::nationId).orElse(null));
    }

    private static void bind(ServerSubLevel subLevel, UUID vehicleId, UUID nationId) {
        CompoundTag current = subLevel.getUserDataTag();
        CompoundTag tag = current == null ? new CompoundTag() : current.copy();
        tag.putUUID(VEHICLE_ID, vehicleId);
        if (nationId != null) tag.putUUID(NATION_ID, nationId);
        subLevel.setUserDataTag(tag);
    }

    /** Stamps a nation on a body that has no vehicle, keeping a split-off piece its owner's. */
    public static void tagNation(ServerSubLevel subLevel, UUID nationId) {
        if (nationId == null || nationId.equals(taggedNation(subLevel))) return;
        CompoundTag current = subLevel.getUserDataTag();
        CompoundTag tag = current == null ? new CompoundTag() : current.copy();
        tag.putUUID(NATION_ID, nationId);
        subLevel.setUserDataTag(tag);
    }

    /** Binds a body that no live vehicle governs yet; one bound to a live vehicle keeps it. */
    public static void bindIfUnbound(ServerLevel level, ServerSubLevel subLevel, UUID vehicleId) {
        if (!boundToLiveVehicle(level, subLevel)) bind(level, subLevel, vehicleId);
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

    /**
     * Whether a core placed at {@code plotPos} by {@code placerNation} may bind its craft; false when a live
     * core already governs any connected body, or a body still carries another nation's live vehicle (see
     * {@link com.ruskserver.moveearth_addtional.s2.vehicle.VehicleCorePlacementPolicy}). Placement outside a
     * Sable body always may.
     */
    public static boolean mayBindPlacedCore(ServerLevel level, BlockPos plotPos, UUID placerNation) {
        SubLevel containing = Sable.HELPER.getContaining(level, plotPos);
        if (!(containing instanceof ServerSubLevel serverSubLevel)) return true;
        Chain chain = chain(serverSubLevel);
        VehicleSavedData vehicles = VehicleSavedData.get(level.getServer());
        return com.ruskserver.moveearth_addtional.s2.vehicle.VehicleCorePlacementPolicy.mayBind(chain.bodies(),
                chain.boundByBody(), vehicle -> coreBodyOf(vehicles, vehicle),
                vehicle -> vehicles.vehicle(vehicle).map(VehicleSavedData.VehicleRecord::nationId).orElse(null),
                placerNation);
    }

    /** Attaches a core placed directly onto an already assembled Sable body. */
    public static VehicleSavedData.VehicleRecord bindPlacedCore(ServerLevel level, BlockPos plotPos,
                                                                 VehicleSavedData.VehicleRecord record) {
        Placement placement = placement(level, plotPos);
        if (placement.subLevel() == null) return record;
        VehicleSavedData vehicles = VehicleSavedData.get(level.getServer());
        VehicleSavedData.VehicleRecord moved = vehicles.move(record.id(), level.dimension().location(),
                plotPos, placement.subLevel().getUniqueId());
        if (moved == null) return record;
        bind(placement.subLevel(), moved.id(), moved.nationId());
        // Turrets and other joined bodies still carry the dismantled predecessor's id; hand them to this core.
        rebindStale(chain(placement.subLevel()), moved, vehicles);
        return moved;
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
