package com.ruskserver.moveearth_addtional.s2.territory;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.time.OpenTimeService;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * When each core entered configuring, in server-open ticks, so the reservation
 * can lapse ({@link ConfiguringReservationPolicy}). Kept beside the territory
 * data rather than in its records: a sweep every 20 seconds starts the clock for
 * new configuring cores and forgets cores that left the state.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ConfiguringReservationSavedData extends SavedData {
    private final Map<UUID, Long> since = new HashMap<>();

    /** Whether the core's configuring reservation still holds; true until the first sweep has seen it. */
    public static boolean live(MinecraftServer server, UUID coreId) {
        Long start = get(server).since.get(coreId);
        return start == null || ConfiguringReservationPolicy.live(start, OpenTimeService.now(server));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % 400L != 11L) return;
        get(server).sweep(TerritorySavedData.get(server), OpenTimeService.now(server));
    }

    void sweep(TerritorySavedData territories, long now) {
        Set<UUID> configuring = new HashSet<>();
        for (TerritorySavedData.CoreRecord core : territories.cores()) {
            if (core.state() == TerritorySavedData.CoreState.CONFIGURING) configuring.add(core.id());
        }
        boolean changed = since.keySet().retainAll(configuring);
        for (UUID id : configuring) {
            if (since.putIfAbsent(id, now) == null) changed = true;
        }
        if (changed) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag entries = new CompoundTag();
        since.forEach((id, start) -> entries.putLong(id.toString(), start));
        tag.put("Since", entries);
        return tag;
    }

    public static ConfiguringReservationSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ConfiguringReservationSavedData data = new ConfiguringReservationSavedData();
        CompoundTag entries = tag.getCompound("Since");
        for (String key : entries.getAllKeys()) {
            try {
                data.since.put(UUID.fromString(key), entries.getLong(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    public static ConfiguringReservationSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ConfiguringReservationSavedData::new, ConfiguringReservationSavedData::load, null),
                "moveearth_configuring_reservations");
    }
}
