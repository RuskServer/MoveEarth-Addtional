package com.ruskserver.moveearth_addtional.s2.siege;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistent prisoner ownership and releases awaiting an offline player's next login. */
public final class PrisonerSavedData extends SavedData {
    private final Map<UUID, Prisoner> held = new LinkedHashMap<>();
    private final Map<UUID, UUID> pendingReleaseHome = new LinkedHashMap<>();

    public CaptureResult capture(UUID playerId, UUID homeNation, UUID holdingNation,
                                 UUID capturedBy, long capturedAt) {
        if (playerId == null || homeNation == null || holdingNation == null || capturedBy == null
                || homeNation.equals(holdingNation)) return CaptureResult.INVALID;
        if (held.containsKey(playerId)) return CaptureResult.ALREADY_HELD;
        held.put(playerId, new Prisoner(playerId, homeNation, holdingNation, capturedBy,
                Math.max(0L, capturedAt)));
        pendingReleaseHome.remove(playerId);
        setDirty();
        return CaptureResult.CAPTURED;
    }

    public Optional<Prisoner> prisoner(UUID playerId) {
        return Optional.ofNullable(held.get(playerId));
    }

    public List<Prisoner> between(UUID firstNation, UUID secondNation) {
        return held.values().stream().filter(value -> samePair(
                value.homeNation, value.holdingNation, firstNation, secondNation)).toList();
    }

    public int countBetween(UUID firstNation, UUID secondNation) {
        return between(firstNation, secondNation).size();
    }

    public List<Prisoner> betweenNation(UUID nationId) {
        if (nationId == null) return List.of();
        return held.values().stream().filter(value -> value.homeNation.equals(nationId)
                || value.holdingNation.equals(nationId)).toList();
    }

    /** Commit point used only after all peace terms have been validated and paid. */
    public List<Prisoner> releaseBetween(UUID firstNation, UUID secondNation) {
        List<Prisoner> released = between(firstNation, secondNation);
        for (Prisoner prisoner : released) {
            held.remove(prisoner.playerId);
            pendingReleaseHome.put(prisoner.playerId, prisoner.homeNation);
        }
        if (!released.isEmpty()) setDirty();
        return released;
    }

    public Optional<UUID> pendingReleaseHome(UUID playerId) {
        return Optional.ofNullable(pendingReleaseHome.get(playerId));
    }

    public void acknowledgeRelease(UUID playerId) {
        if (pendingReleaseHome.remove(playerId) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag heldList = new ListTag();
        for (Prisoner prisoner : held.values()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("Player", prisoner.playerId);
            value.putUUID("HomeNation", prisoner.homeNation);
            value.putUUID("HoldingNation", prisoner.holdingNation);
            value.putUUID("CapturedBy", prisoner.capturedBy);
            value.putLong("CapturedAt", prisoner.capturedAt);
            heldList.add(value);
        }
        tag.put("Held", heldList);
        ListTag releaseList = new ListTag();
        for (var entry : pendingReleaseHome.entrySet()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("Player", entry.getKey());
            value.putUUID("HomeNation", entry.getValue());
            releaseList.add(value);
        }
        tag.put("PendingReleases", releaseList);
        return tag;
    }

    public static PrisonerSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PrisonerSavedData data = new PrisonerSavedData();
        ListTag heldList = tag.getList("Held", Tag.TAG_COMPOUND);
        for (int index = 0; index < heldList.size(); index++) {
            CompoundTag value = heldList.getCompound(index);
            if (!value.hasUUID("Player") || !value.hasUUID("HomeNation")
                    || !value.hasUUID("HoldingNation") || !value.hasUUID("CapturedBy")) continue;
            UUID player = value.getUUID("Player");
            UUID home = value.getUUID("HomeNation");
            UUID holding = value.getUUID("HoldingNation");
            if (!home.equals(holding)) data.held.put(player, new Prisoner(player, home, holding,
                    value.getUUID("CapturedBy"), Math.max(0L, value.getLong("CapturedAt"))));
        }
        ListTag releaseList = tag.getList("PendingReleases", Tag.TAG_COMPOUND);
        for (int index = 0; index < releaseList.size(); index++) {
            CompoundTag value = releaseList.getCompound(index);
            if (value.hasUUID("Player") && value.hasUUID("HomeNation")) {
                data.pendingReleaseHome.put(value.getUUID("Player"), value.getUUID("HomeNation"));
            }
        }
        return data;
    }

    public static PrisonerSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PrisonerSavedData::new, PrisonerSavedData::load, null),
                "moveearth_prisoners");
    }

    private static boolean samePair(UUID leftA, UUID leftB, UUID rightA, UUID rightB) {
        return PrisonerPairPolicy.matches(leftA, leftB, rightA, rightB);
    }

    public enum CaptureResult { CAPTURED, ALREADY_HELD, INVALID }
    public record Prisoner(UUID playerId, UUID homeNation, UUID holdingNation,
                           UUID capturedBy, long capturedAt) { }
}
