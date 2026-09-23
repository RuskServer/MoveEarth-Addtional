package com.ruskserver.moveearth_addtional.s2.technology;

import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.LinkedHashSet;

/** Server-authoritative guide progress. Personal progress is never inherited from a nation. */
public final class NationTechnologySavedData extends SavedData {
    /** Old schema-3 progress is a migration source only. */
    private static final boolean LEGACY_READ_ONLY = true;
    private final Map<UUID, NationProgress> nations = new HashMap<>();
    private final Map<UUID, NationProgress> players = new HashMap<>();
    private long revision;
    private int schema = 3;
    private boolean migrationChecked;

    public long revision() { return revision; }

    public boolean tracked(UUID playerId, UUID nationId, TechnologyDefinition definition) {
        NationProgress progress = definition.scope() == TechnologyDefinition.Scope.PERSONAL
                ? players.get(playerId) : nations.get(nationId);
        return progress != null && progress.tracked.contains(definition.id());
    }

    public TrackResult setTracked(ServerPlayer player, ResourceLocation technologyId, boolean tracked) {
        if (LEGACY_READ_ONLY) return TrackResult.UNCHANGED;
        TechnologyDefinition definition = TechnologyDefinitions.INSTANCE.get(technologyId).orElse(null);
        if (definition == null) return TrackResult.UNKNOWN;
        UUID nationId = NationSavedData.get(player.server).nationIdFor(player.getUUID()).orElse(null);
        NationProgress progress;
        if (definition.scope() == TechnologyDefinition.Scope.NATION) {
            if (nationId == null) return TrackResult.NOT_MEMBER;
            if (!NationSavedData.get(player.server).can(player.getUUID(),
                    com.ruskserver.moveearth_addtional.s2.S2Permission.MANAGE_TECHNOLOGY)) return TrackResult.NO_PERMISSION;
            progress = nations.computeIfAbsent(nationId, ignored -> new NationProgress());
        } else {
            progress = players.computeIfAbsent(player.getUUID(), ignored -> new NationProgress());
        }
        boolean changed;
        if (tracked) {
            if (progress.tracked.size() >= 3 && !progress.tracked.contains(definition.id())) return TrackResult.LIMIT;
            changed = progress.tracked.add(definition.id());
        } else {
            changed = progress.tracked.remove(definition.id());
        }
        if (!changed) return TrackResult.UNCHANGED;
        revision++; setDirty();
        return TrackResult.CHANGED;
    }

