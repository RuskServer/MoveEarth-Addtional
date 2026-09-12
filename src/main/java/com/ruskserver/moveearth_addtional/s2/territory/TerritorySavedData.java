package com.ruskserver.moveearth_addtional.s2.territory;

import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Server-authoritative registry of placed territory cores and reserved chunk areas. */
public final class TerritorySavedData extends SavedData {
    private final Map<CoreKey, CoreRecord> cores = new LinkedHashMap<>();

    public RegistrationResult register(UUID nationId, UUID placedBy, ResourceLocation dimension,
                                       BlockPos pos, int radius) {
        CoreKey key = new CoreKey(dimension, pos.immutable());
        CoreRecord existing = cores.get(key);
        if (existing != null) {
            return existing.nationId.equals(nationId)
                    ? new RegistrationResult(Status.REGISTERED, existing)
                    : new RegistrationResult(Status.POSITION_OCCUPIED, null);
        }
        TerritoryPreviewArea proposed = area(pos, radius);
        if (conflicts(nationId, dimension, proposed, null)) {
            return new RegistrationResult(Status.FOREIGN_TERRITORY_CONFLICT, null);
        }
        CoreType type = cores.values().stream().anyMatch(core -> core.nationId.equals(nationId))
                ? CoreType.OUTPOST : CoreType.CAPITAL;
        int maximumHealth = maximumHealth(type);
        CoreRecord record = new CoreRecord(UUID.randomUUID(), nationId, placedBy, dimension,
                pos.immutable(), type, clampRadius(radius), CoreState.CONFIGURING,
                maximumHealth, maximumHealth, 0L, 0L);
        cores.put(key, record);
        setDirty();
        return new RegistrationResult(Status.REGISTERED, record);
    }

    public UpdateResult updateRadius(UUID nationId, ResourceLocation dimension, BlockPos pos, int radius) {
        if (radius < TerritoryPreviewArea.MIN_RADIUS || radius > TerritoryPreviewArea.MAX_RADIUS) {
            return new UpdateResult(Status.INVALID_RADIUS, null);
        }
        CoreKey key = new CoreKey(dimension, pos.immutable());
        CoreRecord current = cores.get(key);
        if (current == null || !current.nationId.equals(nationId)) {
            return new UpdateResult(Status.NOT_FOUND, null);
        }
        TerritoryPreviewArea proposed = area(pos, radius);
        if (conflicts(nationId, dimension, proposed, key)) {
            return new UpdateResult(Status.FOREIGN_TERRITORY_CONFLICT, current);
        }
        CoreRecord updated = new CoreRecord(current.id, current.nationId, current.placedBy,
                current.dimension, current.pos, current.type, radius, CoreState.CONFIGURING,
                current.health, current.maximumHealth, current.regenDelayTicks, current.regenProgressTicks);
        cores.put(key, updated);
        setDirty();
        return new UpdateResult(Status.UPDATED, updated);
    }

    public Optional<CoreRecord> core(ResourceLocation dimension, BlockPos pos) {
        return Optional.ofNullable(cores.get(new CoreKey(dimension, pos)));
    }

    public Optional<CoreRecord> updateState(UUID nationId, ResourceLocation dimension,
                                            BlockPos pos, CoreState state) {
        CoreKey key = new CoreKey(dimension, pos);
        CoreRecord current = cores.get(key);
        if (current == null || !current.nationId.equals(nationId)) return Optional.empty();
        if (current.state == state) return Optional.of(current);
        CoreRecord updated = new CoreRecord(current.id, current.nationId, current.placedBy,
                current.dimension, current.pos, current.type, current.radius, state,
                current.health, current.maximumHealth, current.regenDelayTicks, current.regenProgressTicks);
        cores.put(key, updated);
        setDirty();
        return Optional.of(updated);
    }

    public List<CoreRecord> coresNear(ResourceLocation dimension, BlockPos pos, int range) {
        int safeRange = Math.max(0, range);
        return cores.values().stream()
                .filter(core -> core.dimension.equals(dimension))
                .filter(core -> Math.abs(core.pos.getX() - pos.getX()) <= safeRange)
                .filter(core -> Math.abs(core.pos.getY() - pos.getY()) <= safeRange)
                .filter(core -> Math.abs(core.pos.getZ() - pos.getZ()) <= safeRange)
                .toList();
    }

    public List<CoreRecord> cores() {
        return List.copyOf(cores.values());
    }

    public Optional<CoreRecord> damageCore(ResourceLocation dimension, BlockPos pos, int amount) {
        CoreKey key = new CoreKey(dimension, pos);
        CoreRecord current = cores.get(key);
        if (current == null || amount <= 0 || current.health <= 0) return Optional.ofNullable(current);
        int health = TerritoryCoreHealthPolicy.damage(current.health, amount);
        CoreRecord updated = new CoreRecord(current.id, current.nationId, current.placedBy,
                current.dimension, current.pos, current.type, current.radius, current.state,
                health, current.maximumHealth, S2TerritoryConfig.coreRegenDelayTicks(), 0L);
        cores.put(key, updated);
        setDirty();
        return Optional.of(updated);
    }

