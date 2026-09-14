package com.ruskserver.moveearth_addtional.s2.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistent PvP tags and offline-body restoration state. */
public final class CombatTagSavedData extends SavedData {
    private final Map<UUID, CombatState> states = new LinkedHashMap<>();

    public void tag(UUID player, UUID opponent, long ticks) {
        if (player == null || ticks <= 0L) return;
        CombatState old = states.get(player);
        CombatState next = old == null
                ? new CombatState(player, opponent, ticks, null, null, 20.0F, false, false, false, null)
                : new CombatState(player, opponent == null ? old.opponent : opponent,
                Math.max(old.remainingTicks, ticks), old.dimension, old.position, old.health,
                old.disconnected, old.downed, old.killed, old.bodyEntity);
        states.put(player, next);
        setDirty();
    }

    public Optional<CombatState> state(UUID player) { return Optional.ofNullable(states.get(player)); }

    public boolean isTagged(UUID player) {
        CombatState state = states.get(player);
        return state != null && state.remainingTicks > 0L;
    }

    public void disconnect(UUID player, ResourceLocation dimension, BlockPos position,
                           float health, boolean downed, UUID bodyEntity) {
        CombatState old = states.get(player);
        if (old == null) return;
        states.put(player, new CombatState(player, old.opponent, old.remainingTicks, dimension,
                position.immutable(), Math.max(0.0F, health), true, downed, false, bodyEntity));
        setDirty();
    }

    public void updateBody(UUID player, ResourceLocation dimension, BlockPos position,
                           float health, boolean downed, boolean killed) {
        CombatState old = states.get(player);
        if (old == null) return;
        states.put(player, new CombatState(player, old.opponent, old.remainingTicks,
                dimension == null ? old.dimension : dimension,
                position == null ? old.position : position.immutable(), Math.max(0.0F, health),
                old.disconnected, downed, killed, old.bodyEntity));
        setDirty();
    }

    public CombatState consume(UUID player) {
        CombatState removed = states.remove(player);
        if (removed != null) setDirty();
        return removed;
    }

    public CombatState detachBody(UUID player, ResourceLocation dimension, BlockPos position,
                                  float health, boolean keepForLogin) {
        CombatState old = states.get(player);
        if (old == null) return null;
        if (keepForLogin && old.disconnected) {
            states.put(player, new CombatState(player, old.opponent, 0L,
                    dimension == null ? old.dimension : dimension,
                    position == null ? old.position : position.immutable(), Math.max(1.0F, health),
                    true, false, false, null));
            setDirty();
        } else {
            consume(player);
        }
        return old;
    }

    public List<CombatState> advance(long ticks) {
        if (ticks <= 0L) return List.of();
        List<CombatState> expiredBodies = new ArrayList<>();
        boolean changed = false;
        for (var entry : new ArrayList<>(states.entrySet())) {
            CombatState old = entry.getValue();
            if (old.killed || old.remainingTicks <= 0L) continue;
            long remaining = Math.max(0L, old.remainingTicks - ticks);
            CombatState next = new CombatState(old.player, old.opponent, remaining, old.dimension,
                    old.position, old.health, old.disconnected, old.downed,
                    old.killed || old.downed && remaining == 0L,
                    remaining == 0L ? null : old.bodyEntity);
            states.put(entry.getKey(), next);
            if (remaining == 0L) {
                if (old.bodyEntity != null) expiredBodies.add(old);
                if (!old.disconnected) states.remove(entry.getKey());
            }
            changed = true;
        }
        if (changed) setDirty();
        return expiredBodies;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (CombatState state : states.values()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("Player", state.player);
            if (state.opponent != null) value.putUUID("Opponent", state.opponent);
            value.putLong("Remaining", state.remainingTicks);
            if (state.dimension != null) value.putString("Dimension", state.dimension.toString());
            if (state.position != null) value.putLong("Position", state.position.asLong());
            value.putFloat("Health", state.health);
            value.putBoolean("Disconnected", state.disconnected);
            value.putBoolean("Downed", state.downed);
            value.putBoolean("Killed", state.killed);
            if (state.bodyEntity != null) value.putUUID("Body", state.bodyEntity);
            list.add(value);
        }
        tag.put("States", list);
        return tag;
    }

    public static CombatTagSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        CombatTagSavedData data = new CombatTagSavedData();
        ListTag list = tag.getList("States", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag value = list.getCompound(i);
            if (!value.hasUUID("Player")) continue;
            ResourceLocation dimension = value.contains("Dimension")
                    ? ResourceLocation.tryParse(value.getString("Dimension")) : null;
            BlockPos position = value.contains("Position") ? BlockPos.of(value.getLong("Position")) : null;
            UUID player = value.getUUID("Player");
            data.states.put(player, new CombatState(player,
                    value.hasUUID("Opponent") ? value.getUUID("Opponent") : null,
                    Math.max(0L, value.getLong("Remaining")), dimension, position,
                    Math.max(0.0F, value.getFloat("Health")), value.getBoolean("Disconnected"),
                    value.getBoolean("Downed"), value.getBoolean("Killed"),
                    value.hasUUID("Body") ? value.getUUID("Body") : null));
        }
        return data;
    }

    public static CombatTagSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CombatTagSavedData::new, CombatTagSavedData::load, null),
                "moveearth_combat_tags");
    }

    public record CombatState(UUID player, UUID opponent, long remainingTicks,
                              ResourceLocation dimension, BlockPos position, float health,
                              boolean disconnected, boolean downed, boolean killed, UUID bodyEntity) { }
}
