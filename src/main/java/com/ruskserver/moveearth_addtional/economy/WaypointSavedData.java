package com.ruskserver.moveearth_addtional.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** One persistent, server-owned navigation target per player, independent of the market. */
public final class WaypointSavedData extends SavedData {
    private final Map<UUID, Waypoint> active = new HashMap<>();

    public Optional<Waypoint> active(UUID player) { return Optional.ofNullable(active.get(player)); }

    public void set(UUID player, Waypoint waypoint) {
        if (player == null || waypoint == null) return;
        active.put(player, waypoint);
        setDirty();
    }

    public void clear(UUID player) {
        if (active.remove(player) != null) setDirty();
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        active.forEach((player, waypoint) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", player);
            entry.putString("Name", waypoint.name());
            entry.putString("Dimension", waypoint.dimension().toString());
            entry.putLong("Pos", waypoint.pos().asLong());
            entry.putBoolean("Market", waypoint.market());
            list.add(entry);
        });
        tag.put("Active", list);
        return tag;
    }

    public static WaypointSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        WaypointSavedData data = new WaypointSavedData();
        ListTag list = tag.getList("Active", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Player")) continue;
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            if (dimension == null) continue;
            data.active.put(entry.getUUID("Player"), new Waypoint(entry.getString("Name"),
                    dimension, BlockPos.of(entry.getLong("Pos")), entry.getBoolean("Market")));
        }
        return data;
    }

    public static WaypointSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WaypointSavedData::new, WaypointSavedData::load, null),
                "moveearth_waypoints");
    }

    public record Waypoint(String name, ResourceLocation dimension, BlockPos pos, boolean market) {
        public Waypoint(String name, ResourceLocation dimension, BlockPos pos) {
            this(name, dimension, pos, false);
        }
        public Waypoint {
            if (dimension == null || pos == null) throw new IllegalArgumentException("Incomplete waypoint");
            name = name == null || name.isBlank() ? "目的地" : name.strip();
            if (name.length() > 48) name = name.substring(0, 48);
            pos = pos.immutable();
        }
    }
}
