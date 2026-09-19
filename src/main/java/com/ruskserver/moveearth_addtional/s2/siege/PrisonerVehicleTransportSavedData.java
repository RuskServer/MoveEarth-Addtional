package com.ruskserver.moveearth_addtional.s2.siege;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent binding only; the captivity deadline remains owned by PrisonerSavedData. */
public final class PrisonerVehicleTransportSavedData extends SavedData {
    private final Map<UUID, Transport> byCaptive = new LinkedHashMap<>();

    public Transport get(UUID captive) { return byCaptive.get(captive); }
    public java.util.List<Transport> all() { return java.util.List.copyOf(byCaptive.values()); }

    public boolean load(UUID captive, UUID captor, UUID vehicle) {
        if (captive == null || captor == null || vehicle == null || byCaptive.containsKey(captive)
                || byCaptive.values().stream().anyMatch(value -> value.vehicleId().equals(vehicle))) return false;
        byCaptive.put(captive, new Transport(captive, captor, vehicle));
        setDirty();
        return true;
    }

    public Transport unload(UUID captive) {
        Transport removed = byCaptive.remove(captive);
        if (removed != null) setDirty();
        return removed;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Transport value : byCaptive.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Captive", value.captiveId());
            entry.putUUID("Captor", value.captorId());
            entry.putUUID("Vehicle", value.vehicleId());
            list.add(entry);
        }
        tag.put("Transports", list);
        return tag;
    }

    public static PrisonerVehicleTransportSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PrisonerVehicleTransportSavedData data = new PrisonerVehicleTransportSavedData();
        ListTag list = tag.getList("Transports", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Captive") || !entry.hasUUID("Captor") || !entry.hasUUID("Vehicle")) continue;
            Transport value = new Transport(entry.getUUID("Captive"), entry.getUUID("Captor"),
                    entry.getUUID("Vehicle"));
            data.byCaptive.put(value.captiveId(), value);
        }
        return data;
    }

    public static PrisonerVehicleTransportSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PrisonerVehicleTransportSavedData::new,
                        PrisonerVehicleTransportSavedData::load, null), "moveearth_prisoner_vehicle_transport");
    }

    public record Transport(UUID captiveId, UUID captorId, UUID vehicleId) { }
}