    public Set<ResourceLocation> completed(UUID nationId) {
        NationProgress progress = nations.get(nationId);
        return progress == null ? Set.of() : progress.completed.stream()
                .map(TechnologyDefinitions.INSTANCE::canonicalId).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public Set<ResourceLocation> completedFor(UUID playerId, UUID nationId) {
        Set<ResourceLocation> result = new HashSet<>();
        NationProgress personal = players.get(playerId);
        if (personal != null) personal.completed.stream().map(TechnologyDefinitions.INSTANCE::canonicalId).forEach(result::add);
        NationProgress nation = nations.get(nationId);
        if (nation != null) nation.completed.stream().map(TechnologyDefinitions.INSTANCE::canonicalId)
                .filter(id -> TechnologyDefinitions.INSTANCE.get(id)
                        .map(definition -> definition.scope() == TechnologyDefinition.Scope.NATION).orElse(false))
                .forEach(result::add);
        return Set.copyOf(result);
    }

    public Map<String, Long> objectiveProgress(UUID nationId, ResourceLocation technologyId) {
        NationProgress progress = nations.get(nationId);
        if (progress == null) return Map.of();
        return Map.copyOf(progress.objectives.getOrDefault(technologyId, Map.of()));
    }

    public Map<String, Long> objectiveProgress(UUID playerId, UUID nationId, TechnologyDefinition definition) {
        NationProgress progress = definition.scope() == TechnologyDefinition.Scope.PERSONAL
                ? players.get(playerId) : nations.get(nationId);
        if (progress == null) return Map.of();
        return Map.copyOf(progress.objectives.getOrDefault(definition.id(), Map.of()));
    }

    public boolean hasTechnology(UUID nationId, ResourceLocation technologyId) {
        return nationId != null && completed(nationId).contains(technologyId);
    }

    public boolean hasUnlock(UUID nationId, String unlock) {
        if (nationId == null || unlock == null || unlock.isBlank()) return false;
        return completed(nationId).stream().map(TechnologyDefinitions.INSTANCE::get)
                .flatMap(java.util.Optional::stream)
                .anyMatch(definition -> definition.unlocks().contains(unlock));
    }

    public void recordPlacement(ServerPlayer player, BlockState state) {
        if (LEGACY_READ_ONLY) return;
        recordPlacement(player, state, player.blockPosition());
    }

    public void recordPlacement(ServerPlayer player, BlockState state, BlockPos pos) {
        if (LEGACY_READ_ONLY) return;
        Set<ResourceLocation> candidates = candidateIds(TechnologyDefinition.ObjectiveType.PLACE_BLOCK,
                TechnologyDefinition.ObjectiveType.PLACE_STRUCTURE);
        advance(player, candidates, definition -> definition.objectives().stream()
                .filter(objective -> objective.matches(state) && objectiveAllowed(player, objective, pos))
                .map(TechnologyDefinition.Objective::id).toList(), 1L,
                player.level().dimension().location() + ":" + pos.asLong());
    }

    public void recordCraft(ServerPlayer player, ItemStack stack) {
        if (LEGACY_READ_ONLY) return;
        Set<ResourceLocation> candidates = candidateIds(TechnologyDefinition.ObjectiveType.CRAFT_ITEM,
                TechnologyDefinition.ObjectiveType.OBTAIN_ITEM);
        advance(player, candidates, definition -> definition.objectives().stream()
                .filter(objective -> objective.matches(stack)).map(TechnologyDefinition.Objective::id).toList(),
                Math.max(1, stack.getCount()), null);
    }

    /** Common integration entry point for Create addons and MoveEarth subsystems. */
    public void recordObjective(ServerPlayer player, TechnologyDefinition.ObjectiveType type,
                                ResourceLocation target, long amount, BlockPos pos) {
        if (LEGACY_READ_ONLY) return;
        Set<ResourceLocation> candidates = candidateIds(type);
        advance(player, candidates, definition -> definition.objectives().stream()
                .filter(objective -> objective.type() == type)
                .filter(objective -> objective.target() == null || objective.matchesTarget(target))
                .filter(objective -> objectiveAllowed(player, objective, pos))
                .map(TechnologyDefinition.Objective::id).toList(), amount, null);
    }

    public void recordAction(ServerPlayer player, String action) {
        ResourceLocation target = ResourceLocation.tryParse(action.contains(":")
                ? action : "moveearth_addtional:" + action);
        if (target != null) recordObjective(player, TechnologyDefinition.ObjectiveType.ACTION,
                target, 1L, player.blockPosition());
    }

    private static boolean objectiveAllowed(ServerPlayer player, TechnologyDefinition.Objective objective, BlockPos pos) {
        if (!objective.ownTerritoryRequired()) return true;
        UUID nationId = NationSavedData.get(player.server).nationIdFor(player.getUUID()).orElse(null);
        if (nationId == null || pos == null) return false;
        return TerritorySavedData.get(player.server).controllingCore(player.server,
                        player.level().dimension().location(), pos)
                .map(core -> core.nationId().equals(nationId)).orElse(false);
    }

    private static Set<ResourceLocation> candidateIds(TechnologyDefinition.ObjectiveType... types) {
        Set<ResourceLocation> result = new HashSet<>();
        for (TechnologyDefinition.ObjectiveType type : types) {
            TechnologyDefinitions.INSTANCE.candidates(type, null).stream()
                    .map(TechnologyDefinition::id).forEach(result::add);
        }
        return result;
    }

    public boolean refreshComputed(MinecraftServer server, UUID nationId) {
        if (LEGACY_READ_ONLY) return false;
        if (nationId == null) return false;
        boolean activeCapital = TerritorySavedData.get(server).cores().stream()
                .anyMatch(core -> core.nationId().equals(nationId)
                        && core.type() == TerritorySavedData.CoreType.CAPITAL
                        && (core.state() == TerritorySavedData.CoreState.ACTIVE
                        || core.state() == TerritorySavedData.CoreState.EXPOSED));
        return mutateNation(server, nationId, definition -> definition.objectives().stream()
                .filter(objective -> objective.type() == TechnologyDefinition.ObjectiveType.ACTIVE_CAPITAL
                        && activeCapital)
                .map(TechnologyDefinition.Objective::id).toList(), 1L);
    }

    private void advance(ServerPlayer player, Set<ResourceLocation> candidates,
                         java.util.function.Function<TechnologyDefinition, java.util.List<String>> matches,
                         long amount, String fingerprint) {
        UUID nationId = NationSavedData.get(player.server).nationIdFor(player.getUUID()).orElse(null);
        if (mutatePlayerAndNation(player.server, player.getUUID(), nationId, candidates, matches, amount, fingerprint)) {
            if (nationId == null) {
                TechnologyViewService.sync(player);
                return;
            }
            NationSavedData.get(player.server).nation(nationId).ifPresent(nation ->
                    nation.members().keySet().forEach(memberId -> {
                        ServerPlayer online = player.server.getPlayerList().getPlayer(memberId);
                        if (online != null) TechnologyViewService.sync(online);
                    }));
        }
    }

    private boolean mutateNation(MinecraftServer server, UUID nationId,
                           java.util.function.Function<TechnologyDefinition, java.util.List<String>> matches,
                           long amount) {
        NationProgress progress = nations.computeIfAbsent(nationId, ignored -> new NationProgress());
        return mutateProgress(server, nationId, progress, progress.completed, TechnologyDefinition.Scope.NATION,
                matches, amount, true);
    }

    private boolean mutatePlayerAndNation(MinecraftServer server, UUID playerId, UUID nationId,
                           Set<ResourceLocation> candidates,
                           java.util.function.Function<TechnologyDefinition, java.util.List<String>> matches,
                           long amount, String fingerprint) {
        NationProgress personal = players.computeIfAbsent(playerId, ignored -> new NationProgress());
        NationProgress nation = nationId == null ? null : nations.computeIfAbsent(nationId, ignored -> new NationProgress());
        Set<ResourceLocation> effective = new HashSet<>(personal.completed);
        if (nation != null) effective.addAll(nation.completed);
        boolean changed = false;
        boolean completedAny;
        do {
            completedAny = false;
            for (TechnologyDefinition definition : TechnologyDefinitions.INSTANCE.all()) {
                NationProgress target = definition.scope() == TechnologyDefinition.Scope.PERSONAL ? personal : nation;
                if (target == null || !candidates.contains(definition.id()) || effective.contains(definition.id())
                        || !TechnologyProgressPolicy.prerequisitesMet(definition, effective)) continue;
                Map<String, Long> values = target.objectives.computeIfAbsent(
                        definition.id(), ignored -> new LinkedHashMap<>());
                for (String objectiveId : matches.apply(definition)) {
                    TechnologyDefinition.Objective objective = definition.objectives().stream()
                            .filter(value -> value.id().equals(objectiveId)).findFirst().orElse(null);
                    if (objective == null) continue;
                    if (fingerprint != null && !rememberFingerprint(target, definition.id(), objective, fingerprint)) continue;
                    long before = values.getOrDefault(objectiveId, 0L);
                    long after = TechnologyProgressPolicy.increment(before, amount, objective.required());
                    if (after != before) { values.put(objectiveId, after); changed = true; }
                }
                if (TechnologyProgressPolicy.objectivesComplete(definition, values)) {
                    target.completed.add(definition.id());
                    effective.add(definition.id());
                    completedAny = true;
                    changed = true;
                    if (definition.scope() == TechnologyDefinition.Scope.PERSONAL) {
                        notifyPersonalCompletion(server, playerId, definition);
                    } else if (nationId != null) {
                        notifyCompletion(server, nationId, definition);
                    }
                }
            }
        } while (completedAny);
        if (changed) { revision++; setDirty(); }
        return changed;
    }

    private static boolean rememberFingerprint(NationProgress progress, ResourceLocation technologyId,
                                               TechnologyDefinition.Objective objective, String fingerprint) {
        if (objective.type() != TechnologyDefinition.ObjectiveType.PLACE_BLOCK
                && objective.type() != TechnologyDefinition.ObjectiveType.PLACE_STRUCTURE) return true;
        Set<String> values = progress.fingerprints.computeIfAbsent(technologyId, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(objective.id(), ignored -> new LinkedHashSet<>());
        if (values.size() >= Math.min(4096L, objective.required())) return false;
        return values.add(fingerprint);
    }

    private boolean mutateProgress(MinecraftServer server, UUID nationId, NationProgress progress,
                                   Set<ResourceLocation> effective, TechnologyDefinition.Scope scope,
                                   java.util.function.Function<TechnologyDefinition, java.util.List<String>> matches,
                                   long amount, boolean notify) {
        boolean changed = false;
        boolean completedAny;
        do {
            completedAny = false;
            for (TechnologyDefinition definition : TechnologyDefinitions.INSTANCE.all()) {
                if (definition.scope() != scope || progress.completed.contains(definition.id())
                        || !TechnologyProgressPolicy.prerequisitesMet(definition, effective)) continue;
                Map<String, Long> values = progress.objectives.computeIfAbsent(definition.id(), ignored -> new LinkedHashMap<>());
                for (String objectiveId : matches.apply(definition)) {
                    TechnologyDefinition.Objective objective = definition.objectives().stream()
                            .filter(value -> value.id().equals(objectiveId)).findFirst().orElse(null);
                    if (objective == null) continue;
                    long before = values.getOrDefault(objectiveId, 0L);
                    long after = TechnologyProgressPolicy.increment(before, amount, objective.required());
                    if (after != before) { values.put(objectiveId, after); changed = true; }
                }
                if (TechnologyProgressPolicy.objectivesComplete(definition, values)) {
                    progress.completed.add(definition.id()); effective.add(definition.id());
                    completedAny = true; changed = true;
                    if (notify) notifyCompletion(server, nationId, definition);
                }
            }
        } while (completedAny);
        if (changed) { revision++; setDirty(); }
        return changed;
    }

    private static void notifyPersonalCompletion(MinecraftServer server, UUID playerId, TechnologyDefinition definition) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) player.sendSystemMessage(MoveEarthMessage.success(Component.translatable(
                "message.moveearth_addtional.technology.personal_completed", Component.translatable(definition.titleKey()))));
    }

