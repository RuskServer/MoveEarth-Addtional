package com.ruskserver.moveearth_addtional.compat.vehicle;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.block.entity.VehicleCoreBlockEntity;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.nation.NationStorageOwnershipSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationService;
import com.ruskserver.moveearth_addtional.s2.time.OpenTimeService;
import com.ruskserver.moveearth_addtional.s2.vehicle.VehicleIntegrityPolicy;
import com.ruskserver.moveearth_addtional.s2.vehicle.VehicleLootSavedData;
import com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Keeps vehicle records and the Sable bodies they govern in step, and repairs crafts a fault already broke.
 *
 * <p>Listens to Sable's sub-level container, so it sees every body as it loads and every body Sable removes:
 * <ul>
 *   <li><b>A body loads</b>: its ownership stamp is filled in; a record whose core stands on it but names another
 *       body is pointed back at it; a record left behind by a body that is gone, whose plot Sable has given to this
 *       one, is dropped; a core block that lost its record gets it back; an unowned body is given to the one nation
 *       whose storage is on it.</li>
 *   <li><b>A body is removed</b> with its blocks still in it (fallen out of the world, a physics failure,
 *       {@code /sable … remove}): the core's {@code onRemove} never runs, so the record would be billed forever
 *       and haunt the next body given that plot. It is dropped and the owner told.</li>
 *   <li><b>Every five minutes</b>: expired or orphaned salvage grants are dropped, and a record whose loaded body
 *       has stood without its core for several sweeps in a row is dropped.</li>
 * </ul>
 * Each body is examined once per server run; {@code /vehicleadmin heal} examines one again on demand.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class VehicleIntegrityService {
    private static final int HEAL_DELAY_TICKS = 40;
    private static final int REMOVAL_DELAY_TICKS = 20;
    private static final int SWEEP_INTERVAL_TICKS = 5 * 60 * 20;
    private static final int HEALS_PER_TICK = 8;

    private record Pending(ResourceKey<Level> dimension, UUID body, long due) { }

    private static final Set<Object> OBSERVED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final ArrayDeque<Pending> HEALS = new ArrayDeque<>();
    private static final ArrayDeque<Pending> REMOVALS = new ArrayDeque<>();
    private static final Set<UUID> HEALED = new HashSet<>();
    private static final Map<UUID, Integer> STRIKES = new HashMap<>();
    private static volatile long tick;

    private VehicleIntegrityService() {
    }

    /** What one examination changed, for logs and the admin command. */
    public record Report(int relinked, int ghosts, int restored, boolean stamped) {
        public boolean changed() {
            return relinked > 0 || ghosts > 0 || restored > 0 || stamped;
        }
    }

    private static final class Observer implements SubLevelObserver {
        private final ResourceKey<Level> dimension;

        private Observer(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }

        @Override
        public void onSubLevelAdded(SubLevel subLevel) {
            if (subLevel instanceof ServerSubLevel) {
                synchronized (HEALS) {
                    HEALS.add(new Pending(dimension, subLevel.getUniqueId(), tick + HEAL_DELAY_TICKS));
                }
            }
        }

        @Override
        public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
            if (reason == SubLevelRemovalReason.REMOVED && subLevel instanceof ServerSubLevel) {
                synchronized (REMOVALS) {
                    REMOVALS.add(new Pending(dimension, subLevel.getUniqueId(), tick + REMOVAL_DELAY_TICKS));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        tick = server.getTickCount();
        try {
            for (ServerLevel level : server.getAllLevels()) observe(level);
            drainRemovals(server);
            drainHeals(server);
            if (tick % SWEEP_INTERVAL_TICKS == 0) sweep(server);
        } catch (LinkageError unavailable) {
            // Sable is optional; without it there is nothing to keep in step.
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        OBSERVED.clear();
        synchronized (HEALS) {
            HEALS.clear();
        }
        synchronized (REMOVALS) {
            REMOVALS.clear();
        }
        HEALED.clear();
        STRIKES.clear();
    }

    private static void observe(ServerLevel level) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || !OBSERVED.add(container)) return;
        container.addObserver(new Observer(level.dimension()));
        // Bodies loaded before the observer was attached are examined like newly loaded ones.
        synchronized (HEALS) {
            for (SubLevel body : container.getAllSubLevels()) {
                HEALS.add(new Pending(level.dimension(), body.getUniqueId(), tick + HEAL_DELAY_TICKS));
            }
        }
    }

    private static void drainRemovals(MinecraftServer server) {
        List<Pending> due = new ArrayList<>();
        synchronized (REMOVALS) {
            while (!REMOVALS.isEmpty() && REMOVALS.peek().due() <= tick) due.add(REMOVALS.poll());
        }
        for (Pending pending : due) {
            ServerLevel level = server.getLevel(pending.dimension());
            SubLevelContainer container = level == null ? null : SubLevelContainer.getContainer(level);
            // Back already (a reload in the same breath): nothing was lost.
            if (container == null || container.getSubLevel(pending.body()) != null) continue;
            VehicleSavedData vehicles = VehicleSavedData.get(server);
            for (VehicleSavedData.VehicleRecord record : vehicles.all()) {
                // A record moved off the body before it went (a split, a merge) no longer names it.
                if (pending.body().equals(record.subLevelId())
                        && record.dimension().equals(level.dimension().location())) {
                    lose(server, record, "removed body " + pending.body());
                }
            }
        }
    }

    private static void drainHeals(MinecraftServer server) {
        List<Pending> due = new ArrayList<>();
        synchronized (HEALS) {
            while (!HEALS.isEmpty() && HEALS.peek().due() <= tick && due.size() < HEALS_PER_TICK) due.add(HEALS.poll());
        }
        for (Pending pending : due) {
            if (!HEALED.add(pending.body())) continue;
            ServerLevel level = server.getLevel(pending.dimension());
            SubLevelContainer container = level == null ? null : SubLevelContainer.getContainer(level);
            if (container != null && container.getSubLevel(pending.body()) instanceof ServerSubLevel body
                    && !body.isRemoved()) {
                Report report = heal(level, body);
                if (report.changed()) {
                    Moveearth_addtional.LOGGER.info("Vehicle integrity on body {}: {} relinked, {} ghost record(s) dropped, {} core(s) restored{}",
                            body.getUniqueId(), report.relinked(), report.ghosts(), report.restored(),
                            report.stamped() ? ", owner stamped" : "");
                }
            }
        }
    }

    /** Examines one loaded body and repairs what it finds. */
    public static Report heal(ServerLevel level, ServerSubLevel body) {
        MinecraftServer server = level.getServer();
        VehicleSavedData vehicles = VehicleSavedData.get(server);
        var dimension = level.dimension().location();
        var bounds = body.getPlot().getBoundingBox();
        UUID bodyId = body.getUniqueId();
        int relinked = 0;
        int ghosts = 0;
        int restored = 0;

        // Core blocks standing on this body.
        NationSavedData nations = NationSavedData.get(server);
        for (VehicleCoreBlockEntity core : cores(level, bounds)) {
            UUID vehicleId = core.vehicleId();
            if (vehicleId == null) continue;
            BlockPos pos = core.getBlockPos();
            VehicleSavedData.VehicleRecord record = vehicles.vehicle(vehicleId).orElse(null);
            if (record != null) {
                boolean recordedHere = record.corePos().equals(pos) && record.dimension().equals(dimension);
                // Recorded elsewhere, and that spot is loaded and no longer holds it: the record follows the core.
                boolean recordedSpotEmpty = !recordedHere && record.dimension().equals(dimension)
                        && level.isLoaded(record.corePos())
                        && !(level.getBlockEntity(record.corePos()) instanceof VehicleCoreBlockEntity other
                        && vehicleId.equals(other.vehicleId()));
                if (recordedHere && !bodyId.equals(record.subLevelId()) || recordedSpotEmpty) {
                    core.bind(vehicles.move(vehicleId, dimension, pos, bodyId));
                    relinked++;
                }
                if (recordedHere || recordedSpotEmpty) SableVehicleTopology.bindIfUnbound(level, body, vehicleId);
                continue;
            }
            if (VehicleIntegrityPolicy.restoresCore(vehicleId, core.nationId(),
                    SableVehicleTopology.boundVehicleId(body), SableVehicleTopology.taggedNation(body),
                    core.nationId() != null && nations.nation(core.nationId()).isPresent())) {
                VehicleSavedData.VehicleRecord back = vehicles.restore(vehicleId, core.nationId(), core.placedBy(),
                        dimension, pos, bodyId);
                core.bind(back);
                SableVehicleTopology.bindIfUnbound(level, body, vehicleId);
                restored++;
                Moveearth_addtional.LOGGER.warn("Restored the lost record of vehicle {} ({}) from its core at {}",
                        vehicleId, core.nationId(), pos);
            }
        }

        // Records whose core position lies in this plot, once the cores standing here have claimed theirs.
        for (VehicleSavedData.VehicleRecord record : vehicles.all()) {
            BlockPos pos = record.corePos();
            if (!record.dimension().equals(dimension) || !inside(bounds, pos) || !level.isLoaded(pos)) continue;
            boolean coreHere = level.getBlockEntity(pos) instanceof VehicleCoreBlockEntity core
                    && record.id().equals(core.vehicleId());
            switch (VehicleIntegrityPolicy.plotRecord(record.subLevelId(), bodyId, coreHere)) {
                case KEEP -> { }
                case RELINK -> {
                    vehicles.move(record.id(), dimension, pos, bodyId);
                    SableVehicleTopology.bindIfUnbound(level, body, record.id());
                    relinked++;
                }
                case GHOST -> {
                    lose(server, record, "ghost on reused plot of body " + bodyId);
                    ghosts++;
                }
            }
        }

        // Ownership stamp.
        boolean stamped = false;
        UUID owner = SableVehicleTopology.owningNation(level, body);
        if (owner != null && !owner.equals(SableVehicleTopology.taggedNation(body))) {
            SableVehicleTopology.tagNation(body, owner);
            stamped = true;
        } else if (owner == null) {
            UUID inferred = VehicleIntegrityPolicy.inferredNation(NationStorageOwnershipSavedData.get(server)
                            .ownersInside(dimension, bounds.minX(), bounds.minY(), bounds.minZ(),
                                    bounds.maxX(), bounds.maxY(), bounds.maxZ()),
                    nation -> nations.nation(nation).isPresent());
            if (inferred != null) {
                SableVehicleTopology.tagNation(body, inferred);
                stamped = true;
            }
        }
        return new Report(relinked, ghosts, restored, stamped);
    }

    private static void sweep(MinecraftServer server) {
        VehicleSavedData vehicles = VehicleSavedData.get(server);
        int pruned = VehicleLootSavedData.get(server).prune(OpenTimeService.now(server),
                id -> vehicles.vehicle(id).isPresent());
        if (pruned > 0) Moveearth_addtional.LOGGER.info("Dropped {} expired or orphaned vehicle salvage grant(s)", pruned);
        Set<UUID> seen = new HashSet<>();
        for (VehicleSavedData.VehicleRecord record : vehicles.all()) {
            seen.add(record.id());
            if (record.subLevelId() == null) continue;
            ServerLevel level = server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    record.dimension()));
            SubLevelContainer container = level == null ? null : SubLevelContainer.getContainer(level);
            if (container == null || !(container.getSubLevel(record.subLevelId()) instanceof ServerSubLevel body)
                    || body.isRemoved() || !level.isLoaded(record.corePos())) {
                STRIKES.remove(record.id());
                continue;
            }
            if (level.getBlockEntity(record.corePos()) instanceof VehicleCoreBlockEntity core
                    && record.id().equals(core.vehicleId())) {
                STRIKES.remove(record.id());
                continue;
            }
            // The core may simply stand elsewhere on the body; look before counting it missing.
            HEALED.remove(body.getUniqueId());
            heal(level, body);
            if (vehicles.vehicle(record.id()).map(after -> !after.corePos().equals(record.corePos())).orElse(true)) {
                STRIKES.remove(record.id());
                continue;
            }
            int strikes = STRIKES.merge(record.id(), 1, Integer::sum);
            if (VehicleIntegrityPolicy.dropsMissingCore(strikes)) {
                STRIKES.remove(record.id());
                lose(server, record, "core missing from its loaded body for " + strikes + " sweeps");
            }
        }
        STRIKES.keySet().retainAll(seen);
    }

    private static List<VehicleCoreBlockEntity> cores(ServerLevel level, dev.ryanhcode.sable.companion.math.BoundingBox3ic bounds) {
        return cores(level, bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ());
    }

    private static List<VehicleCoreBlockEntity> cores(ServerLevel level, int minX, int minZ, int maxX, int maxZ) {
        List<VehicleCoreBlockEntity> result = new ArrayList<>();
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) continue;
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof VehicleCoreBlockEntity core && !core.isRemoved()) result.add(core);
                }
            }
        }
        return result;
    }

    private static boolean inside(dev.ryanhcode.sable.companion.math.BoundingBox3ic bounds, BlockPos pos) {
        return pos.getX() >= bounds.minX() && pos.getX() <= bounds.maxX()
                && pos.getY() >= bounds.minY() && pos.getY() <= bounds.maxY()
                && pos.getZ() >= bounds.minZ() && pos.getZ() <= bounds.maxZ();
    }

    /** Drops a record whose craft is gone, and tells its nation. */
    private static void lose(MinecraftServer server, VehicleSavedData.VehicleRecord record, String reason) {
        VehicleSavedData vehicles = VehicleSavedData.get(server);
        if (vehicles.vehicle(record.id()).isEmpty()) return;
        vehicles.remove(record.id());
        VehicleLootSavedData.get(server).revoke(record.id());
        Moveearth_addtional.LOGGER.warn("Dropped the record of vehicle {} ({}, core {} on body {}): {}",
                record.id(), record.nationId(), record.corePos(), record.subLevelId(), reason);
        NationNotificationService.publish(server, List.of(record.nationId()),
                NationNotificationSavedData.EventType.VEHICLE_DESTROYED, record.dimension(), null,
                Component.literal("自国の車両（" + record.id().toString().substring(0, 8)
                        + "）の物理構造物が失われたため、車両の記録を削除しました。積み荷の補填は運営に相談してください"),
                List.of("車両の物理構造物が失われました"));
    }
}
