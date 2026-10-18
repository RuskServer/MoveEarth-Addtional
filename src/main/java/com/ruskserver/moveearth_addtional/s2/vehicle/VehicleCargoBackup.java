package com.ruskserver.moveearth_addtional.s2.vehicle;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps a record of what each registered vehicle carries, so that when a craft is lost to a Sable fault, as an
 * airship that split apart and took its chests' contents with it, staff can see what was aboard and hand it
 * back ({@code /vehicleadmin cargo}). Every container on every body bound to the vehicle is read (chests,
 * barrels and anything else that is a container, and Create's item vaults block by block) every ten minutes,
 * just before a body splits, and when the server stops. A snapshot identical to the last is not stored again;
 * the newest {@link VehicleCargoPolicy#KEPT_SNAPSHOTS} are kept, one file per vehicle under
 * {@code <world>/moveearth/vehicle_cargo}. Only the record is kept: nothing is restored on its own.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class VehicleCargoBackup {
    private VehicleCargoBackup() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % VehicleCargoPolicy.INTERVAL_TICKS != 0) return;
        snapshotAll(event.getServer(), "periodic");
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        snapshotAll(event.getServer(), "stop");
    }

    /** Just before Sable splits a body: what the vehicle carried while it was still whole. */
    public static void beforeSplit(ServerLevel level, ServerSubLevel splitting) {
        UUID vehicleId = SableVehicleTopology.boundVehicleId(splitting);
        if (vehicleId == null || VehicleSavedData.get(level.getServer()).vehicle(vehicleId).isEmpty()) return;
        try {
            store(level.getServer(), vehicleId, collect(level, vehicleId), "split");
        } catch (RuntimeException failure) {
            Moveearth_addtional.LOGGER.warn("Could not record the cargo of vehicle {} before a split", vehicleId, failure);
        }
    }

    private static void snapshotAll(MinecraftServer server, String reason) {
        VehicleSavedData vehicles = VehicleSavedData.get(server);
        for (ServerLevel level : server.getAllLevels()) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) continue;
            Map<UUID, ListTag> byVehicle = new HashMap<>();
            for (SubLevel body : container.getAllSubLevels()) {
                if (!(body instanceof ServerSubLevel serverBody) || body.isRemoved()) continue;
                UUID vehicleId = SableVehicleTopology.boundVehicleId(serverBody);
                if (vehicleId == null || vehicles.vehicle(vehicleId).isEmpty()) continue;
                read(level, serverBody, byVehicle.computeIfAbsent(vehicleId, ignored -> new ListTag()));
            }
            byVehicle.forEach((vehicleId, containers) -> {
                try {
                    store(server, vehicleId, containers, reason);
                } catch (RuntimeException failure) {
                    Moveearth_addtional.LOGGER.warn("Could not record the cargo of vehicle {}", vehicleId, failure);
                }
            });
        }
    }

    /** Every container on every body bound to {@code vehicleId} in this level. */
    private static ListTag collect(ServerLevel level, UUID vehicleId) {
        ListTag containers = new ListTag();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return containers;
        for (SubLevel body : container.getAllSubLevels()) {
            if (body instanceof ServerSubLevel serverBody && !body.isRemoved()
                    && vehicleId.equals(SableVehicleTopology.boundVehicleId(serverBody))) {
                read(level, serverBody, containers);
            }
        }
        return containers;
    }

    private static void read(ServerLevel level, ServerSubLevel body, ListTag out) {
        for (var holder : body.getPlot().getLoadedChunks()) {
            for (BlockEntity blockEntity : holder.getChunk().getBlockEntities().values()) {
                List<ItemStack> items = items(blockEntity);
                if (items.isEmpty()) continue;
                CompoundTag entry = new CompoundTag();
                entry.putLong("Pos", blockEntity.getBlockPos().asLong());
                entry.putString("Block", BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock()).toString());
                ListTag stacks = new ListTag();
                for (ItemStack stack : items) stacks.add(stack.save(level.registryAccess()));
                entry.put("Items", stacks);
                out.add(entry);
            }
        }
    }

    private static List<ItemStack> items(BlockEntity blockEntity) {
        List<ItemStack> items = new ArrayList<>();
        if (blockEntity instanceof Container container) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty()) items.add(stack.copy());
            }
        } else if (blockEntity instanceof com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity vault
                && vault.getInventoryOfBlock() != null) {
            var inventory = vault.getInventoryOfBlock();
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (!stack.isEmpty()) items.add(stack.copy());
            }
        }
        return items;
    }

    private static void store(MinecraftServer server, UUID vehicleId, ListTag containers, String reason) {
        CompoundTag file = load(server, vehicleId);
        ListTag snapshots = file.getList("Snapshots", Tag.TAG_COMPOUND);
        if (!snapshots.isEmpty() && snapshots.getCompound(0).getList("Containers", Tag.TAG_COMPOUND).equals(containers)) return;
        VehicleSavedData.VehicleRecord record = VehicleSavedData.get(server).vehicle(vehicleId).orElse(null);
        CompoundTag snapshot = new CompoundTag();
        snapshot.putLong("Time", System.currentTimeMillis());
        snapshot.putString("Reason", reason);
        if (record != null) {
            snapshot.putUUID("Nation", record.nationId());
            snapshot.putString("NationName", NationSavedData.get(server).nation(record.nationId())
                    .map(NationSavedData.Nation::name).orElse("?"));
        }
        snapshot.put("Containers", containers);
        int pinned = -1;
        for (int i = 0; i < snapshots.size(); i++) {
            if ("split".equals(snapshots.getCompound(i).getString("Reason"))) {
                pinned = i;
                break;
            }
        }
        int drop = VehicleCargoPolicy.dropIndex(snapshots.size(), pinned);
        if (drop >= 0) snapshots.remove(drop);
        snapshots.add(0, snapshot);
        file.put("Snapshots", snapshots);
        file.putUUID("Vehicle", vehicleId);
        write(server, vehicleId, file);
    }

    /** The snapshots of a vehicle, newest first; empty when it has none. */
    public static ListTag snapshots(MinecraftServer server, UUID vehicleId) {
        return load(server, vehicleId).getList("Snapshots", Tag.TAG_COMPOUND);
    }

    /** Every vehicle with a record, by id. */
    public static List<UUID> recorded(MinecraftServer server) {
        List<UUID> ids = new ArrayList<>();
        Path dir = directory(server);
        if (!Files.isDirectory(dir)) return ids;
        try (var files = Files.list(dir)) {
            files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".dat"))
                    .forEach(name -> {
                        try {
                            ids.add(UUID.fromString(name.substring(0, name.length() - 4)));
                        } catch (IllegalArgumentException ignored) {
                            // Not one of ours.
                        }
                    });
        } catch (IOException failure) {
            Moveearth_addtional.LOGGER.warn("Could not list vehicle cargo records", failure);
        }
        return ids;
    }

    private static Path directory(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("moveearth").resolve("vehicle_cargo");
    }

    private static CompoundTag load(MinecraftServer server, UUID vehicleId) {
        Path path = directory(server).resolve(vehicleId + ".dat");
        if (!Files.isRegularFile(path)) return new CompoundTag();
        try {
            return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        } catch (IOException failure) {
            Moveearth_addtional.LOGGER.warn("Could not read the cargo record of vehicle {}", vehicleId, failure);
            return new CompoundTag();
        }
    }

    private static void write(MinecraftServer server, UUID vehicleId, CompoundTag file) {
        Path dir = directory(server);
        try {
            Files.createDirectories(dir);
            Path temp = dir.resolve(vehicleId + ".dat.tmp");
            NbtIo.writeCompressed(file, temp);
            Files.move(temp, dir.resolve(vehicleId + ".dat"), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            Moveearth_addtional.LOGGER.warn("Could not write the cargo record of vehicle {}", vehicleId, failure);
        }
    }
}
