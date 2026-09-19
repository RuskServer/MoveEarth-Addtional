package com.ruskserver.moveearth_addtional.s2.vehicle;

import com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeLootPolicy;
import com.ruskserver.moveearth_addtional.s2.time.OpenTimeService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Time-limited cargo rights created exactly when a vehicle core reaches zero HP. */
public final class VehicleLootSavedData extends SavedData {
    private final Map<UUID, Grant> grants = new LinkedHashMap<>();

    public void open(UUID vehicleId, UUID ownerNation, UUID attackerId, boolean individual,
                     long nowOpenTick) {
        if (vehicleId == null || ownerNation == null || attackerId == null || ownerNation.equals(attackerId)) return;
        grants.put(vehicleId, new Grant(vehicleId, ownerNation, attackerId, individual,
                Math.max(0L, nowOpenTick) + SiegeLootPolicy.FINAL_WINDOW_TICKS));
        setDirty();
    }

    public boolean canLoot(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SableVehicleTopology.VehicleContext context;
        try { context = SableVehicleTopology.at(level, pos).orElse(null); }
        catch (RuntimeException | LinkageError ignored) { return false; }
        if (context == null || context.vehicle().health() > 0) return false;
        Grant grant = grants.get(context.vehicle().id());
        if (grant == null) return false;
        long now = OpenTimeService.now(player.server);
        if (now >= grant.expiresOpenTick()) {
            grants.remove(grant.vehicleId());
            setDirty();
            return false;
        }
        UUID nation = NationSavedData.get(player.server).nationIdFor(player.getUUID()).orElse(null);
        return grant.individualAttacker() ? grant.attackerId().equals(player.getUUID())
                : grant.attackerId().equals(nation);
    }

    public Grant grant(UUID vehicleId) { return grants.get(vehicleId); }

    public void revoke(UUID vehicleId) {
        if (vehicleId != null && grants.remove(vehicleId) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Grant value : grants.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Vehicle", value.vehicleId());
            entry.putUUID("Owner", value.ownerNation());
            entry.putUUID("Attacker", value.attackerId());
            entry.putBoolean("Individual", value.individualAttacker());
            entry.putLong("ExpiresOpenTick", value.expiresOpenTick());
            list.add(entry);
        }
        tag.put("Grants", list);
        return tag;
    }

    public static VehicleLootSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        VehicleLootSavedData data = new VehicleLootSavedData();
        ListTag list = tag.getList("Grants", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Vehicle") || !entry.hasUUID("Owner") || !entry.hasUUID("Attacker")) continue;
            Grant value = new Grant(entry.getUUID("Vehicle"), entry.getUUID("Owner"),
                    entry.getUUID("Attacker"), entry.getBoolean("Individual"),
                    Math.max(0L, entry.getLong("ExpiresOpenTick")));
            data.grants.put(value.vehicleId(), value);
        }
        return data;
    }

    public static VehicleLootSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VehicleLootSavedData::new, VehicleLootSavedData::load, null),
                "moveearth_vehicle_loot");
    }

    public record Grant(UUID vehicleId, UUID ownerNation, UUID attackerId,
                        boolean individualAttacker, long expiresOpenTick) { }
}
