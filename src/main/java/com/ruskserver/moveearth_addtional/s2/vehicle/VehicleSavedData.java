package com.ruskserver.moveearth_addtional.s2.vehicle;

import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Server-authoritative identities for nation-owned vehicle cores. */
public final class VehicleSavedData extends SavedData {
    private final Map<UUID, VehicleRecord> vehicles = new LinkedHashMap<>();
    private final Map<UUID, VehicleRepairPolicy.State> repairs = new LinkedHashMap<>();
    /** Core positions of {@link #vehicles}; kept in step by every method that adds, moves or drops one. */
    private final VehiclePositionIndex positions = new VehiclePositionIndex();

    public void recordHit(UUID id, long now) {
        if (!vehicles.containsKey(id)) return;
        repairs.put(id, repairs.getOrDefault(id, VehicleRepairPolicy.State.EMPTY)
                .hit(now, S2TerritoryConfig.vehicleRepairQuietTicks()));
        setDirty();
    }

    /** Combat and repair-interval state; {@link VehicleRepairPolicy.State#EMPTY} when never hit. */
    public VehicleRepairPolicy.State repairState(UUID id) {
        return id == null ? VehicleRepairPolicy.State.EMPTY
                : repairs.getOrDefault(id, VehicleRepairPolicy.State.EMPTY);
    }

    public VehicleRepairPolicy.Result repair(UUID id, long now) {
        VehicleRecord before = vehicles.get(id);
        if (before == null) return null;
        var result = VehicleRepairPolicy.repair(before.health(), before.maximumHealth(), now,
                repairs.getOrDefault(id, VehicleRepairPolicy.State.EMPTY),
                S2TerritoryConfig.vehicleEmergencyRepairHp(), S2TerritoryConfig.vehicleNormalRepairHp(),
                S2TerritoryConfig.vehicleEmergencyRepairCap(), S2TerritoryConfig.vehicleEmergencyRepairTicks(),
                S2TerritoryConfig.vehicleNormalRepairTicks());
        if (result.gain() > 0) {
            vehicles.put(id, new VehicleRecord(before.id(), before.nationId(), before.placedBy(), before.dimension(),
                    before.corePos(), before.subLevelId(), before.health() + result.gain(), before.maximumHealth()));
            repairs.put(id, result.state());
            setDirty();
        }
        return result;
    }

    public VehicleRecord register(UUID nationId, UUID placedBy, ResourceLocation dimension, BlockPos corePos) {
        VehicleRecord record = new VehicleRecord(UUID.randomUUID(), nationId, placedBy, dimension,
                corePos.immutable(), null, S2TerritoryConfig.vehicleCoreHealth(),
                S2TerritoryConfig.vehicleCoreHealth());
        vehicles.put(record.id(), record);
        index(record);
        setDirty();
        return record;
    }

    public Optional<VehicleRecord> vehicle(UUID id) {
        return Optional.ofNullable(id == null ? null : vehicles.get(id));
    }

