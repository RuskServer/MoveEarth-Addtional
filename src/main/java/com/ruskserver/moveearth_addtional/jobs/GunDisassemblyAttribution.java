package com.ruskserver.moveearth_addtional.jobs;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Short-lived ownership records for guns sent to Create crushing wheels. */
public final class GunDisassemblyAttribution {
    private static final long MAX_AGE_TICKS = 200L;
    private static final Map<Key, Entry> ENTRIES = new HashMap<>();

    private GunDisassemblyAttribution() {}

    public static void record(ServerLevel level, BlockPos pos, ItemEntity entity, ItemStack stack) {
        if (entity.getOwner() instanceof ServerPlayer player
                && com.tacz.guns.api.item.IGun.getIGunOrNull(stack) != null) {
            purge(level.getGameTime(), level.dimension());
            ENTRIES.put(new Key(level.dimension(), pos.immutable()), new Entry(player.getUUID(), level.getGameTime()));
        }
    }

    public static UUID consume(ServerLevel level, BlockPos pos) {
        purge(level.getGameTime(), level.dimension());
        Entry entry = ENTRIES.remove(new Key(level.dimension(), pos));
        if (entry == null || level.getGameTime() - entry.tick() > MAX_AGE_TICKS) return null;
        return entry.playerId();
    }

    public static void clear() { ENTRIES.clear(); }

    public static void purge(MinecraftServer server) {
        ENTRIES.entrySet().removeIf(entry -> {
            ServerLevel level = server.getLevel(entry.getKey().dimension());
            return level == null || level.getGameTime() - entry.getValue().tick() > MAX_AGE_TICKS;
        });
    }

    private static void purge(long now, ResourceKey<Level> dimension) {
        ENTRIES.entrySet().removeIf(entry -> entry.getKey().dimension().equals(dimension)
                && now - entry.getValue().tick() > MAX_AGE_TICKS);
    }

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {}
    private record Entry(UUID playerId, long tick) {}
}