    private static void notifyCompletion(MinecraftServer server, UUID nationId, TechnologyDefinition definition) {
        NationSavedData.get(server).nation(nationId).ifPresent(nation -> nation.members().keySet().forEach(memberId -> {
            ServerPlayer online = server.getPlayerList().getPlayer(memberId);
            if (online != null) online.sendSystemMessage(MoveEarthMessage.success(Component.translatable(
                    "message.moveearth_addtional.technology.completed", Component.translatable(definition.titleKey()))));
        }));
    }

    public void removeNation(UUID nationId) {
        if (LEGACY_READ_ONLY) return;
        if (nations.remove(nationId) != null) { revision++; setDirty(); }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Schema", schema);
        tag.putLong("Revision", revision);
        ListTag nationList = new ListTag();
        nations.forEach((nationId, progress) -> {
            CompoundTag nationTag = new CompoundTag();
            nationTag.putUUID("Nation", nationId);
            ListTag completedList = new ListTag();
            progress.completed.forEach(id -> { CompoundTag value = new CompoundTag(); value.putString("Id", id.toString()); completedList.add(value); });
            nationTag.put("Completed", completedList);
            ListTag technologyList = new ListTag();
            progress.objectives.forEach((technologyId, objectives) -> {
                CompoundTag technologyTag = new CompoundTag();
                technologyTag.putString("Id", technologyId.toString());
                CompoundTag values = new CompoundTag();
                objectives.forEach(values::putLong);
                technologyTag.put("Objectives", values);
                technologyList.add(technologyTag);
            });
            nationTag.put("Progress", technologyList);
            ListTag trackedList = new ListTag();
            progress.tracked.forEach(id -> { CompoundTag value = new CompoundTag(); value.putString("Id", id.toString()); trackedList.add(value); });
            nationTag.put("Tracked", trackedList);
            writeFingerprints(nationTag, progress);
            nationList.add(nationTag);
        });
        tag.put("Nations", nationList);
        ListTag playerList = new ListTag();
        players.forEach((playerId, progress) -> playerList.add(writeProgress("Player", playerId, progress)));
        tag.put("Players", playerList);
        return tag;
    }