    public Optional<VehicleRecord> at(ResourceLocation dimension, BlockPos pos) {
        if (dimension == null || pos == null) return Optional.empty();
        java.util.Set<UUID> ids = positions.at(dimension, pos.asLong());
        if (ids.isEmpty()) return Optional.empty();
        if (ids.size() == 1) {
            VehicleRecord only = vehicles.get(ids.iterator().next());
            return only != null && only.dimension().equals(dimension) && only.corePos().equals(pos)
                    ? Optional.of(only) : Optional.empty();
        }
        // Several records on one packed position: keep the registration-order winner of a full scan.
        for (VehicleRecord value : vehicles.values()) {
            if (ids.contains(value.id()) && value.dimension().equals(dimension) && value.corePos().equals(pos)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    public int count(UUID nationId) {
        return (int) vehicles.values().stream().filter(value -> value.nationId().equals(nationId)).count();
    }

    public VehicleRecord move(UUID id, ResourceLocation dimension, BlockPos corePos, UUID subLevelId) {
        VehicleRecord before = vehicles.get(id);
        if (before == null) return null;
        VehicleRecord after = new VehicleRecord(before.id(), before.nationId(), before.placedBy(), dimension,
                corePos.immutable(), subLevelId, before.health(), before.maximumHealth());
        vehicles.put(id, after);
        unindex(before);
        index(after);
        setDirty();
        return after;
    }

    public VehicleRecord damage(UUID id, int amount) {
        VehicleRecord before = vehicles.get(id);
        if (before == null || amount <= 0 || before.health() <= 0) return before;
        VehicleRecord after = new VehicleRecord(before.id(), before.nationId(), before.placedBy(),
                before.dimension(), before.corePos(), before.subLevelId(),
                VehicleCoreHealthPolicy.damage(before.health(), amount), before.maximumHealth());
        vehicles.put(id, after);
        setDirty();
        return after;
    }

    public void remove(UUID id) {
        repairs.remove(id);
        VehicleRecord removed = id == null ? null : vehicles.remove(id);
        if (removed != null) {
            unindex(removed);
            setDirty();
        }
    }

    public void removeNation(UUID nationId) {
        if (vehicles.values().removeIf(value -> value.nationId().equals(nationId))) {
            reindex();
            setDirty();
        }
        repairs.keySet().retainAll(vehicles.keySet());
    }

    private void index(VehicleRecord record) {
        positions.add(record.dimension(), record.corePos().asLong(), record.id());
    }

    private void unindex(VehicleRecord record) {
        positions.remove(record.dimension(), record.corePos().asLong(), record.id());
    }

    private void reindex() {
        positions.clear();
        for (VehicleRecord record : vehicles.values()) index(record);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (VehicleRecord record : vehicles.values()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("Id", record.id());
            value.putUUID("Nation", record.nationId());
            value.putUUID("PlacedBy", record.placedBy());
            value.putString("Dimension", record.dimension().toString());
            value.putLong("CorePos", record.corePos().asLong());
            if (record.subLevelId() != null) value.putUUID("SubLevel", record.subLevelId());
            value.putInt("Health", record.health());
            value.putInt("MaximumHealth", record.maximumHealth());
            var repair = repairs.getOrDefault(record.id(), VehicleRepairPolicy.State.EMPTY);
            value.putLong("CombatUntil", repair.combatUntil());
            value.putLong("NextRepairAt", repair.nextRepairAt());
            list.add(value);
        }
        tag.put("Vehicles", list);
        return tag;
    }

    public static VehicleSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        VehicleSavedData data = new VehicleSavedData();
        ListTag list = tag.getList("Vehicles", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag value = list.getCompound(index);
            if (!value.hasUUID("Id") || !value.hasUUID("Nation") || !value.hasUUID("PlacedBy")) continue;
            ResourceLocation dimension = ResourceLocation.tryParse(value.getString("Dimension"));
            if (dimension == null) continue;
            int maximum = Math.max(1, value.getInt("MaximumHealth"));
            VehicleRecord record = new VehicleRecord(value.getUUID("Id"), value.getUUID("Nation"),
                    value.getUUID("PlacedBy"), dimension, BlockPos.of(value.getLong("CorePos")),
                    value.hasUUID("SubLevel") ? value.getUUID("SubLevel") : null,
                    Math.max(0, Math.min(maximum, value.getInt("Health"))), maximum);
            data.vehicles.put(record.id(), record);
            data.index(record);
            data.repairs.put(record.id(), new VehicleRepairPolicy.State(
                    Math.max(0L, value.getLong("CombatUntil")), Math.max(0L, value.getLong("NextRepairAt"))));
        }
        return data;
    }

    public static VehicleSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VehicleSavedData::new, VehicleSavedData::load, null),
                "moveearth_vehicles");
    }

    public record VehicleRecord(UUID id, UUID nationId, UUID placedBy, ResourceLocation dimension,
                                BlockPos corePos, UUID subLevelId, int health, int maximumHealth) { }
}
