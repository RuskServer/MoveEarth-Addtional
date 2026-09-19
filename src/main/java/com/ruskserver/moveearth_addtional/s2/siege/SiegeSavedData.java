package com.ruskserver.moveearth_addtional.s2.siege;

import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Persistent attacker/defender/core scoped Siege timers. */
public final class SiegeSavedData extends SavedData {
    private final Map<SiegeKey, SiegeRecord> active = new LinkedHashMap<>();
    private final Map<AttackerPair, Long> retryCooldowns = new LinkedHashMap<>();
    private final Map<UUID, FallenRecord> fallen = new LinkedHashMap<>();
    private final Map<UUID, Long> nationTruces = new LinkedHashMap<>();
    private final Map<UUID, Long> coreTruces = new LinkedHashMap<>();
    private final Map<DiplomaticPair, Long> peaceTruces = new LinkedHashMap<>();
    private final Map<OfflineDamageKey, DamageCarry> offlineDamageCarry = new LinkedHashMap<>();

    public AttemptResult registerAttempt(UUID attackerNation, TerritorySavedData.CoreRecord core,
                                         boolean effectiveDamage, boolean offlineDefenseAllowed) {
        return registerAttempt(attackerNation, false, core, effectiveDamage, offlineDefenseAllowed);
    }

    public AttemptResult registerAttempt(UUID attackerId, boolean individualAttacker,
                                         TerritorySavedData.CoreRecord core,
                                         boolean effectiveDamage, boolean offlineDefenseAllowed) {
        if (attackerId == null || core == null
                || (!individualAttacker && attackerId.equals(core.nationId()))) {
            return new AttemptResult(AttemptStatus.IGNORED, null);
        }
        if (isNationSettlementProtected(core.nationId()) || isCoreSettlementProtected(core.id())) {
            return new AttemptResult(AttemptStatus.SETTLEMENT_TRUCE, null);
        }
        if (!individualAttacker && isPeaceTruceActive(attackerId, core.nationId())) {
            return new AttemptResult(AttemptStatus.PEACE_TRUCE, null);
        }
        SiegeKey key = new SiegeKey(attackerId, individualAttacker, core.nationId(), core.id());
        SiegeRecord current = active.get(key);
        if (current != null) {
            if (!effectiveDamage) return new AttemptResult(AttemptStatus.ACTIVE_UNCHANGED, current);
            SiegeRecord updated = current.withTimer(SiegeTimerPolicy.Phase.ROLLING,
                    S2TerritoryConfig.siegeRollingTicks());
            active.put(key, updated);
            retryCooldowns.remove(new AttackerPair(attackerId, individualAttacker, core.nationId()));
            setDirty();
            return new AttemptResult(current.phase() == SiegeTimerPolicy.Phase.INITIAL_LOCK
                    ? AttemptStatus.ROLLING_STARTED : AttemptStatus.ROLLING_EXTENDED, updated);
        }

        AttackerPair pair = new AttackerPair(attackerId, individualAttacker, core.nationId());
        if (!effectiveDamage && retryCooldowns.getOrDefault(pair, 0L) > 0L) {
            return new AttemptResult(AttemptStatus.RETRY_COOLDOWN, null);
        }
        SiegeTimerPolicy.State state = SiegeTimerPolicy.begin(effectiveDamage,
                S2TerritoryConfig.siegeInitialLockTicks(), S2TerritoryConfig.siegeRollingTicks());
        SiegeRecord created = new SiegeRecord(UUID.randomUUID(), attackerId, core.nationId(), core.id(),
                core.dimension(), core.pos(), state.phase(), state.remainingTicks(), offlineDefenseAllowed,
                individualAttacker);
        active.put(key, created);
        if (effectiveDamage) retryCooldowns.remove(pair);
        setDirty();
        return new AttemptResult(effectiveDamage ? AttemptStatus.ROLLING_STARTED
                : AttemptStatus.INITIAL_STARTED, created);
    }

