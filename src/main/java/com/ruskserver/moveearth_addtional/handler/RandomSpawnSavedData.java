package com.ruskserver.moveearth_addtional.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Persistent, bounded and lease-aware pool used by random spawning. */
public final class RandomSpawnSavedData extends SavedData {
    static final int MAX_CANDIDATES = 8_192;

    private final Map<Long, Entry> entries = new LinkedHashMap<>();
    private MappingState mapping;

    public List<BlockPos> positions() {
        return entries.values().stream().map(Entry::position).toList();
    }

    public List<BlockPos> availablePositions(long gameTime) {
        return entries.values().stream()
                .filter(entry -> entry.reservedUntil() <= gameTime)
                .filter(entry -> entry.cooldownUntil() <= gameTime)
                .map(Entry::position).toList();
    }

    public int size() { return entries.size(); }

    public void remember(BlockPos position) { remember(position, Source.PASSIVE, 0L); }

    public void remember(BlockPos position, Source source, long gameTime) {
        long key = columnKey(position.getX(), position.getZ());
        Entry previous = entries.get(key);
        if (previous != null) {
            Source effectiveSource = previous.source() == Source.MAPPED ? Source.MAPPED : source;
            if (previous.position().equals(position) && previous.source() == effectiveSource) return;
            entries.put(key, new Entry(position.immutable(), effectiveSource, gameTime,
                    previous.lastUsed(), previous.cooldownUntil(), previous.reservedUntil(), 0));
            setDirty();
            return;
        }
        if (entries.size() >= MAX_CANDIDATES) {
            Iterator<Long> iterator = entries.keySet().iterator();
            if (iterator.hasNext()) { iterator.next(); iterator.remove(); }
        }
        entries.put(key, new Entry(position.immutable(), source, gameTime, 0L, 0L, 0L, 0));
        setDirty();
    }

    public boolean reserve(BlockPos position, long gameTime, long leaseTicks) {
        long key = columnKey(position.getX(), position.getZ());
        Entry entry = entries.get(key);
        if (entry == null || entry.reservedUntil() > gameTime || entry.cooldownUntil() > gameTime) return false;
        entries.put(key, entry.withReservation(gameTime + Math.max(1L, leaseTicks)));
        setDirty();
        return true;
    }

    public void markUsed(BlockPos position, long gameTime, long cooldownTicks) {
        long key = columnKey(position.getX(), position.getZ());
        Entry entry = entries.get(key);
        if (entry == null) return;
        entries.put(key, entry.withUse(gameTime, gameTime + Math.max(0L, cooldownTicks)));
        setDirty();
    }

    public void forget(BlockPos position) { forgetColumn(position.getX(), position.getZ()); }

    public void forgetColumn(int x, int z) {
        if (entries.remove(columnKey(x, z)) != null) setDirty();
    }

    public int removeIf(Predicate<BlockPos> predicate) {
        int before = entries.size();
        entries.values().removeIf(entry -> predicate.test(entry.position()));
        int removed = before - entries.size();
        if (removed > 0) setDirty();
        return removed;
    }

    public boolean hasNearby(BlockPos position, double minimumDistanceSqr) {
        for (Entry entry : entries.values()) {
            double dx = entry.position().getX() - position.getX();
            double dz = entry.position().getZ() - position.getZ();
            if (dx * dx + dz * dz < minimumDistanceSqr) return true;
        }
        return false;
    }

    public MappingState mapping() { return mapping; }

    public void startMapping(boolean generate, int target, BlockPos center, int minimumRadius,
                             int maximumRadius) {
        mapping = new MappingState(UUID.randomUUID(), true, false, generate, Math.max(1, target),
                0, 0, 0, center.getX(), center.getZ(), minimumRadius, maximumRadius);
        setDirty();
    }

    public void updateMapping(MappingState state) { mapping = state; setDirty(); }

    public void clearMapping() {
        if (mapping != null) { mapping = null; setDirty(); }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("SchemaVersion", 2);
        ListTag list = new ListTag();
        for (Entry entry : entries.values()) {
            CompoundTag value = new CompoundTag();
            value.putLong("Pos", entry.position().asLong());
            value.putString("Source", entry.source().name());
            value.putLong("Validated", entry.validatedAt());
            value.putLong("LastUsed", entry.lastUsed());
            value.putLong("CooldownUntil", entry.cooldownUntil());
            value.putLong("ReservedUntil", entry.reservedUntil());
            value.putInt("Failures", entry.failures());
            list.add(value);
        }
        tag.put("Entries", list);
        if (mapping != null) tag.put("Mapping", mapping.save());
        return tag;
    }

