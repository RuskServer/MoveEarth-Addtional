package com.ruskserver.moveearth_addtional.s2.territory;

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
        CoreRecord record = new CoreRecord(UUID.randomUUID(), nationId, placedBy, dimension,
                pos.immutable(), type, clampRadius(radius), CoreState.CONFIGURING);
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
                current.dimension, current.pos, current.type, radius, CoreState.CONFIGURING);
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
                current.dimension, current.pos, current.type, current.radius, state);
        cores.put(key, updated);
        setDirty();
        return Optional.of(updated);
    }

    public boolean controlsChunk(UUID nationId, ResourceLocation dimension, BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        return cores.values().stream()
                .filter(core -> core.nationId.equals(nationId) && core.dimension.equals(dimension))
                .filter(core -> core.state == CoreState.ACTIVE || core.state == CoreState.EXPOSED)
                .map(core -> area(core.pos, core.radius))
                .anyMatch(area -> area.containsChunk(chunk.x, chunk.z));
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
                CoreRecord core = new CoreRecord(value.getUUID("Id"), value.getUUID("Nation"),
                        value.getUUID("PlacedBy"), dimension, pos,
                        CoreType.valueOf(value.getString("Type")), clampRadius(value.getInt("Radius")),
                        CoreState.valueOf(value.getString("State")));
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
                             BlockPos pos, CoreType type, int radius, CoreState state) { }
    private record CoreKey(ResourceLocation dimension, BlockPos pos) { }
    private record ReservedChunk(ResourceLocation dimension, int x, int z) { }
}