    /** Advances only while the server is running. */
    public TickResult advance(long elapsedTicks) {
        if (elapsedTicks <= 0L) return new TickResult(List.of(), List.of());
        List<SiegeRecord> initialExpired = new ArrayList<>();
        List<SiegeRecord> rollingExpired = new ArrayList<>();
        boolean changed = false;
        var cooldownIterator = retryCooldowns.entrySet().iterator();
        while (cooldownIterator.hasNext()) {
            var entry = cooldownIterator.next();
            long remaining = Math.max(0L, entry.getValue() - elapsedTicks);
            if (remaining == 0L) cooldownIterator.remove(); else entry.setValue(remaining);
            changed = true;
        }
        changed |= advanceCountdowns(nationTruces, elapsedTicks);
        changed |= advanceCountdowns(coreTruces, elapsedTicks);
        changed |= advancePairCountdowns(peaceTruces, elapsedTicks);
        var iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            SiegeRecord current = entry.getValue();
            SiegeTimerPolicy.State advanced = SiegeTimerPolicy.advance(
                    new SiegeTimerPolicy.State(current.phase(), current.remainingTicks()), elapsedTicks);
            if (advanced.expired()) {
                iterator.remove();
                if (current.phase() == SiegeTimerPolicy.Phase.INITIAL_LOCK) {
                    initialExpired.add(current);
                    retryCooldowns.put(new AttackerPair(current.attackerNation(), current.individualAttacker(),
                                    current.defenderNation()),
                            S2TerritoryConfig.siegeRetryCooldownTicks());
                } else {
                    rollingExpired.add(current);
                }
            } else {
                entry.setValue(current.withTimer(advanced.phase(), advanced.remainingTicks()));
            }
            changed = true;
        }
        if (changed) setDirty();
        return new TickResult(List.copyOf(initialExpired), List.copyOf(rollingExpired));
    }

    public List<SiegeRecord> activeFor(UUID nationId) {
        return active.values().stream()
                .filter(record -> (!record.individualAttacker() && record.attackerNation().equals(nationId))
                        || record.defenderNation().equals(nationId))
                .sorted(Comparator.comparing(SiegeRecord::remainingTicks))
                .toList();
    }

    public FallenResult markFallen(SiegeRecord siege, TerritorySavedData.CoreRecord core) {
        if (siege == null || core == null || core.health() > 0) return new FallenResult(false, null);
        FallenRecord existing = fallen.get(core.id());
        if (existing != null) return new FallenResult(false, existing);
        FallenRecord created = new FallenRecord(siege.id(), siege.attackerNation(), siege.defenderNation(),
                core.id(), core.dimension(), core.pos(), core.type(), core.radius(),
                S2TerritoryConfig.siegePostFallTicks(), 0L, 0, false, siege.individualAttacker());
        active.entrySet().removeIf(entry -> entry.getValue().coreId().equals(core.id()));
        fallen.put(core.id(), created);
        setDirty();
        return new FallenResult(true, created);
    }

    public FallenTickResult advanceFallen(long elapsedTicks,
                                           Function<FallenRecord, SiegeFallPolicy.Presence> presence) {
        if (elapsedTicks <= 0L || fallen.isEmpty()) return new FallenTickResult(
                List.of(), List.of(), List.of(), List.of(), List.of());
        List<FallenRecord> stageChanged = new ArrayList<>();
        List<FallenRecord> recovered = new ArrayList<>();
        List<FallenRecord> finalized = new ArrayList<>();
        List<FallenRecord> counterStarted = new ArrayList<>();
        List<FallenRecord> counterFailed = new ArrayList<>();
        boolean changed = false;
        var iterator = fallen.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            FallenRecord current = entry.getValue();
            // Re-emit persisted finalization until the territory settlement removes it. This makes
            // the two SavedData updates recoverable if the server stops between them.
            if (current.finalized()) {
                finalized.add(current);
                continue;
            }
            SiegeFallPolicy.AdvanceResult next = SiegeFallPolicy.advance(current.remainingTicks(),
                    current.captureTicks(), presence.apply(current), elapsedTicks,
                    S2TerritoryConfig.siegePostFallTicks(), S2TerritoryConfig.siegeFallStageTicks(),
                    S2TerritoryConfig.siegeCounterCaptureTicks());
            FallenRecord updated = current.withProgress(next.remainingTicks(), next.captureTicks(),
                    next.stage(), next.remainingTicks() == 0L);
            if (current.captureTicks() == 0L && updated.captureTicks() > 0L) counterStarted.add(updated);
            if (current.captureTicks() > 0L && updated.captureTicks() == 0L) counterFailed.add(updated);
            if (next.recovered()) {
                recovered.add(updated);
                iterator.remove();
                changed = true;
            } else {
                entry.setValue(updated);
                changed = true;
                if (updated.stage() != current.stage()) stageChanged.add(updated);
                if (updated.finalized() && !current.finalized()) finalized.add(updated);
            }
        }
        if (changed) setDirty();
        return new FallenTickResult(List.copyOf(stageChanged), List.copyOf(recovered), List.copyOf(finalized),
                List.copyOf(counterStarted), List.copyOf(counterFailed));
    }

    public List<FallenRecord> fallenFor(UUID nationId) {
        return fallen.values().stream()
                .filter(record -> !record.finalized())
                .filter(record -> (!record.individualAttacker() && record.attackerNation().equals(nationId))
                        || record.defenderNation().equals(nationId))
                .sorted(Comparator.comparing(FallenRecord::remainingTicks))
                .toList();
    }

    public boolean isNationLocked(UUID nationId) {
        return !activeFor(nationId).isEmpty() || !fallenFor(nationId).isEmpty();
    }

    public List<SiegeRecord> activeForPlayer(UUID playerId) {
        return active.values().stream()
                .filter(SiegeRecord::individualAttacker)
                .filter(record -> record.attackerNation().equals(playerId))
                .sorted(Comparator.comparing(SiegeRecord::remainingTicks)).toList();
    }

    public boolean hasActiveIndividualAttack(UUID playerId, UUID coreId) {
        return playerId != null && coreId != null && active.values().stream()
                .anyMatch(record -> record.individualAttacker()
                        && record.attackerNation().equals(playerId) && record.coreId().equals(coreId));
    }

    public List<FallenRecord> fallenForPlayer(UUID playerId) {
        return fallen.values().stream().filter(record -> !record.finalized())
                .filter(FallenRecord::individualAttacker)
                .filter(record -> record.attackerNation().equals(playerId))
                .sorted(Comparator.comparing(FallenRecord::remainingTicks)).toList();
    }

    public boolean isCoreLocked(UUID coreId) {
        return active.values().stream().anyMatch(record -> record.coreId().equals(coreId))
                || fallen.containsKey(coreId);
    }

    public boolean isCoreFallen(UUID coreId) { return fallen.containsKey(coreId); }

    public boolean isNationSettlementProtected(UUID nationId) {
        return nationId != null && nationTruces.getOrDefault(nationId, 0L) > 0L;
    }

    public boolean isCoreSettlementProtected(UUID coreId) {
        return coreId != null && coreTruces.getOrDefault(coreId, 0L) > 0L;
    }

    public long nationSettlementTruceRemaining(UUID nationId) {
        return nationId == null ? 0L : nationTruces.getOrDefault(nationId, 0L);
    }

    public long coreSettlementTruceRemaining(UUID coreId) {
        return coreId == null ? 0L : coreTruces.getOrDefault(coreId, 0L);
    }

    public boolean isPeaceTruceActive(UUID firstNation, UUID secondNation) {
        return firstNation != null && secondNation != null
                && peaceTruces.getOrDefault(new DiplomaticPair(firstNation, secondNation), 0L) > 0L;
    }

    public long peaceTruceRemaining(UUID firstNation, UUID secondNation) {
        return firstNation == null || secondNation == null ? 0L
                : peaceTruces.getOrDefault(new DiplomaticPair(firstNation, secondNation), 0L);
    }

    public void startPeaceTruce(UUID firstNation, UUID secondNation, long ticks) {
        if (firstNation == null || secondNation == null || firstNation.equals(secondNation) || ticks <= 0L) return;
        peaceTruces.merge(new DiplomaticPair(firstNation, secondNation), ticks, Math::max);
        setDirty();
    }

    public boolean hasConflictBetween(UUID firstNation, UUID secondNation) {
        return active.values().stream().anyMatch(record -> !record.individualAttacker() && sameParties(record.attackerNation,
                record.defenderNation, firstNation, secondNation))
                || fallen.values().stream().anyMatch(record -> !record.individualAttacker() && sameParties(record.attackerNation,
                record.defenderNation, firstNation, secondNation));
    }

    public boolean hasIndividualConflictBetween(UUID playerId, UUID defenderNation) {
        if (playerId == null || defenderNation == null) return false;
        return active.values().stream().anyMatch(record -> record.individualAttacker()
                && record.attackerNation().equals(playerId) && record.defenderNation().equals(defenderNation))
                || fallen.values().stream().anyMatch(record -> record.individualAttacker()
                && record.attackerNation().equals(playerId) && record.defenderNation().equals(defenderNation));
    }

    public java.util.Optional<SiegeRecord> activeById(UUID siegeId) {
        return active.values().stream().filter(record -> record.id.equals(siegeId)).findFirst();
    }

    public java.util.Optional<FallenRecord> fallenBySiegeId(UUID siegeId) {
        return fallen.values().stream().filter(record -> record.siegeId.equals(siegeId)).findFirst();
    }

    public List<FallenRecord> fallenRecords() { return List.copyOf(fallen.values()); }

    public boolean removeActive(UUID siegeId) {
        boolean changed = active.values().removeIf(record -> record.id.equals(siegeId));
        if (changed) setDirty();
        return changed;
    }

    public void startRetryCooldown(UUID attackerNation, UUID defenderNation, long ticks) {
        startRetryCooldown(attackerNation, false, defenderNation, ticks);
    }

    public void startRetryCooldown(UUID attackerId, boolean individualAttacker, UUID defenderNation, long ticks) {
        if (attackerId == null || defenderNation == null || ticks <= 0L) return;
        retryCooldowns.merge(new AttackerPair(attackerId, individualAttacker, defenderNation), ticks, Math::max);
        setDirty();
    }

    public void removeNationState(UUID nationId) {
        boolean changed = active.values().removeIf(record -> !record.individualAttacker && record.attackerNation.equals(nationId)
                || record.defenderNation.equals(nationId));
        changed |= fallen.values().removeIf(record -> !record.individualAttacker && record.attackerNation.equals(nationId)
                || record.defenderNation.equals(nationId));
        changed |= retryCooldowns.entrySet().removeIf(entry -> (!entry.getKey().individual && entry.getKey().attacker.equals(nationId))
                || entry.getKey().defender.equals(nationId));
        changed |= peaceTruces.entrySet().removeIf(entry -> entry.getKey().first.equals(nationId)
                || entry.getKey().second.equals(nationId));
        changed |= nationTruces.remove(nationId) != null;
        if (changed) setDirty();
    }

    public void removeCoreState(List<TerritorySavedData.CoreRecord> cores) {
        if (cores == null || cores.isEmpty()) return;
        java.util.Set<UUID> coreIds = cores.stream().map(TerritorySavedData.CoreRecord::id)
                .collect(java.util.stream.Collectors.toSet());
        boolean changed = coreTruces.keySet().removeIf(coreIds::contains);
        changed |= offlineDamageCarry.entrySet().removeIf(entry -> cores.stream().anyMatch(core ->
                core.dimension().equals(entry.getKey().dimension)
                        && core.pos().asLong() == entry.getKey().pos));
        if (changed) setDirty();
    }

    public boolean removeFallen(UUID coreId) {
        boolean changed = fallen.remove(coreId) != null;
        if (changed) setDirty();
        return changed;
    }

    public ConflictEndResult endConflictsBetween(UUID firstNation, UUID secondNation) {
        List<SiegeRecord> endedActive = active.values().stream()
                .filter(record -> !record.individualAttacker() && sameParties(record.attackerNation, record.defenderNation,
                        firstNation, secondNation)).toList();
        List<FallenRecord> endedFallen = fallen.values().stream()
                .filter(record -> !record.individualAttacker() && sameParties(record.attackerNation, record.defenderNation,
                        firstNation, secondNation)).toList();
        if (!endedActive.isEmpty()) active.values().removeAll(endedActive);
        if (!endedFallen.isEmpty()) fallen.values().removeAll(endedFallen);
        if (!endedActive.isEmpty() || !endedFallen.isEmpty()) setDirty();
        return new ConflictEndResult(endedActive, endedFallen);
    }

    /** Removes the transient fall state after the territory registry has applied its final settlement. */
    public void resolveFallen(FallenRecord record, boolean protectNation, long truceTicks) {
        if (record == null || fallen.remove(record.coreId()) == null) return;
        long safeTicks = Math.max(0L, truceTicks);
        if (safeTicks > 0L) {
            Map<UUID, Long> target = protectNation ? nationTruces : coreTruces;
            UUID key = protectNation ? record.defenderNation() : record.coreId();
            target.merge(key, safeTicks, Math::max);
        }
        setDirty();
    }

    public boolean isCoreRegenPaused(UUID coreId) {
        return fallen.containsKey(coreId) || active.values().stream().anyMatch(record -> record.coreId().equals(coreId)
                && record.phase() == SiegeTimerPolicy.Phase.ROLLING);
    }

    public boolean isOfflineDefenseSuppressed(UUID coreId) {
        if (fallen.containsKey(coreId)) return true;
        return active.values().stream().filter(record -> record.coreId().equals(coreId))
                .anyMatch(record -> OfflineDefensePolicy.siegeSuppresses(
                        false, record.phase(), record.offlineDefenseAllowed()));
    }

    public OfflineDefensePolicy.DamageResult applyScaledDamage(ResourceLocation dimension, BlockPos pos,
                                                                int rawDamage, int numerator,
                                                                int denominator) {
        OfflineDamageKey key = new OfflineDamageKey(dimension, pos.asLong());
        int safeDenominator = Math.max(1, denominator);
        DamageCarry previous = offlineDamageCarry.get(key);
        int carried = previous != null && previous.denominator() == safeDenominator
                ? previous.units() : 0;
        OfflineDefensePolicy.DamageResult result = OfflineDefensePolicy.applyRatio(
                rawDamage, numerator, safeDenominator, carried);
        if (result.carriedUnits() > 0) {
            DamageCarry updated = new DamageCarry(result.carriedUnits(), result.divisor());
            offlineDamageCarry.put(key, updated);
            if (!updated.equals(previous)) setDirty();
        } else {
            if (offlineDamageCarry.remove(key) != null) setDirty();
        }
        return result;
    }

    public void clearOfflineDamageCarry(ResourceLocation dimension, BlockPos pos) {
        if (offlineDamageCarry.remove(new OfflineDamageKey(dimension, pos.asLong())) != null) setDirty();
    }

    public boolean isReinforcementDisabled(ResourceLocation dimension, BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        for (FallenRecord record : fallen.values()) {
            if (!record.dimension().equals(dimension)) continue;
            int coreChunkX = record.corePos().getX() >> 4;
            int coreChunkZ = record.corePos().getZ() >> 4;
            int dx = Math.abs(chunkX - coreChunkX);
            int dz = Math.abs(chunkZ - coreChunkZ);
            if (dx > record.radius() || dz > record.radius()) continue;
            if (SiegeFallPolicy.protectionDisabled(record.stage(), record.radius(), Math.max(dx, dz))) return true;
        }
        return false;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag activeTag = new ListTag();
        for (SiegeRecord record : active.values()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("Id", record.id());
            value.putUUID("Attacker", record.attackerNation());
            value.putBoolean("IndividualAttacker", record.individualAttacker());
            value.putUUID("Defender", record.defenderNation());
            value.putUUID("Core", record.coreId());
            value.putString("Dimension", record.dimension().toString());
            value.putLong("Pos", record.corePos().asLong());
            value.putString("Phase", record.phase().name());
            value.putLong("Remaining", record.remainingTicks());
            value.putBoolean("OfflineDefenseAllowed", record.offlineDefenseAllowed());
            activeTag.add(value);
        }
        tag.put("Active", activeTag);
        ListTag cooldownTag = new ListTag();
        for (var entry : retryCooldowns.entrySet()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("Attacker", entry.getKey().attacker());
            value.putBoolean("IndividualAttacker", entry.getKey().individual());
            value.putUUID("Defender", entry.getKey().defender());
            value.putLong("Remaining", entry.getValue());
            cooldownTag.add(value);
        }
        tag.put("RetryCooldowns", cooldownTag);
        ListTag fallenTag = new ListTag();
        for (FallenRecord record : fallen.values()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("Siege", record.siegeId());
            value.putUUID("Attacker", record.attackerNation());
            value.putBoolean("IndividualAttacker", record.individualAttacker());
            value.putUUID("Defender", record.defenderNation());
            value.putUUID("Core", record.coreId());
            value.putString("Dimension", record.dimension().toString());
            value.putLong("Pos", record.corePos().asLong());
            value.putString("CoreType", record.coreType().name());
            value.putInt("Radius", record.radius());
            value.putLong("Remaining", record.remainingTicks());
            value.putLong("Capture", record.captureTicks());
            value.putInt("Stage", record.stage());
            value.putBoolean("Finalized", record.finalized());
            fallenTag.add(value);
        }
        tag.put("Fallen", fallenTag);
        saveUuidCountdowns(tag, "NationTruces", nationTruces);
        saveUuidCountdowns(tag, "CoreTruces", coreTruces);
        ListTag peaceTruceList = new ListTag();
        for (var entry : peaceTruces.entrySet()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("First", entry.getKey().first);
            value.putUUID("Second", entry.getKey().second);
            value.putLong("Remaining", entry.getValue());
            peaceTruceList.add(value);
        }
        tag.put("PeaceTruces", peaceTruceList);
        ListTag offlineDamageTag = new ListTag();
        for (var entry : offlineDamageCarry.entrySet()) {
            CompoundTag value = new CompoundTag();
            value.putString("Dimension", entry.getKey().dimension().toString());
            value.putLong("Pos", entry.getKey().pos());
            value.putInt("Units", entry.getValue().units());
            value.putInt("Denominator", entry.getValue().denominator());
            offlineDamageTag.add(value);
        }
        tag.put("OfflineDamageCarry", offlineDamageTag);
        return tag;
    }

    public static SiegeSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        SiegeSavedData data = new SiegeSavedData();
        ListTag activeTag = tag.getList("Active", Tag.TAG_COMPOUND);
        for (int index = 0; index < activeTag.size(); index++) {
            CompoundTag value = activeTag.getCompound(index);
            try {
                SiegeRecord record = new SiegeRecord(value.getUUID("Id"), value.getUUID("Attacker"),
                        value.getUUID("Defender"), value.getUUID("Core"),
                        ResourceLocation.parse(value.getString("Dimension")), BlockPos.of(value.getLong("Pos")),
                        SiegeTimerPolicy.Phase.valueOf(value.getString("Phase")),
                        Math.max(1L, value.getLong("Remaining")),
                        value.getBoolean("OfflineDefenseAllowed"), value.getBoolean("IndividualAttacker"));
                data.active.put(new SiegeKey(record.attackerNation(), record.individualAttacker(),
                        record.defenderNation(), record.coreId()), record);
            } catch (IllegalArgumentException ignored) { }
        }
        ListTag cooldownTag = tag.getList("RetryCooldowns", Tag.TAG_COMPOUND);
        for (int index = 0; index < cooldownTag.size(); index++) {
            CompoundTag value = cooldownTag.getCompound(index);
            long remaining = value.getLong("Remaining");
            if (remaining > 0L) data.retryCooldowns.put(
                    new AttackerPair(value.getUUID("Attacker"), value.getBoolean("IndividualAttacker"),
                            value.getUUID("Defender")), remaining);
        }
        ListTag fallenTag = tag.getList("Fallen", Tag.TAG_COMPOUND);
        for (int index = 0; index < fallenTag.size(); index++) {
            CompoundTag value = fallenTag.getCompound(index);
            try {
                FallenRecord record = new FallenRecord(value.getUUID("Siege"), value.getUUID("Attacker"),
                        value.getUUID("Defender"), value.getUUID("Core"),
                        ResourceLocation.parse(value.getString("Dimension")), BlockPos.of(value.getLong("Pos")),
                        TerritorySavedData.CoreType.valueOf(value.getString("CoreType")),
                        Math.max(0, value.getInt("Radius")), Math.max(0L, value.getLong("Remaining")),
                        Math.max(0L, value.getLong("Capture")), Math.max(0, value.getInt("Stage")),
                        value.getBoolean("Finalized"), value.getBoolean("IndividualAttacker"));
                data.fallen.put(record.coreId(), record);
            } catch (IllegalArgumentException ignored) { }
        }
        ListTag offlineDamageTag = tag.getList("OfflineDamageCarry", Tag.TAG_COMPOUND);
        for (int index = 0; index < offlineDamageTag.size(); index++) {
            CompoundTag value = offlineDamageTag.getCompound(index);
            int units = value.getInt("Units");
            if (units <= 0) continue;
            try {
                data.offlineDamageCarry.put(new OfflineDamageKey(
                        ResourceLocation.parse(value.getString("Dimension")), value.getLong("Pos")),
                        new DamageCarry(units, Math.max(1, value.getInt("Denominator"))));
            } catch (IllegalArgumentException ignored) { }
        }
        loadUuidCountdowns(tag, "NationTruces", data.nationTruces);
        loadUuidCountdowns(tag, "CoreTruces", data.coreTruces);
        ListTag peaceTruceList = tag.getList("PeaceTruces", Tag.TAG_COMPOUND);
        for (int index = 0; index < peaceTruceList.size(); index++) {
            CompoundTag value = peaceTruceList.getCompound(index);
            long remaining = value.getLong("Remaining");
            if (remaining > 0L) data.peaceTruces.put(new DiplomaticPair(
                    value.getUUID("First"), value.getUUID("Second")), remaining);
        }
        return data;
    }

    public static SiegeSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SiegeSavedData::new, SiegeSavedData::load, null),
                "moveearth_sieges");
    }

    public enum AttemptStatus {
        INITIAL_STARTED, ROLLING_STARTED, ROLLING_EXTENDED, ACTIVE_UNCHANGED, RETRY_COOLDOWN,
        SETTLEMENT_TRUCE, PEACE_TRUCE, RECOVERY_PROTECTED, IGNORED
    }

    public record AttemptResult(AttemptStatus status, SiegeRecord siege) { }
    public record TickResult(List<SiegeRecord> initialExpired, List<SiegeRecord> rollingExpired) { }
    public record FallenResult(boolean created, FallenRecord fallen) { }
    public record FallenTickResult(List<FallenRecord> stageChanged, List<FallenRecord> recovered,
                                   List<FallenRecord> finalized, List<FallenRecord> counterStarted,
                                   List<FallenRecord> counterFailed) { }
    public record ConflictEndResult(List<SiegeRecord> active, List<FallenRecord> fallen) { }
    public record SiegeRecord(UUID id, UUID attackerNation, UUID defenderNation, UUID coreId,
                              ResourceLocation dimension, BlockPos corePos, SiegeTimerPolicy.Phase phase,
                              long remainingTicks, boolean offlineDefenseAllowed, boolean individualAttacker) {
        public SiegeRecord withTimer(SiegeTimerPolicy.Phase nextPhase, long nextRemaining) {
            return new SiegeRecord(id, attackerNation, defenderNation, coreId, dimension, corePos,
                    nextPhase, Math.max(1L, nextRemaining), offlineDefenseAllowed, individualAttacker);
        }
    }
    public record FallenRecord(UUID siegeId, UUID attackerNation, UUID defenderNation, UUID coreId,
                               ResourceLocation dimension, BlockPos corePos,
                               TerritorySavedData.CoreType coreType, int radius, long remainingTicks,
                               long captureTicks, int stage, boolean finalized, boolean individualAttacker) {
        public FallenRecord withProgress(long remaining, long capture, int nextStage, boolean nextFinalized) {
            return new FallenRecord(siegeId, attackerNation, defenderNation, coreId, dimension, corePos,
                    coreType, radius, Math.max(0L, remaining), Math.max(0L, capture),
                    Math.max(0, Math.min(3, nextStage)), nextFinalized, individualAttacker);
        }
    }
    private record SiegeKey(UUID attacker, boolean individual, UUID defender, UUID core) { }
    private record AttackerPair(UUID attacker, boolean individual, UUID defender) { }
    private record OfflineDamageKey(ResourceLocation dimension, long pos) { }
    private record DamageCarry(int units, int denominator) { }
    private record DiplomaticPair(UUID first, UUID second) {
        private DiplomaticPair {
            if (compare(first, second) > 0) {
                UUID swap = first;
                first = second;
                second = swap;
            }
        }
        private static int compare(UUID left, UUID right) {
            int most = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
            return most != 0 ? most : Long.compareUnsigned(
                    left.getLeastSignificantBits(), right.getLeastSignificantBits());
        }
    }

    private static boolean advanceCountdowns(Map<UUID, Long> countdowns, long elapsedTicks) {
        if (countdowns.isEmpty()) return false;
        var iterator = countdowns.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            long remaining = Math.max(0L, entry.getValue() - elapsedTicks);
            if (remaining == 0L) iterator.remove(); else entry.setValue(remaining);
        }
        return true;
    }

    private static boolean advancePairCountdowns(Map<DiplomaticPair, Long> countdowns, long elapsedTicks) {
        if (countdowns.isEmpty()) return false;
        var iterator = countdowns.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            long remaining = Math.max(0L, entry.getValue() - elapsedTicks);
            if (remaining == 0L) iterator.remove(); else entry.setValue(remaining);
        }
        return true;
    }

    private static boolean sameParties(UUID leftA, UUID leftB, UUID rightA, UUID rightB) {
        return leftA.equals(rightA) && leftB.equals(rightB)
                || leftA.equals(rightB) && leftB.equals(rightA);
    }

    private static void saveUuidCountdowns(CompoundTag tag, String name, Map<UUID, Long> countdowns) {
        ListTag list = new ListTag();
        for (var entry : countdowns.entrySet()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("Id", entry.getKey());
            value.putLong("Remaining", entry.getValue());
            list.add(value);
        }
        tag.put(name, list);
    }

    private static void loadUuidCountdowns(CompoundTag tag, String name, Map<UUID, Long> countdowns) {
        ListTag list = tag.getList(name, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag value = list.getCompound(index);
            long remaining = value.getLong("Remaining");
            if (remaining > 0L && value.hasUUID("Id")) countdowns.put(value.getUUID("Id"), remaining);
        }
    }
}