    public static RandomSpawnSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RandomSpawnSavedData data = new RandomSpawnSavedData();
        if (tag.contains("Entries", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
            for (int index = 0; index < list.size() && data.entries.size() < MAX_CANDIDATES; index++) {
                CompoundTag value = list.getCompound(index);
                try {
                    BlockPos position = BlockPos.of(value.getLong("Pos"));
                    Source source = Source.valueOf(value.getString("Source"));
                    data.entries.put(columnKey(position.getX(), position.getZ()), new Entry(position, source,
                            Math.max(0L, value.getLong("Validated")), Math.max(0L, value.getLong("LastUsed")),
                            Math.max(0L, value.getLong("CooldownUntil")), Math.max(0L, value.getLong("ReservedUntil")),
                            Math.max(0, value.getInt("Failures"))));
                } catch (RuntimeException ignored) { }
            }
        } else {
            for (long packed : tag.getLongArray("Positions")) {
                if (data.entries.size() >= MAX_CANDIDATES) break;
                BlockPos position = BlockPos.of(packed);
                data.entries.putIfAbsent(columnKey(position.getX(), position.getZ()),
                        new Entry(position, Source.PASSIVE, 0L, 0L, 0L, 0L, 0));
            }
        }
        if (tag.contains("Mapping", Tag.TAG_COMPOUND)) data.mapping = MappingState.load(tag.getCompound("Mapping"));
        return data;
    }

    public static RandomSpawnSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RandomSpawnSavedData::new, RandomSpawnSavedData::load, null),
                "moveearth_random_spawn_pool");
    }

    private static long columnKey(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

    public enum Source { PASSIVE, MAPPED }

    public record Entry(BlockPos position, Source source, long validatedAt, long lastUsed,
                        long cooldownUntil, long reservedUntil, int failures) {
        Entry withReservation(long until) {
            return new Entry(position, source, validatedAt, lastUsed, cooldownUntil, until, failures);
        }
        Entry withUse(long usedAt, long cooldown) {
            return new Entry(position, source, validatedAt, usedAt, cooldown, 0L, failures);
        }
    }

    public record MappingState(UUID id, boolean active, boolean paused, boolean generate, int target,
                               int nextIndex, int checked, int accepted, int centerX, int centerZ,
                               int minimumRadius, int maximumRadius) {
        public MappingState advance(boolean acceptedPoint) {
            return new MappingState(id, active, paused, generate, target, nextIndex + 1,
                    checked + 1, accepted + (acceptedPoint ? 1 : 0), centerX, centerZ,
                    minimumRadius, maximumRadius);
        }
        public MappingState withPaused(boolean value) {
            return new MappingState(id, active, value, generate, target, nextIndex, checked, accepted,
                    centerX, centerZ, minimumRadius, maximumRadius);
        }
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id); tag.putBoolean("Active", active); tag.putBoolean("Paused", paused);
            tag.putBoolean("Generate", generate); tag.putInt("Target", target); tag.putInt("NextIndex", nextIndex);
            tag.putInt("Checked", checked); tag.putInt("Accepted", accepted); tag.putInt("CenterX", centerX);
            tag.putInt("CenterZ", centerZ); tag.putInt("MinimumRadius", minimumRadius);
            tag.putInt("MaximumRadius", maximumRadius); return tag;
        }
        static MappingState load(CompoundTag tag) {
            if (!tag.hasUUID("Id")) return null;
            return new MappingState(tag.getUUID("Id"), tag.getBoolean("Active"), tag.getBoolean("Paused"),
                    tag.getBoolean("Generate"), Math.max(1, tag.getInt("Target")), Math.max(0, tag.getInt("NextIndex")),
                    Math.max(0, tag.getInt("Checked")), Math.max(0, tag.getInt("Accepted")), tag.getInt("CenterX"),
                    tag.getInt("CenterZ"), Math.max(0, tag.getInt("MinimumRadius")),
                    Math.max(1, tag.getInt("MaximumRadius")));
        }
    }
}