    /** Advances health using server-open ticks only; depleted cores await the Siege state machine. */
    public List<CoreRecord> advanceCoreRegeneration(long elapsedTicks) {
        if (elapsedTicks <= 0L) return List.of();
        List<CoreRecord> changed = new java.util.ArrayList<>();
        boolean persistenceChanged = false;
        for (Map.Entry<CoreKey, CoreRecord> value : cores.entrySet()) {
            CoreRecord current = value.getValue();
            if (current.state == CoreState.CONFIGURING || current.health <= 0) continue;
            int configuredMaximum = maximumHealth(current.type);
            var result = TerritoryCoreHealthPolicy.advance(current.health, configuredMaximum,
                    current.regenDelayTicks, current.regenProgressTicks, elapsedTicks,
                    S2TerritoryConfig.coreRegenIntervalTicks(),
                    S2TerritoryConfig.coreRegenPercent());
            if (result.health() == current.health && result.delayTicks() == current.regenDelayTicks
                    && result.progressTicks() == current.regenProgressTicks
                    && configuredMaximum == current.maximumHealth) continue;
            CoreRecord updated = new CoreRecord(current.id, current.nationId, current.placedBy,
                    current.dimension, current.pos, current.type, current.radius, current.state,
                    result.health(), configuredMaximum, result.delayTicks(), result.progressTicks());
            value.setValue(updated);
            persistenceChanged = true;
            if (updated.health != current.health || updated.maximumHealth != current.maximumHealth) {
                changed.add(updated);
            }
        }
        if (persistenceChanged) setDirty();
        return List.copyOf(changed);
    }

    public boolean controlsChunk(UUID nationId, ResourceLocation dimension, BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        return cores.values().stream()
                .filter(core -> core.nationId.equals(nationId) && core.dimension.equals(dimension))
                .filter(core -> core.state == CoreState.ACTIVE || core.state == CoreState.EXPOSED)
                .map(core -> area(core.pos, core.radius))
                .anyMatch(area -> area.containsChunk(chunk.x, chunk.z));
    }

    /** Returns the nation whose ACTIVE or EXPOSED territory controls this chunk. */
    public Optional<UUID> controllingNation(ResourceLocation dimension, BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        return cores.values().stream()
                .filter(core -> core.dimension.equals(dimension))
                .filter(core -> core.state == CoreState.ACTIVE || core.state == CoreState.EXPOSED)
                .filter(core -> area(core.pos, core.radius).containsChunk(chunk.x, chunk.z))
                .map(CoreRecord::nationId)
                .findFirst();
    }

    public void remove(ResourceLocation dimension, BlockPos pos, UUID expectedCoreId) {
        CoreKey key = new CoreKey(dimension, pos);
        CoreRecord current = cores.get(key);
        if (current != null && (expectedCoreId == null || current.id.equals(expectedCoreId))) {
            cores.remove(key);
            setDirty();
        }
    }

    public int coreCount(UUID nationId) {
        return (int) cores.values().stream().filter(core -> core.nationId.equals(nationId)).count();
    }

    public int reservedChunkCount(UUID nationId) {
        return reservedChunks(nationId).size();
    }

    public int controlledChunkCount(UUID nationId) {
        return controlledChunks(nationId).size();
    }

    public int controlledCoreCount(UUID nationId) {
        return (int) cores.values().stream()
                .filter(core -> core.nationId.equals(nationId) && isControlled(core))
                .count();
    }

    public int activeOutpostCount(UUID nationId) {
        return (int) cores.values().stream()
                .filter(core -> core.nationId.equals(nationId) && isControlled(core))
                .filter(core -> core.type == CoreType.OUTPOST)
                .count();
    }

    public boolean ownsChunk(UUID nationId, ResourceLocation dimension, BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        return cores.values().stream()
                .filter(core -> core.nationId.equals(nationId) && core.dimension.equals(dimension))
                .map(core -> area(core.pos, core.radius))
                .anyMatch(area -> area.containsChunk(chunk.x, chunk.z));
    }

    private Set<ReservedChunk> reservedChunks(UUID nationId) {
        return cores.values().stream().filter(core -> core.nationId.equals(nationId))
                .flatMap(core -> {
                    TerritoryPreviewArea area = area(core.pos, core.radius);
                    java.util.List<ReservedChunk> chunks = new java.util.ArrayList<>(area.chunkCount());
                    for (int x = area.minChunkX(); x <= area.maxChunkX(); x++) {
                        for (int z = area.minChunkZ(); z <= area.maxChunkZ(); z++) {
                            chunks.add(new ReservedChunk(core.dimension, x, z));
                        }
                    }
                    return chunks.stream();
                }).collect(Collectors.toSet());
    }

