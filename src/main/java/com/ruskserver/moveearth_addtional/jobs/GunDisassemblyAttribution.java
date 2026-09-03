package com.ruskserver.moveearth_addtional.jobs;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
            ENTRIES.put(new Key(level, pos.immutable()), new Entry(player.getUUID(), level.getGameTime()));
        }
    }

    public static UUID consume(ServerLevel level, BlockPos pos) {
        Entry entry = ENTRIES.remove(new Key(level, pos));
        if (entry == null || level.getGameTime() - entry.tick() > MAX_AGE_TICKS) return null;
        return entry.playerId();
    }

    public static void clear() { ENTRIES.clear(); }

    private record Key(ServerLevel level, BlockPos pos) {}
    private record Entry(UUID playerId, long tick) {}
}
