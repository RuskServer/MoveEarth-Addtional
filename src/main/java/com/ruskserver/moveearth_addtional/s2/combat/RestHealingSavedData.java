package com.ruskserver.moveearth_addtional.s2.combat;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent shared bed/campfire healing allowance and server-open period clock. */
public final class RestHealingSavedData extends SavedData {
    private long elapsedTicks;
    private final Map<UUID, Float> spentByPlayer = new LinkedHashMap<>();

    public void advance(long ticks, long periodTicks) {
        if (ticks <= 0L) return;
        RestHealingPolicy.Clock clock = RestHealingPolicy.advance(periodTicks, elapsedTicks, ticks);
        elapsedTicks = clock.elapsedTicks();
        if (clock.completedPeriods() > 0L) spentByPlayer.clear();
        setDirty();
    }

    public float remaining(UUID playerId, float maximum) {
        return RestHealingPolicy.remaining(maximum, spentByPlayer.getOrDefault(playerId, 0.0F));
    }

    public float consume(UUID playerId, float requested, float maximum) {
        float consumed = Math.min(Math.max(0.0F, requested), remaining(playerId, maximum));
        if (consumed <= 0.0F) return 0.0F;
        spentByPlayer.merge(playerId, consumed, Float::sum);
        setDirty();
        return consumed;
    }

    public long ticksUntilReset(long periodTicks) {
        return Math.max(1L, periodTicks) - Math.min(elapsedTicks, Math.max(1L, periodTicks) - 1L);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("ElapsedTicks", elapsedTicks);
        ListTag usage = new ListTag();
        for (Map.Entry<UUID, Float> entry : spentByPlayer.entrySet()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("Player", entry.getKey());
            value.putFloat("Spent", Math.max(0.0F, entry.getValue()));
            usage.add(value);
        }
        tag.put("Usage", usage);
        return tag;
    }

    public static RestHealingSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RestHealingSavedData data = new RestHealingSavedData();
        data.elapsedTicks = Math.max(0L, tag.getLong("ElapsedTicks"));
        ListTag usage = tag.getList("Usage", Tag.TAG_COMPOUND);
        for (int index = 0; index < usage.size(); index++) {
            CompoundTag value = usage.getCompound(index);
            if (!value.hasUUID("Player")) continue;
            float spent = Math.max(0.0F, value.getFloat("Spent"));
            if (spent > 0.0F) data.spentByPlayer.put(value.getUUID("Player"), spent);
        }
        return data;
    }

    public static RestHealingSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RestHealingSavedData::new, RestHealingSavedData::load, null),
                "moveearth_rest_healing");
    }
}
