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
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Server-authoritative registry of placed territory cores and reserved chunk areas. */
public final class TerritorySavedData extends SavedData {
    private transient MinecraftServer attachedServer;
    private final Map<CoreKey, CoreRecord> cores = new LinkedHashMap<>();
    private final Map<UUID, VaultChunk> vaults = new LinkedHashMap<>();
    private final Map<UUID, Long> vaultChangeCooldowns = new LinkedHashMap<>();
    private final Map<ResourceLocation, Map<Long, List<CoreKey>>> reservedChunkIndex = new HashMap<>();
    private final Map<ResourceLocation, Map<Long, List<CoreKey>>> controlledChunkIndex = new HashMap<>();
    private final Map<ResourceLocation, Map<Long, List<CoreKey>>> corePositionIndex = new HashMap<>();
    private final Map<ResourceLocation, Map<Long, UUID>> vaultChunkIndex = new HashMap<>();
    private final Map<UUID, CoreKey> coreIdIndex = new HashMap<>();

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
        rebuildIndexes();
        setDirty();
        return new RegistrationResult(Status.REGISTERED, record);
    }

    public Status validateRegistration(UUID nationId, ResourceLocation dimension, BlockPos pos, int radius) {
        CoreRecord existing = cores.get(new CoreKey(dimension, pos));
        if (existing != null) {
            return existing.nationId.equals(nationId) ? Status.REGISTERED : Status.POSITION_OCCUPIED;
        }
        return conflicts(nationId, dimension, area(pos, radius), null)
                ? Status.FOREIGN_TERRITORY_CONFLICT : Status.REGISTERED;
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
        rebuildIndexes();
        setDirty();
        return new UpdateResult(Status.UPDATED, updated);
    }

    public Optional<CoreRecord> core(ResourceLocation dimension, BlockPos pos) {
        return Optional.ofNullable(cores.get(new CoreKey(dimension, pos)));
    }

    public Optional<CoreRecord> coreById(UUID coreId) {
        if (coreId == null) return Optional.empty();
        CoreKey key = coreIdIndex.get(coreId);
        return key == null ? Optional.empty() : Optional.ofNullable(cores.get(key));
    }

    public Optional<CoreRecord> updateState(UUID nationId, ResourceLocation dimension,
                                            BlockPos pos, CoreState state) {
        CoreKey key = new CoreKey(dimension, pos);
        CoreRecord current = cores.get(key);
        if (current == null || !current.nationId.equals(nationId)) return Optional.empty();
        if ((current.state == CoreState.FALLEN || current.state == CoreState.DEFEATED)
                && state != current.state) return Optional.of(current);
        if (current.state == state) return Optional.of(current);
        CoreRecord updated = new CoreRecord(current.id, current.nationId, current.placedBy,
                current.dimension, current.pos, current.type, current.radius, state,
                current.health, current.maximumHealth, current.regenDelayTicks, current.regenProgressTicks);
        cores.put(key, updated);
        updateStateIndexes(key, current, updated);
        setDirty();
        return Optional.of(updated);
    }

    public List<CoreRecord> coresNear(ResourceLocation dimension, BlockPos pos, int range) {
        int safeRange = Math.max(0, range);
        int chunkRange = (safeRange + 15) >> 4;
        int centerX = pos.getX() >> 4;
        int centerZ = pos.getZ() >> 4;
        List<CoreRecord> candidates = new ArrayList<>();
        for (int chunkX = centerX - chunkRange; chunkX <= centerX + chunkRange; chunkX++) {
            for (int chunkZ = centerZ - chunkRange; chunkZ <= centerZ + chunkRange; chunkZ++) {
                for (CoreKey key : indexed(corePositionIndex, dimension, chunkX, chunkZ)) {
                    CoreRecord core = cores.get(key);
                    if (core != null) candidates.add(core);
                }
            }
        }
        return candidates.stream()
                .filter(core -> Math.abs(core.pos.getX() - pos.getX()) <= safeRange)
                .filter(core -> Math.abs(core.pos.getY() - pos.getY()) <= safeRange)
                .filter(core -> Math.abs(core.pos.getZ() - pos.getZ()) <= safeRange)
                .toList();
    }

    public List<CoreRecord> cores() {
        return List.copyOf(cores.values());
    }

    public List<VaultChunk> vaultChunks() {
        return List.copyOf(vaults.values());
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

    public Optional<CoreRecord> markCoreFallen(UUID coreId, boolean finalized) {
        CoreKey key = coreIdIndex.get(coreId);
        CoreRecord current = key == null ? null : cores.get(key);
        if (current == null) return Optional.empty();
        CoreRecord updated = new CoreRecord(current.id, current.nationId, current.placedBy,
                current.dimension, current.pos, current.type, current.radius,
                finalized ? CoreState.DEFEATED : CoreState.FALLEN,
                0, current.maximumHealth, 0L, 0L);
        cores.put(key, updated);
        updateStateIndexes(key, current, updated);
        setDirty();
        return Optional.of(updated);
    }

    public Optional<CoreRecord> recoverCore(UUID coreId, double healthPercent) {
        CoreKey key = coreIdIndex.get(coreId);
        CoreRecord current = key == null ? null : cores.get(key);
        if (current == null) return Optional.empty();
        int health = Math.max(1, (int) Math.ceil(current.maximumHealth
                * Math.max(0.0D, Math.min(100.0D, healthPercent)) / 100.0D));
        CoreRecord updated = new CoreRecord(current.id, current.nationId, current.placedBy,
                current.dimension, current.pos, current.type, current.radius, CoreState.EXPOSED,
                health, current.maximumHealth, S2TerritoryConfig.coreRegenDelayTicks(), 0L);
        cores.put(key, updated);
        updateStateIndexes(key, current, updated);
        setDirty();
        return Optional.of(updated);
    }

    /** Finalizes a fallen core without deleting any blocks, containers, or items in its territory. */
    public Optional<SettlementResult> settleFallenCore(UUID coreId, UUID attackerNation,
                                                        double recoveryPercent) {
        CoreKey key = coreIdIndex.get(coreId);
        CoreRecord current = key == null ? null : cores.get(key);
        if (current != null && (current.state == CoreState.FALLEN || current.state == CoreState.DEFEATED)) {
            TerritoryFallSettlementPolicy.Decision decision = attackerNation == null
                    ? TerritoryFallSettlementPolicy.decideIndividual(
                            current.type == CoreType.CAPITAL)
                    : TerritoryFallSettlementPolicy.decide(current.type == CoreType.CAPITAL, current.radius,
                            radius -> conflicts(attackerNation, current.dimension,
                                    area(current.pos, radius), key));
            boolean occupied = decision.outcome()
                    == TerritoryFallSettlementPolicy.Outcome.OUTPOST_OCCUPIED;
            boolean rebuilding = decision.outcome()
                    == TerritoryFallSettlementPolicy.Outcome.CAPITAL_REBUILDING;
            UUID nextNation = occupied ? attackerNation : current.nationId;
            CoreState nextState = occupied || rebuilding ? CoreState.EXPOSED : CoreState.DEFEATED;
            int nextHealth = nextState == CoreState.EXPOSED
                    ? recoveryHealth(current.maximumHealth, recoveryPercent) : 0;
            CoreRecord updated = new CoreRecord(current.id, nextNation, current.placedBy,
                    current.dimension, current.pos, current.type, decision.radius(), nextState,
                    nextHealth, current.maximumHealth, S2TerritoryConfig.coreRegenDelayTicks(), 0L);
            cores.put(key, updated);
            rebuildIndexes();
            setDirty();
            return Optional.of(new SettlementResult(decision.outcome(), current, updated));
        }
        return Optional.empty();
    }

    /** Advances health using server-open ticks only; depleted cores await the Siege state machine. */
    public List<CoreRecord> advanceCoreRegeneration(long elapsedTicks) {
        return advanceCoreRegeneration(elapsedTicks, ignored -> false);
    }

    public List<CoreRecord> advanceCoreRegeneration(long elapsedTicks, Predicate<UUID> pausedCore) {
        return advanceCoreRegeneration(elapsedTicks, pausedCore, ignored -> false);
    }

    public List<CoreRecord> advanceCoreRegeneration(long elapsedTicks, Predicate<UUID> pausedCore,
                                                     Predicate<UUID> pausedNation) {
        if (elapsedTicks <= 0L) return List.of();
        List<CoreRecord> changed = new java.util.ArrayList<>();
        boolean persistenceChanged = false;
        for (Map.Entry<CoreKey, CoreRecord> value : cores.entrySet()) {
            CoreRecord current = value.getValue();
            if (current.state == CoreState.CONFIGURING || current.state == CoreState.DEFEATED
                    || current.health <= 0) continue;
            if (pausedCore != null && pausedCore.test(current.id)) continue;
            if (pausedNation != null && pausedNation.test(current.nationId)) continue;
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
        return controlsChunk(null, nationId, dimension, pos);
    }

    public boolean controlsChunk(MinecraftServer server, UUID nationId, ResourceLocation dimension, BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        if (vaultMatches(nationId, dimension, chunk)) return true;
        for (CoreKey key : indexed(controlledChunkIndex, dimension, chunk.x, chunk.z)) {
            CoreRecord core = cores.get(key);
            if (core != null && core.nationId.equals(nationId) && effectivelyContains(server, core, chunk)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Allows normal reinforcement inside effective controlled territory and also
     * permits the one bootstrap case needed to activate a newly placed core.
     * Reserved space around active cores is deliberately not accepted here, so
     * upkeep shrinkage cannot be bypassed by welding in the disabled outer area.
     */
    public boolean allowsReinforcement(MinecraftServer server, UUID nationId,
                                       ResourceLocation dimension, BlockPos pos) {
        boolean controlled = controlsChunk(server, nationId, dimension, pos);
        if (controlled) return true;
        ChunkPos chunk = new ChunkPos(pos);
        boolean configuringReservation = false;
        for (CoreKey key : indexed(reservedChunkIndex, dimension, chunk.x, chunk.z)) {
            CoreRecord core = cores.get(key);
            if (core != null && core.nationId.equals(nationId)
                    && core.state == CoreState.CONFIGURING) {
                configuringReservation = true;
                break;
            }
        }
        return TerritoryReinforcementAccessPolicy.canManage(controlled, configuringReservation);
    }

    /** Storage is nation infrastructure: effective home territory plus the initial configuring reservation. */
    public boolean allowsStorage(MinecraftServer server, UUID nationId,
                                 ResourceLocation dimension, BlockPos pos) {
        if (nationId == null) return false;
        if (controlsChunk(server, nationId, dimension, pos)) return true;
        ChunkPos chunk = new ChunkPos(pos);
        for (CoreKey key : indexed(reservedChunkIndex, dimension, chunk.x, chunk.z)) {
            CoreRecord core = cores.get(key);
            if (core != null && core.nationId.equals(nationId)
                    && core.state == CoreState.CONFIGURING) return true;
        }
        return false;
    }

    /** Returns the nation whose ACTIVE or EXPOSED territory controls this chunk. */
    public Optional<UUID> controllingNation(ResourceLocation dimension, BlockPos pos) {
        return controllingNation(null, dimension, pos);
    }

    public Optional<UUID> controllingNation(MinecraftServer server, ResourceLocation dimension, BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        for (CoreKey key : indexed(controlledChunkIndex, dimension, chunk.x, chunk.z)) {
            CoreRecord core = cores.get(key);
            if (core != null && effectivelyContains(server, core, chunk)) return Optional.of(core.nationId);
        }
        return Optional.ofNullable(indexedVault(dimension, chunk.x, chunk.z));
    }

    /** Returns the closest active core controlling the target chunk. */
    public Optional<CoreRecord> controllingCore(ResourceLocation dimension, BlockPos pos) {
        return controllingCore(null, dimension, pos);
    }

    public Optional<CoreRecord> controllingCore(MinecraftServer server, ResourceLocation dimension, BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        CoreRecord closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (CoreKey key : indexed(controlledChunkIndex, dimension, chunk.x, chunk.z)) {
            CoreRecord core = cores.get(key);
            if (core == null || !effectivelyContains(server, core, chunk)) continue;
            double distance = core.pos.distSqr(pos);
            if (distance < closestDistance) {
                closest = core;
                closestDistance = distance;
            }
        }
        if (closest != null) return Optional.of(closest);
        UUID vaultNation = indexedVault(dimension, chunk.x, chunk.z);
        if (vaultNation == null) return Optional.empty();
        return cores.values().stream()
                .filter(core -> core.nationId.equals(vaultNation) && core.type == CoreType.CAPITAL)
                .filter(core -> core.state != CoreState.DEFEATED)
                .findFirst();
    }

    private static boolean effectivelyContains(MinecraftServer server, CoreRecord core, ChunkPos chunk) {
        int radius = server == null ? core.radius
                : NationUpkeepService.effectiveTerritoryRadius(server, core.nationId, core.radius);
        return area(core.pos, radius).containsChunk(chunk.x, chunk.z);
    }

    /** Returns the closest non-defeated core whose reserved square contains the target chunk. */
    public Optional<CoreRecord> reservedCore(ResourceLocation dimension, BlockPos pos) {
        return reservedCores(dimension, pos).stream().findFirst();
    }

    public List<CoreRecord> reservedCores(ResourceLocation dimension, BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        return indexedCores(reservedChunkIndex, dimension, chunk.x, chunk.z).stream()
                .sorted(java.util.Comparator.comparingDouble(core -> core.pos.distSqr(pos)))
                .toList();
    }

    public void remove(ResourceLocation dimension, BlockPos pos, UUID expectedCoreId) {
        CoreKey key = new CoreKey(dimension, pos);
        CoreRecord current = cores.get(key);
        if (current != null && (expectedCoreId == null || current.id.equals(expectedCoreId))) {
            cores.remove(key);
            rebuildIndexes();
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

    /**
     * Tests the durable reserved claim, intentionally ignoring temporary upkeep shrinkage.
     * Protection and reinforcement management must use the server-aware controlsChunk overload.
     */
    public boolean ownsChunk(UUID nationId, ResourceLocation dimension, BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        if (vaultMatches(nationId, dimension, chunk)) return true;
        for (CoreKey key : indexed(reservedChunkIndex, dimension, chunk.x, chunk.z)) {
            CoreRecord core = cores.get(key);
            if (core != null && core.nationId.equals(nationId)) return true;
        }
        return false;
    }

    private Set<ReservedChunk> reservedChunks(UUID nationId) {
        Set<ReservedChunk> result = cores.values().stream().filter(core -> core.nationId.equals(nationId))
                .filter(core -> core.state != CoreState.DEFEATED)
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
        VaultChunk vault = vaults.get(nationId);
        if (vault != null) result.add(new ReservedChunk(vault.dimension, vault.chunkX, vault.chunkZ));
        return result;
    }

    private Set<ReservedChunk> controlledChunks(UUID nationId) {
        Set<ReservedChunk> result = cores.values().stream()
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
        VaultChunk vault = vaults.get(nationId);
        if (vault != null) result.add(new ReservedChunk(vault.dimension, vault.chunkX, vault.chunkZ));
        return result;
    }

    private static boolean isControlled(CoreRecord core) {
        return core.state == CoreState.ACTIVE || core.state == CoreState.EXPOSED
                || core.state == CoreState.FALLEN;
    }

    private boolean conflicts(UUID nationId, ResourceLocation dimension,
                              TerritoryPreviewArea proposed, CoreKey ignored) {
        if (attachedServer != null && com.ruskserver.moveearth_addtional.warehouse.WarehouseSites
                .get(attachedServer).conflictsClaim(dimension, proposed)) return true;
        boolean coreConflict = cores.entrySet().stream()
                .filter(entry -> ignored == null || !entry.getKey().equals(ignored))
                .map(Map.Entry::getValue)
                .filter(core -> !core.nationId.equals(nationId) && core.dimension.equals(dimension))
                .filter(core -> core.state != CoreState.DEFEATED)
                .map(core -> area(core.pos, core.radius))
                .anyMatch(proposed::overlaps);
        if (coreConflict) return true;
        return vaults.values().stream()
                .filter(vault -> !vault.nationId.equals(nationId) && vault.dimension.equals(dimension))
                .anyMatch(vault -> proposed.containsChunk(vault.chunkX, vault.chunkZ));
    }

    public VaultResult setVaultChunk(UUID nationId, ResourceLocation dimension, BlockPos pos) {
        if (nationId == null || dimension == null || pos == null) return VaultResult.INVALID;
        ChunkPos chunk = new ChunkPos(pos);
        boolean capitalChunk = cores.values().stream()
                .filter(core -> core.nationId.equals(nationId) && core.type == CoreType.CAPITAL
                        && core.dimension.equals(dimension))
                .anyMatch(core -> (core.pos.getX() >> 4) == chunk.x && (core.pos.getZ() >> 4) == chunk.z);
        if (capitalChunk) return VaultResult.CAPITAL_CHUNK;
        boolean controlledByCore = false;
        for (CoreKey key : indexed(controlledChunkIndex, dimension, chunk.x, chunk.z)) {
            CoreRecord core = cores.get(key);
            if (core != null && core.nationId.equals(nationId)) {
                controlledByCore = true;
                break;
            }
        }
        if (!controlledByCore) return VaultResult.NOT_CONTROLLED;
        VaultChunk current = vaults.get(nationId);
        if (current != null && current.dimension.equals(dimension)
                && current.chunkX == chunk.x && current.chunkZ == chunk.z) return VaultResult.UNCHANGED;
        if (current != null && vaultChangeCooldown(nationId) > 0L) return VaultResult.COOLDOWN;
        vaults.put(nationId, new VaultChunk(nationId, dimension, chunk.x, chunk.z));
        if (current != null) vaultChangeCooldowns.put(nationId, S2TerritoryConfig.vaultChangeCooldownTicks());
        rebuildIndexes();
        setDirty();
        return VaultResult.UPDATED;
    }

    public Optional<VaultChunk> vaultChunk(UUID nationId) {
        return Optional.ofNullable(vaults.get(nationId));
    }

    public List<CoreRecord> removeNation(UUID nationId) {
        List<CoreRecord> removed = cores.values().stream()
                .filter(core -> core.nationId.equals(nationId)).toList();
        boolean changed = cores.values().removeIf(core -> core.nationId.equals(nationId));
        changed |= vaults.remove(nationId) != null;
        changed |= vaultChangeCooldowns.remove(nationId) != null;
        if (changed) {
            rebuildIndexes();
            setDirty();
        }
        return removed;
    }

    public long vaultChangeCooldown(UUID nationId) {
        return Math.max(0L, vaultChangeCooldowns.getOrDefault(nationId, 0L));
    }

    public void advanceVaultCooldowns(long elapsedTicks) {
        if (elapsedTicks <= 0L || vaultChangeCooldowns.isEmpty()) return;
        vaultChangeCooldowns.replaceAll((nation, remaining) -> Math.max(0L, remaining - elapsedTicks));
        vaultChangeCooldowns.entrySet().removeIf(entry -> entry.getValue() <= 0L);
        setDirty();
    }

    private boolean vaultMatches(UUID nationId, ResourceLocation dimension, ChunkPos chunk) {
        VaultChunk vault = vaults.get(nationId);
        return vault != null && vault.dimension.equals(dimension)
                && vault.chunkX == chunk.x && vault.chunkZ == chunk.z;
    }

    /** Rebuilds the transient lookup tables after the comparatively rare territory mutations. */
    private void rebuildIndexes() {
        reservedChunkIndex.clear();
        controlledChunkIndex.clear();
        corePositionIndex.clear();
        vaultChunkIndex.clear();
        coreIdIndex.clear();
        for (Map.Entry<CoreKey, CoreRecord> entry : cores.entrySet()) {
            CoreKey key = entry.getKey();
            CoreRecord core = entry.getValue();
            coreIdIndex.put(core.id, key);
            addIndex(corePositionIndex, core.dimension,
                    core.pos.getX() >> 4, core.pos.getZ() >> 4, key);
            if (core.state == CoreState.DEFEATED) continue;
            TerritoryPreviewArea reserved = area(core.pos, core.radius);
            for (int chunkX = reserved.minChunkX(); chunkX <= reserved.maxChunkX(); chunkX++) {
                for (int chunkZ = reserved.minChunkZ(); chunkZ <= reserved.maxChunkZ(); chunkZ++) {
                    addIndex(reservedChunkIndex, core.dimension, chunkX, chunkZ, key);
                    if (isControlled(core)) {
                        addIndex(controlledChunkIndex, core.dimension, chunkX, chunkZ, key);
                    }
                }
            }
        }
        for (VaultChunk vault : vaults.values()) {
            vaultChunkIndex.computeIfAbsent(vault.dimension, ignored -> new HashMap<>())
                    .put(ChunkPos.asLong(vault.chunkX, vault.chunkZ), vault.nationId);
        }
    }

    private void updateStateIndexes(CoreKey key, CoreRecord before, CoreRecord after) {
        boolean reservedBefore = before.state != CoreState.DEFEATED;
        boolean reservedAfter = after.state != CoreState.DEFEATED;
        if (reservedBefore != reservedAfter) {
            updateAreaIndex(reservedChunkIndex, key, after, reservedAfter);
        }
        if (isControlled(before) != isControlled(after)) {
            updateAreaIndex(controlledChunkIndex, key, after, isControlled(after));
        }
    }

    private static void updateAreaIndex(Map<ResourceLocation, Map<Long, List<CoreKey>>> index,
                                        CoreKey key, CoreRecord core, boolean add) {
        TerritoryPreviewArea area = area(core.pos, core.radius);
        for (int chunkX = area.minChunkX(); chunkX <= area.maxChunkX(); chunkX++) {
            for (int chunkZ = area.minChunkZ(); chunkZ <= area.maxChunkZ(); chunkZ++) {
                if (add) {
                    addIndex(index, core.dimension, chunkX, chunkZ, key);
                } else {
                    removeIndex(index, core.dimension, chunkX, chunkZ, key);
                }
            }
        }
    }

    private static void removeIndex(Map<ResourceLocation, Map<Long, List<CoreKey>>> index,
                                    ResourceLocation dimension, int chunkX, int chunkZ, CoreKey key) {
        Map<Long, List<CoreKey>> dimensionIndex = index.get(dimension);
        if (dimensionIndex == null) return;
        long packed = ChunkPos.asLong(chunkX, chunkZ);
        List<CoreKey> keys = dimensionIndex.get(packed);
        if (keys == null) return;
        keys.remove(key);
        if (keys.isEmpty()) dimensionIndex.remove(packed);
        if (dimensionIndex.isEmpty()) index.remove(dimension);
    }

    private static void addIndex(Map<ResourceLocation, Map<Long, List<CoreKey>>> index,
                                 ResourceLocation dimension, int chunkX, int chunkZ, CoreKey key) {
        index.computeIfAbsent(dimension, ignored -> new HashMap<>())
                .computeIfAbsent(ChunkPos.asLong(chunkX, chunkZ), ignored -> new ArrayList<>())
                .add(key);
    }

    private static List<CoreKey> indexed(Map<ResourceLocation, Map<Long, List<CoreKey>>> index,
                                         ResourceLocation dimension, int chunkX, int chunkZ) {
        Map<Long, List<CoreKey>> dimensionIndex = index.get(dimension);
        if (dimensionIndex == null) return List.of();
        return dimensionIndex.getOrDefault(ChunkPos.asLong(chunkX, chunkZ), List.of());
    }

    private List<CoreRecord> indexedCores(Map<ResourceLocation, Map<Long, List<CoreKey>>> index,
                                          ResourceLocation dimension, int chunkX, int chunkZ) {
        List<CoreKey> keys = indexed(index, dimension, chunkX, chunkZ);
        if (keys.isEmpty()) return List.of();
        List<CoreRecord> result = new ArrayList<>(keys.size());
        for (CoreKey key : keys) {
            CoreRecord core = cores.get(key);
            if (core != null) result.add(core);
        }
        return result;
    }

    private UUID indexedVault(ResourceLocation dimension, int chunkX, int chunkZ) {
        Map<Long, UUID> dimensionIndex = vaultChunkIndex.get(dimension);
        return dimensionIndex == null ? null : dimensionIndex.get(ChunkPos.asLong(chunkX, chunkZ));
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
        ListTag vaultList = new ListTag();
        for (VaultChunk vault : vaults.values()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("Nation", vault.nationId);
            value.putString("Dimension", vault.dimension.toString());
            value.putInt("ChunkX", vault.chunkX);
            value.putInt("ChunkZ", vault.chunkZ);
            value.putLong("ChangeCooldownTicks", vaultChangeCooldown(vault.nationId));
            vaultList.add(value);
        }
        tag.put("Vaults", vaultList);
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
        ListTag vaultList = tag.getList("Vaults", Tag.TAG_COMPOUND);
        for (int index = 0; index < vaultList.size(); index++) {
            CompoundTag value = vaultList.getCompound(index);
            try {
                UUID nationId = value.getUUID("Nation");
                data.vaults.put(nationId, new VaultChunk(nationId,
                        ResourceLocation.parse(value.getString("Dimension")),
                        value.getInt("ChunkX"), value.getInt("ChunkZ")));
                long cooldown = Math.max(0L, value.getLong("ChangeCooldownTicks"));
                if (cooldown > 0L) data.vaultChangeCooldowns.put(nationId, cooldown);
            } catch (IllegalArgumentException ignored) { }
        }
        data.rebuildIndexes();
        return data;
    }

    public static TerritorySavedData get(MinecraftServer server) {
        TerritorySavedData data = server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TerritorySavedData::new, TerritorySavedData::load, null),
                "moveearth_territory_cores");
        data.attachedServer = server;
        return data;
    }

    public enum CoreType { CAPITAL, OUTPOST }
    public enum CoreState { CONFIGURING, ACTIVE, EXPOSED, FALLEN, DEFEATED }
    public enum Status { REGISTERED, UPDATED, POSITION_OCCUPIED, FOREIGN_TERRITORY_CONFLICT, INVALID_RADIUS, NOT_FOUND }
    public enum VaultResult { UPDATED, UNCHANGED, COOLDOWN, NOT_CONTROLLED, CAPITAL_CHUNK, INVALID }
    public record RegistrationResult(Status status, CoreRecord core) { public boolean success() { return status == Status.REGISTERED; } }
    public record UpdateResult(Status status, CoreRecord core) { public boolean success() { return status == Status.UPDATED; } }
    public record SettlementResult(TerritoryFallSettlementPolicy.Outcome outcome,
                                   CoreRecord previous, CoreRecord core) { }
    public record VaultChunk(UUID nationId, ResourceLocation dimension, int chunkX, int chunkZ) { }
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

    private static int recoveryHealth(int maximumHealth, double recoveryPercent) {
        return Math.max(1, (int) Math.ceil(maximumHealth
                * Math.max(0.0D, Math.min(100.0D, recoveryPercent)) / 100.0D));
    }
}