    private static CompoundTag writeProgress(String idKey, UUID id, NationProgress progress) {
        CompoundTag owner = new CompoundTag(); owner.putUUID(idKey, id);
        ListTag completed = new ListTag();
        progress.completed.forEach(value -> { CompoundTag entry = new CompoundTag(); entry.putString("Id", value.toString()); completed.add(entry); });
        owner.put("Completed", completed);
        ListTag entries = new ListTag();
        progress.objectives.forEach((technologyId, objectives) -> {
            CompoundTag entry = new CompoundTag(); entry.putString("Id", technologyId.toString());
            CompoundTag values = new CompoundTag(); objectives.forEach(values::putLong); entry.put("Objectives", values); entries.add(entry);
        });
        owner.put("Progress", entries);
        ListTag tracked = new ListTag();
        progress.tracked.forEach(value -> { CompoundTag entry = new CompoundTag(); entry.putString("Id", value.toString()); tracked.add(entry); });
        owner.put("Tracked", tracked); writeFingerprints(owner, progress); return owner;
    }

    private static void writeFingerprints(CompoundTag owner, NationProgress progress) {
        ListTag entries = new ListTag();
        progress.fingerprints.forEach((technologyId, objectives) -> objectives.forEach((objectiveId, values) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Technology", technologyId.toString()); entry.putString("Objective", objectiveId);
            ListTag fingerprints = new ListTag();
            values.stream().limit(4096).forEach(value -> { CompoundTag tag = new CompoundTag(); tag.putString("Value", value); fingerprints.add(tag); });
            entry.put("Values", fingerprints); entries.add(entry);
        }));
        owner.put("Fingerprints", entries);
    }

    private static void readFingerprints(CompoundTag owner, NationProgress progress) {
        ListTag entries = owner.getList("Fingerprints", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            ResourceLocation technologyId = ResourceLocation.tryParse(entry.getString("Technology"));
            String objectiveId = entry.getString("Objective");
            if (technologyId == null || objectiveId.isBlank()) continue;
            Set<String> values = progress.fingerprints.computeIfAbsent(technologyId, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(objectiveId, ignored -> new LinkedHashSet<>());
            ListTag fingerprints = entry.getList("Values", Tag.TAG_COMPOUND);
            for (int valueIndex = 0; valueIndex < fingerprints.size() && values.size() < 4096; valueIndex++) {
                values.add(fingerprints.getCompound(valueIndex).getString("Value"));
            }
        }
    }

    public static NationTechnologySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        NationTechnologySavedData data = new NationTechnologySavedData();
        data.schema = tag.contains("Schema", Tag.TAG_INT) ? tag.getInt("Schema") : 1;
        data.revision = Math.max(0L, tag.getLong("Revision"));
        ListTag nations = tag.getList("Nations", Tag.TAG_COMPOUND);
        for (int index = 0; index < nations.size(); index++) {
            CompoundTag nationTag = nations.getCompound(index);
            if (!nationTag.hasUUID("Nation")) continue;
            NationProgress progress = new NationProgress();
            ListTag completed = nationTag.getList("Completed", Tag.TAG_COMPOUND);
            for (int valueIndex = 0; valueIndex < completed.size(); valueIndex++) {
                ResourceLocation id = ResourceLocation.tryParse(completed.getCompound(valueIndex).getString("Id"));
                if (id != null) progress.completed.add(id);
            }
            ListTag technologies = nationTag.getList("Progress", Tag.TAG_COMPOUND);
            for (int valueIndex = 0; valueIndex < technologies.size(); valueIndex++) {
                CompoundTag technologyTag = technologies.getCompound(valueIndex);
                ResourceLocation id = ResourceLocation.tryParse(technologyTag.getString("Id"));
                if (id == null) continue;
                CompoundTag values = technologyTag.getCompound("Objectives");
                Map<String, Long> objectiveValues = new LinkedHashMap<>();
                values.getAllKeys().forEach(key -> objectiveValues.put(key, Math.max(0L, values.getLong(key))));
                progress.objectives.put(id, objectiveValues);
            }
            ListTag tracked = nationTag.getList("Tracked", Tag.TAG_COMPOUND);
            for (int valueIndex = 0; valueIndex < tracked.size() && progress.tracked.size() < 3; valueIndex++) {
                ResourceLocation id = ResourceLocation.tryParse(tracked.getCompound(valueIndex).getString("Id"));
                if (id != null) progress.tracked.add(id);
            }
            readFingerprints(nationTag, progress);
            data.nations.put(nationTag.getUUID("Nation"), progress);
        }
        ListTag players = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int index = 0; index < players.size(); index++) {
            CompoundTag playerTag = players.getCompound(index);
            if (playerTag.hasUUID("Player")) data.players.put(playerTag.getUUID("Player"), readProgress(playerTag));
        }
        return data;
    }

    private static NationProgress readProgress(CompoundTag owner) {
        NationProgress progress = new NationProgress();
        ListTag completed = owner.getList("Completed", Tag.TAG_COMPOUND);
        for (int index = 0; index < completed.size(); index++) {
            ResourceLocation id = ResourceLocation.tryParse(completed.getCompound(index).getString("Id"));
            if (id != null) progress.completed.add(id);
        }
        ListTag entries = owner.getList("Progress", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index); ResourceLocation id = ResourceLocation.tryParse(entry.getString("Id"));
            if (id == null) continue; CompoundTag values = entry.getCompound("Objectives"); Map<String, Long> objectiveValues = new LinkedHashMap<>();
            values.getAllKeys().forEach(key -> objectiveValues.put(key, Math.max(0L, values.getLong(key))));
            progress.objectives.put(id, objectiveValues);
        }
        ListTag tracked = owner.getList("Tracked", Tag.TAG_COMPOUND);
        for (int index = 0; index < tracked.size() && progress.tracked.size() < 3; index++) {
            ResourceLocation id = ResourceLocation.tryParse(tracked.getCompound(index).getString("Id"));
            if (id != null) progress.tracked.add(id);
        }
        readFingerprints(owner, progress);
        return progress;
    }

    public static NationTechnologySavedData get(MinecraftServer server) {
        NationTechnologySavedData data = server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(NationTechnologySavedData::new, NationTechnologySavedData::load, null),
                "moveearth_nation_technology");
        data.migrateIfNeeded(server);
        return data;
    }

    private void migrateIfNeeded(MinecraftServer server) {
        if (migrationChecked) return;
        migrationChecked = true;
        if (schema >= 3) return;
        nations.values().forEach(progress -> {
            progress.completed.removeIf(NationTechnologySavedData::isPersonalDefinition);
            progress.objectives.keySet().removeIf(NationTechnologySavedData::isPersonalDefinition);
            progress.tracked.removeIf(NationTechnologySavedData::isPersonalDefinition);
            progress.fingerprints.keySet().removeIf(NationTechnologySavedData::isPersonalDefinition);
        });
        schema = 3; revision++; setDirty();
        com.ruskserver.moveearth_addtional.Moveearth_addtional.LOGGER.info(
                "Migrated guide progress schema to v3; personal progress is no longer inherited by nations");
    }

    private static boolean isPersonalDefinition(ResourceLocation id) {
        return TechnologyDefinitions.INSTANCE.get(id)
                .map(definition -> definition.scope() == TechnologyDefinition.Scope.PERSONAL).orElse(false);
    }

    private static final class NationProgress {
        private final Set<ResourceLocation> completed = new HashSet<>();
        private final Map<ResourceLocation, Map<String, Long>> objectives = new LinkedHashMap<>();
        private final Set<ResourceLocation> tracked = new LinkedHashSet<>();
        private final Map<ResourceLocation, Map<String, Set<String>>> fingerprints = new LinkedHashMap<>();
    }

    public enum TrackResult { CHANGED, UNCHANGED, LIMIT, NO_PERMISSION, NOT_MEMBER, UNKNOWN }
}
