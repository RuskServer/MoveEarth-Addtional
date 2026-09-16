package com.ruskserver.moveearth_addtional.s2.siege;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative combat affiliation for formally dispatched players.
 * The dispatch-contract subsystem owns registration and removal; prisoner records copy the
 * combat nation at capture time so contract expiry never releases or reassigns a captive.
 */
public final class SiegeParticipationSavedData extends SavedData {
    private final Map<UUID, Participation> participants = new LinkedHashMap<>();

    public void register(UUID playerId, UUID homeNation, UUID combatNation, UUID siegeId) {
        if (playerId == null || homeNation == null || combatNation == null || siegeId == null) return;
        participants.put(playerId, new Participation(playerId, homeNation, combatNation, siegeId));
        setDirty();
    }

    public void remove(UUID playerId, UUID siegeId) {
        Participation current = participants.get(playerId);
        if (current != null && (siegeId == null || current.siegeId.equals(siegeId))) {
            participants.remove(playerId);
            setDirty();
        }
    }

    public Optional<Participation> forPlayer(UUID playerId) {
        return Optional.ofNullable(participants.get(playerId));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Participation value : participants.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", value.playerId);
            entry.putUUID("HomeNation", value.homeNation);
            entry.putUUID("CombatNation", value.combatNation);
            entry.putUUID("Siege", value.siegeId);
            list.add(entry);
        }
        tag.put("Participants", list);
        return tag;
    }

    public static SiegeParticipationSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        SiegeParticipationSavedData data = new SiegeParticipationSavedData();
        ListTag list = tag.getList("Participants", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Player") || !entry.hasUUID("HomeNation")
                    || !entry.hasUUID("CombatNation") || !entry.hasUUID("Siege")) continue;
            Participation value = new Participation(entry.getUUID("Player"), entry.getUUID("HomeNation"),
                    entry.getUUID("CombatNation"), entry.getUUID("Siege"));
            data.participants.put(value.playerId, value);
        }
        return data;
    }

    public static SiegeParticipationSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SiegeParticipationSavedData::new, SiegeParticipationSavedData::load, null),
                "moveearth_siege_participation");
    }

    public record Participation(UUID playerId, UUID homeNation, UUID combatNation, UUID siegeId) { }
}
