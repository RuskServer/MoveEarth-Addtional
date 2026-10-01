package com.ruskserver.moveearth_addtional.s2.territory;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.time.OpenTimeService;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * When each core entered configuring, in server-open ticks, so the reservation
 * can lapse ({@link ConfiguringReservationPolicy}). Kept beside the territory
 * data rather than in its records: a sweep every 20 seconds starts the clock for
 * new configuring cores and forgets cores that left the state.
 *
 * <p>Outposts also leave a {@link ConfiguringReservationPolicy.Claim} on their land.
 * An outpost removed while configuring keeps its claim for
 * {@link ConfiguringReservationPolicy#REUSE_COOLDOWN_OPEN_TICKS}, and every outpost
 * counts its hour from the earliest claim of its nation on overlapping land, so a
 * break and re-place (new core id) or a neighbouring outpost does not restart it.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ConfiguringReservationSavedData extends SavedData {
    private final Map<UUID, Long> since = new HashMap<>();
    /** Land of the outposts now configuring, by core id, as of the last sweep. */
    private final Map<UUID, ConfiguringReservationPolicy.Claim> claims = new HashMap<>();
    /** Land of outposts removed while configuring, with the open time they were removed. */
    private final List<Retired> retired = new ArrayList<>();

    /** Whether the core's configuring reservation still holds. */
    public static boolean live(MinecraftServer server, TerritorySavedData.CoreRecord core) {
        return get(server).liveAt(core, OpenTimeService.now(server));
    }

    boolean liveAt(TerritorySavedData.CoreRecord core, long now) {
        Long start = since.get(core.id());
        if (start == null) {
            // Not swept yet. A capital is free until then; an outpost already answers for
            // any earlier claim on its land, so a re-placed one is not live for those seconds.
            if (core.type() != TerritorySavedData.CoreType.OUTPOST) return true;
            start = outpostStart(core, now, now);
        }
        return ConfiguringReservationPolicy.live(start, now);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % 400L != 11L) return;
        get(server).sweep(TerritorySavedData.get(server), OpenTimeService.now(server));
    }

    void sweep(TerritorySavedData territories, long now) {
        Map<UUID, TerritorySavedData.CoreRecord> configuring = new HashMap<>();
        Set<UUID> existing = new HashSet<>();
        for (TerritorySavedData.CoreRecord core : territories.cores()) {
            existing.add(core.id());
            if (core.state() == TerritorySavedData.CoreState.CONFIGURING) configuring.put(core.id(), core);
        }
        boolean changed = false;
        // An outpost gone while still configuring leaves its land tied to its start;
        // one that became active (or anything else) simply stops being tracked.
        for (var iterator = claims.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            if (configuring.containsKey(entry.getKey())) continue;
            if (!existing.contains(entry.getKey())) retired.add(new Retired(entry.getValue(), now));
            iterator.remove();
            changed = true;
        }
        changed |= retired.removeIf(value -> !ConfiguringReservationPolicy.retained(value.removedAt(), now));
        changed |= since.keySet().retainAll(configuring.keySet());
        for (TerritorySavedData.CoreRecord core : configuring.values()) {
            Long previous = since.get(core.id());
            long start = previous == null ? now : previous;
            if (core.type() == TerritorySavedData.CoreType.OUTPOST) {
                // Re-checked every sweep, so widening the radius over older land also counts.
                start = outpostStart(core, start, now);
                ConfiguringReservationPolicy.Claim claim = new ConfiguringReservationPolicy.Claim(core.nationId(),
                        core.dimension().toString(), TerritorySavedData.area(core.pos(), core.radius()), start);
                if (!claim.equals(claims.put(core.id(), claim))) changed = true;
            }
            if (previous == null || previous != start) {
                since.put(core.id(), start);
                changed = true;
            }
        }
        if (changed) setDirty();
    }

    /** The earliest start among this nation's other claims on overlapping land, never later than {@code ownStart}. */
    private long outpostStart(TerritorySavedData.CoreRecord core, long ownStart, long now) {
        List<ConfiguringReservationPolicy.Claim> others = new ArrayList<>();
        claims.forEach((id, claim) -> { if (!id.equals(core.id())) others.add(claim); });
        for (Retired value : retired) {
            if (ConfiguringReservationPolicy.retained(value.removedAt(), now)) others.add(value.claim());
        }
        return ConfiguringReservationPolicy.start(core.nationId(), core.dimension().toString(),
                TerritorySavedData.area(core.pos(), core.radius()), ownStart, others);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag entries = new CompoundTag();
        since.forEach((id, start) -> entries.putLong(id.toString(), start));
        tag.put("Since", entries);
        ListTag claimList = new ListTag();
        claims.forEach((id, claim) -> {
            CompoundTag value = saveClaim(claim);
            value.putUUID("Core", id);
            claimList.add(value);
        });
        tag.put("Claims", claimList);
        ListTag retiredList = new ListTag();
        for (Retired value : retired) {
            CompoundTag entry = saveClaim(value.claim());
            entry.putLong("RemovedAt", value.removedAt());
            retiredList.add(entry);
        }
        tag.put("Retired", retiredList);
        return tag;
    }

    private static CompoundTag saveClaim(ConfiguringReservationPolicy.Claim claim) {
        CompoundTag value = new CompoundTag();
        value.putUUID("Nation", claim.nationId());
        value.putString("Dimension", claim.dimension());
        value.putInt("ChunkX", claim.area().centerChunkX());
        value.putInt("ChunkZ", claim.area().centerChunkZ());
        value.putInt("Radius", claim.area().radius());
        value.putLong("Since", claim.since());
        return value;
    }

    /** Null for a malformed entry. */
    private static ConfiguringReservationPolicy.Claim loadClaim(CompoundTag value) {
        if (!value.hasUUID("Nation") || value.getString("Dimension").isEmpty()) return null;
        int radius = value.getInt("Radius");
        if (radius < TerritoryPreviewArea.MIN_RADIUS || radius > TerritoryPreviewArea.MAX_RADIUS) return null;
        return new ConfiguringReservationPolicy.Claim(value.getUUID("Nation"), value.getString("Dimension"),
                new TerritoryPreviewArea(value.getInt("ChunkX"), value.getInt("ChunkZ"), radius),
                value.getLong("Since"));
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
        // Saves from before claims carry neither list; the next sweep records claims for the
        // outposts still configuring.
        ListTag claimList = tag.getList("Claims", Tag.TAG_COMPOUND);
        for (int index = 0; index < claimList.size(); index++) {
            CompoundTag value = claimList.getCompound(index);
            ConfiguringReservationPolicy.Claim claim = loadClaim(value);
            if (claim != null && value.hasUUID("Core")) data.claims.put(value.getUUID("Core"), claim);
        }
        ListTag retiredList = tag.getList("Retired", Tag.TAG_COMPOUND);
        for (int index = 0; index < retiredList.size(); index++) {
            CompoundTag value = retiredList.getCompound(index);
            ConfiguringReservationPolicy.Claim claim = loadClaim(value);
            if (claim != null) data.retired.add(new Retired(claim, value.getLong("RemovedAt")));
        }
        return data;
    }

    public static ConfiguringReservationSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ConfiguringReservationSavedData::new, ConfiguringReservationSavedData::load, null),
                "moveearth_configuring_reservations");
    }

    private record Retired(ConfiguringReservationPolicy.Claim claim, long removedAt) { }
}