    private Set<ReservedChunk> controlledChunks(UUID nationId) {
        return cores.values().stream()
                .filter(core -> core.nationId.equals(nationId) && isControlled(core))
                .flatMap(core -> {
                    TerritoryPreviewArea area = area(core.pos, core.radius);
                    java.util.List<ReservedChunk> chunks = new java.util.ArrayList<>(area.chunkCount());
                    for (int x = area.minChunkX(); x <= area.maxChunkX(); x++) {
                        for (int z = area.minChunkZ(); z <= area.maxChunkZ(); z++) {
                            chunks.add(new ReservedChunk(core.dimension, x, z));
                        }
                    }
                    return chunks.stream();
                }).collect(Collectors.toSet());
    }

    private static boolean isControlled(CoreRecord core) {
        return core.state == CoreState.ACTIVE || core.state == CoreState.EXPOSED;
    }

    private boolean conflicts(UUID nationId, ResourceLocation dimension,
                              TerritoryPreviewArea proposed, CoreKey ignored) {
        return cores.entrySet().stream()
                .filter(entry -> ignored == null || !entry.getKey().equals(ignored))
                .map(Map.Entry::getValue)
                .filter(core -> !core.nationId.equals(nationId) && core.dimension.equals(dimension))
                .map(core -> area(core.pos, core.radius))
                .anyMatch(proposed::overlaps);
    }

    private static TerritoryPreviewArea area(BlockPos pos, int radius) {
        ChunkPos chunk = new ChunkPos(pos);
        return new TerritoryPreviewArea(chunk.x, chunk.z, clampRadius(radius));
    }

    private static int clampRadius(int radius) {
        return Math.max(TerritoryPreviewArea.MIN_RADIUS, Math.min(TerritoryPreviewArea.MAX_RADIUS, radius));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (CoreRecord core : cores.values()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("Id", core.id);
            value.putUUID("Nation", core.nationId);
            value.putUUID("PlacedBy", core.placedBy);
            value.putString("Dimension", core.dimension.toString());
            value.putLong("Pos", core.pos.asLong());
            value.putString("Type", core.type.name());
            value.putInt("Radius", core.radius);
            value.putString("State", core.state.name());
            value.putInt("Health", core.health);
            value.putInt("MaximumHealth", core.maximumHealth);
            value.putLong("RegenDelayTicks", core.regenDelayTicks);
            value.putLong("RegenProgressTicks", core.regenProgressTicks);
            list.add(value);
        }
        tag.put("Cores", list);
        return tag;
    }

    public static TerritorySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TerritorySavedData data = new TerritorySavedData();
        ListTag list = tag.getList("Cores", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag value = list.getCompound(index);
            try {
                ResourceLocation dimension = ResourceLocation.parse(value.getString("Dimension"));
                BlockPos pos = BlockPos.of(value.getLong("Pos"));
                CoreType type = CoreType.valueOf(value.getString("Type"));
                int maximumHealth = value.contains("MaximumHealth", Tag.TAG_INT)
                        ? Math.max(1, value.getInt("MaximumHealth")) : maximumHealth(type);
                int health = value.contains("Health", Tag.TAG_INT)
                        ? Math.max(0, Math.min(maximumHealth, value.getInt("Health"))) : maximumHealth;
                CoreRecord core = new CoreRecord(value.getUUID("Id"), value.getUUID("Nation"),
                        value.getUUID("PlacedBy"), dimension, pos,
                        type, clampRadius(value.getInt("Radius")),
                        CoreState.valueOf(value.getString("State")), health, maximumHealth,
                        Math.max(0L, value.getLong("RegenDelayTicks")),
                        Math.max(0L, value.getLong("RegenProgressTicks")));
                data.cores.put(new CoreKey(dimension, pos), core);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    public static TerritorySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TerritorySavedData::new, TerritorySavedData::load, null),
                "moveearth_territory_cores");
    }

    public enum CoreType { CAPITAL, OUTPOST }
    public enum CoreState { CONFIGURING, ACTIVE, EXPOSED }
    public enum Status { REGISTERED, UPDATED, POSITION_OCCUPIED, FOREIGN_TERRITORY_CONFLICT, INVALID_RADIUS, NOT_FOUND }
    public record RegistrationResult(Status status, CoreRecord core) { public boolean success() { return status == Status.REGISTERED; } }
    public record UpdateResult(Status status, CoreRecord core) { public boolean success() { return status == Status.UPDATED; } }
    public record CoreRecord(UUID id, UUID nationId, UUID placedBy, ResourceLocation dimension,
                             BlockPos pos, CoreType type, int radius, CoreState state,
                             int health, int maximumHealth, long regenDelayTicks,
                             long regenProgressTicks) {
        public CoreRecord {
            maximumHealth = Math.max(1, maximumHealth);
            health = Math.max(0, Math.min(maximumHealth, health));
            regenDelayTicks = Math.max(0L, regenDelayTicks);
            regenProgressTicks = Math.max(0L, regenProgressTicks);
        }
    }
    private record CoreKey(ResourceLocation dimension, BlockPos pos) { }
    private record ReservedChunk(ResourceLocation dimension, int x, int z) { }

    private static int maximumHealth(CoreType type) {
        return type == CoreType.CAPITAL
                ? S2TerritoryConfig.capitalCoreHealth() : S2TerritoryConfig.outpostCoreHealth();
    }
}
