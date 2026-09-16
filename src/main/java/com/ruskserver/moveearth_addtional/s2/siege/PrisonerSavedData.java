package com.ruskserver.moveearth_addtional.s2.siege;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistent prisoner ownership and releases awaiting an offline player's next login. */
public final class PrisonerSavedData extends SavedData {
    private final Map<UUID, Prisoner> held = new LinkedHashMap<>();
    private final Map<UUID, Custody> custody = new LinkedHashMap<>();
    private final Map<UUID, CaptivityWindow> captivityWindows = new LinkedHashMap<>();
    private final Map<UUID, UUID> pendingReleaseHome = new LinkedHashMap<>();

    public CaptureResult capture(UUID playerId, UUID homeNation, UUID holdingNation,
                                 UUID capturedBy, long capturedAt) {
        if (playerId == null || homeNation == null || holdingNation == null || capturedBy == null
                || homeNation.equals(holdingNation)) return CaptureResult.INVALID;
        if (held.containsKey(playerId)) return CaptureResult.ALREADY_HELD;
        held.put(playerId, new Prisoner(playerId, homeNation, homeNation, holdingNation, capturedBy,
                Math.max(0L, capturedAt), S2TerritoryConfig.captivityMaxTicks(), null, null, null, null));
        captivityWindows.put(playerId, new CaptivityWindow(playerId, homeNation, homeNation, holdingNation,
                S2TerritoryConfig.captivityMaxTicks()));
        pendingReleaseHome.remove(playerId);
        setDirty();
        return CaptureResult.CAPTURED;
    }

    public Optional<Prisoner> prisoner(UUID playerId) {
        return Optional.ofNullable(held.get(playerId));
    }

    public Optional<Prisoner> release(UUID playerId) {
        Prisoner removed = held.remove(playerId);
        if (removed != null) {
            pendingReleaseHome.put(playerId, removed.homeNation);
            setDirty();
        }
        return Optional.ofNullable(removed);
    }

    public CustodyResult beginCustody(UUID playerId, UUID homeNation, UUID conflictNation, UUID holdingNation,
                                      UUID captor, ResourceLocation dimension, BlockPos position) {
        return beginCustody(playerId, homeNation, conflictNation, holdingNation, captor, dimension, position,
                null, null);
    }

    public CustodyResult beginCustody(UUID playerId, UUID homeNation, UUID conflictNation, UUID holdingNation,
                                      UUID captor, ResourceLocation dimension, BlockPos position,
                                      UUID siegeId, UUID contractId) {
        if (playerId == null || homeNation == null || conflictNation == null || holdingNation == null || captor == null
                || dimension == null || position == null || conflictNation.equals(holdingNation)) {
            return CustodyResult.INVALID;
        }
        if (held.containsKey(playerId) || custody.containsKey(playerId)) return CustodyResult.ALREADY_RESTRAINED;
        CaptivityWindow existing = captivityWindows.get(playerId);
        long remaining = existing != null && existing.homeNation.equals(homeNation)
                && existing.conflictNation.equals(conflictNation)
                && existing.holdingNation.equals(holdingNation)
                ? existing.remainingTicks : S2TerritoryConfig.captivityMaxTicks();
        if (remaining <= 0L) return CustodyResult.INVALID;
        captivityWindows.put(playerId, new CaptivityWindow(playerId, homeNation, conflictNation, holdingNation, remaining));
        custody.put(playerId, new Custody(playerId, homeNation, conflictNation, holdingNation, captor,
                remaining, dimension, position.immutable(), siegeId, contractId));
        setDirty();
        return CustodyResult.RESTRAINED;
    }

    public Optional<Custody> custody(UUID playerId) { return Optional.ofNullable(custody.get(playerId)); }

    public Optional<Custody> custodyByCaptor(UUID captor) {
        return custody.values().stream().filter(value -> value.captor.equals(captor)).findFirst();
    }

    public void moveCustody(UUID playerId, ResourceLocation dimension, BlockPos position) {
        Custody old = custody.get(playerId);
        if (old == null || dimension == null || position == null) return;
        custody.put(playerId, new Custody(old.playerId, old.homeNation, old.conflictNation, old.holdingNation,
                old.captor, old.remainingTicks, dimension, position.immutable(), old.siegeId, old.contractId));
        setDirty();
    }

    public Optional<Custody> releaseCustody(UUID playerId) {
        Custody removed = custody.remove(playerId);
        if (removed != null) setDirty();
        return Optional.ofNullable(removed);
    }

    /** Formal release: unlike a field rescue, an offline captive must return to their home destination. */
    public Optional<Custody> releaseCustodyToHome(UUID playerId) {
        Custody removed = custody.remove(playerId);
        if (removed != null) {
            pendingReleaseHome.put(playerId, removed.homeNation);
            setDirty();
        }
        return Optional.ofNullable(removed);
    }

    /** Transfers an active escort without changing the original captivity window. */
    public boolean transferCustody(UUID playerId, UUID expectedCaptor, UUID newCaptor) {
        Custody old = custody.get(playerId);
        if (old == null || expectedCaptor == null || newCaptor == null
                || expectedCaptor.equals(newCaptor) || !old.captor.equals(expectedCaptor)
                || custodyByCaptor(newCaptor).isPresent()) return false;
        custody.put(playerId, new Custody(old.playerId, old.homeNation, old.conflictNation, old.holdingNation,
                newCaptor, old.remainingTicks, old.dimension, old.position, old.siegeId, old.contractId));
        setDirty();
        return true;
    }

    public CaptureResult imprison(UUID playerId, ResourceLocation jailDimension, BlockPos jailPos,
                                  long capturedAt) {
        Custody value = custody.remove(playerId);
        if (value == null || jailDimension == null || jailPos == null) return CaptureResult.INVALID;
        held.put(playerId, new Prisoner(value.playerId, value.homeNation, value.conflictNation, value.holdingNation,
                value.captor, Math.max(0L, capturedAt), value.remainingTicks,
                jailDimension, jailPos.immutable(), value.siegeId, value.contractId));
        pendingReleaseHome.remove(playerId);
        setDirty();
        return CaptureResult.CAPTURED;
    }

    /** Advances only while the configured JST server opening window is active. */
    public List<TimedRelease> advanceCaptivity(long ticks) {
        if (ticks <= 0L) return List.of();
        java.util.ArrayList<TimedRelease> released = new java.util.ArrayList<>();
        for (var entry : new java.util.ArrayList<>(captivityWindows.entrySet())) {
            CaptivityWindow old = entry.getValue();
            captivityWindows.put(entry.getKey(), new CaptivityWindow(old.playerId, old.homeNation,
                    old.conflictNation, old.holdingNation, CaptivityPolicy.advance(old.remainingTicks, ticks)));
        }
        for (var entry : new java.util.ArrayList<>(custody.entrySet())) {
            Custody old = entry.getValue();
            long remaining = captivityWindows.getOrDefault(old.playerId,
                    new CaptivityWindow(old.playerId, old.homeNation, old.conflictNation, old.holdingNation,
                            CaptivityPolicy.advance(old.remainingTicks, ticks))).remainingTicks;
            if (remaining == 0L) {
                custody.remove(entry.getKey());
                pendingReleaseHome.put(old.playerId, old.homeNation);
                released.add(new TimedRelease(old.playerId, old.homeNation));
            } else {
                custody.put(entry.getKey(), new Custody(old.playerId, old.homeNation, old.conflictNation, old.holdingNation,
                        old.captor, remaining, old.dimension, old.position, old.siegeId, old.contractId));
            }
        }
        for (var entry : new java.util.ArrayList<>(held.entrySet())) {
            Prisoner old = entry.getValue();
            long remaining = captivityWindows.getOrDefault(old.playerId,
                    new CaptivityWindow(old.playerId, old.homeNation, old.conflictNation, old.holdingNation,
                            CaptivityPolicy.advance(old.remainingTicks, ticks))).remainingTicks;
            if (remaining == 0L) {
                held.remove(entry.getKey());
                pendingReleaseHome.put(old.playerId, old.homeNation);
                released.add(new TimedRelease(old.playerId, old.homeNation));
            } else {
                held.put(entry.getKey(), new Prisoner(old.playerId, old.homeNation, old.conflictNation, old.holdingNation,
                        old.capturedBy, old.capturedAt, remaining, old.jailDimension, old.jailPos,
                        old.siegeId, old.contractId));
            }
        }
        if (!released.isEmpty() || !captivityWindows.isEmpty()) setDirty();
        return released;
    }

    public List<Prisoner> between(UUID firstNation, UUID secondNation) {
        return held.values().stream().filter(value -> samePair(
                value.conflictNation, value.holdingNation, firstNation, secondNation)).toList();
    }

    public int countBetween(UUID firstNation, UUID secondNation) {
        return between(firstNation, secondNation).size();
    }

    public List<Prisoner> betweenNation(UUID nationId) {
        if (nationId == null) return List.of();
        return held.values().stream().filter(value -> value.homeNation.equals(nationId)
                || value.conflictNation.equals(nationId) || value.holdingNation.equals(nationId)).toList();
    }

    public List<Prisoner> prisoners() { return List.copyOf(held.values()); }
    public List<Custody> custodyRecords() { return List.copyOf(custody.values()); }
    public List<CaptivityWindow> captivityWindows() { return List.copyOf(captivityWindows.values()); }

    public void clearCaptivityWindow(UUID playerId) {
        if (captivityWindows.remove(playerId) != null) setDirty();
    }

    public boolean hasNation(UUID nationId) {
        if (nationId == null) return false;
        return held.values().stream().anyMatch(value -> value.homeNation.equals(nationId)
                || value.conflictNation.equals(nationId) || value.holdingNation.equals(nationId))
                || custody.values().stream().anyMatch(value -> value.homeNation.equals(nationId)
                || value.conflictNation.equals(nationId) || value.holdingNation.equals(nationId));
    }

    public void removePendingReleaseNation(UUID nationId) {
        if (pendingReleaseHome.entrySet().removeIf(entry -> entry.getValue().equals(nationId))) setDirty();
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
            value.putUUID("ConflictNation", prisoner.conflictNation);
            value.putUUID("HoldingNation", prisoner.holdingNation);
            value.putUUID("CapturedBy", prisoner.capturedBy);
            value.putLong("CapturedAt", prisoner.capturedAt);
            value.putLong("Remaining", prisoner.remainingTicks);
            if (prisoner.jailDimension != null) value.putString("JailDimension", prisoner.jailDimension.toString());
            if (prisoner.jailPos != null) value.putLong("JailPos", prisoner.jailPos.asLong());
            if (prisoner.siegeId != null) value.putUUID("Siege", prisoner.siegeId);
            if (prisoner.contractId != null) value.putUUID("Contract", prisoner.contractId);
            heldList.add(value);
        }
        tag.put("Held", heldList);
        ListTag custodyList = new ListTag();
        for (Custody value : custody.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", value.playerId);
            entry.putUUID("HomeNation", value.homeNation);
            entry.putUUID("ConflictNation", value.conflictNation);
            entry.putUUID("HoldingNation", value.holdingNation);
            entry.putUUID("Captor", value.captor);
            entry.putLong("Remaining", value.remainingTicks);
            entry.putString("Dimension", value.dimension.toString());
            entry.putLong("Position", value.position.asLong());
            if (value.siegeId != null) entry.putUUID("Siege", value.siegeId);
            if (value.contractId != null) entry.putUUID("Contract", value.contractId);
            custodyList.add(entry);
        }
        tag.put("Custody", custodyList);
        ListTag windowList = new ListTag();
        for (CaptivityWindow value : captivityWindows.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", value.playerId);
            entry.putUUID("HomeNation", value.homeNation);
            entry.putUUID("ConflictNation", value.conflictNation);
            entry.putUUID("HoldingNation", value.holdingNation);
            entry.putLong("Remaining", value.remainingTicks);
            windowList.add(entry);
        }
        tag.put("CaptivityWindows", windowList);
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
            UUID conflict = value.hasUUID("ConflictNation") ? value.getUUID("ConflictNation") : home;
            if (!conflict.equals(holding)) data.held.put(player, new Prisoner(player, home, conflict, holding,
                    value.getUUID("CapturedBy"), Math.max(0L, value.getLong("CapturedAt")),
                    value.contains("Remaining") ? Math.max(1L, value.getLong("Remaining"))
                            : S2TerritoryConfig.captivityMaxTicks(),
                    value.contains("JailDimension") ? ResourceLocation.tryParse(value.getString("JailDimension")) : null,
                    value.contains("JailPos") ? BlockPos.of(value.getLong("JailPos")) : null,
                    value.hasUUID("Siege") ? value.getUUID("Siege") : null,
                    value.hasUUID("Contract") ? value.getUUID("Contract") : null));
        }
        ListTag custodyList = tag.getList("Custody", Tag.TAG_COMPOUND);
        for (int index = 0; index < custodyList.size(); index++) {
            CompoundTag value = custodyList.getCompound(index);
            if (!value.hasUUID("Player") || !value.hasUUID("HomeNation")
                    || !value.hasUUID("HoldingNation") || !value.hasUUID("Captor")
                    || !value.contains("Dimension") || !value.contains("Position")) continue;
            ResourceLocation dimension = ResourceLocation.tryParse(value.getString("Dimension"));
            if (dimension == null) continue;
            UUID player = value.getUUID("Player");
            UUID home = value.getUUID("HomeNation");
            data.custody.put(player, new Custody(player, home,
                    value.hasUUID("ConflictNation") ? value.getUUID("ConflictNation") : home,
                    value.getUUID("HoldingNation"), value.getUUID("Captor"),
                    Math.max(1L, value.getLong("Remaining")), dimension,
                    BlockPos.of(value.getLong("Position")),
                    value.hasUUID("Siege") ? value.getUUID("Siege") : null,
                    value.hasUUID("Contract") ? value.getUUID("Contract") : null));
        }
        ListTag windowList = tag.getList("CaptivityWindows", Tag.TAG_COMPOUND);
        for (int index = 0; index < windowList.size(); index++) {
            CompoundTag value = windowList.getCompound(index);
            if (!value.hasUUID("Player") || !value.hasUUID("HomeNation")
                    || !value.hasUUID("HoldingNation")) continue;
            UUID player = value.getUUID("Player");
            UUID home = value.getUUID("HomeNation");
            data.captivityWindows.put(player, new CaptivityWindow(player, home,
                    value.hasUUID("ConflictNation") ? value.getUUID("ConflictNation") : home,
                    value.getUUID("HoldingNation"),
                    Math.max(0L, value.getLong("Remaining"))));
        }
        for (Prisoner prisoner : data.held.values()) {
            data.captivityWindows.putIfAbsent(prisoner.playerId, new CaptivityWindow(prisoner.playerId,
                    prisoner.homeNation, prisoner.conflictNation, prisoner.holdingNation, prisoner.remainingTicks));
        }
        for (Custody value : data.custody.values()) {
            data.captivityWindows.putIfAbsent(value.playerId, new CaptivityWindow(value.playerId,
                    value.homeNation, value.conflictNation, value.holdingNation, value.remainingTicks));
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
    public enum CustodyResult { RESTRAINED, ALREADY_RESTRAINED, INVALID }
    public record Custody(UUID playerId, UUID homeNation, UUID conflictNation, UUID holdingNation, UUID captor,
                          long remainingTicks, ResourceLocation dimension, BlockPos position,
                          UUID siegeId, UUID contractId) {
        public Custody(UUID playerId, UUID homeNation, UUID conflictNation, UUID holdingNation, UUID captor,
                       long remainingTicks, ResourceLocation dimension, BlockPos position) {
            this(playerId, homeNation, conflictNation, holdingNation, captor, remainingTicks,
                    dimension, position, null, null);
        }
    }
    public record CaptivityWindow(UUID playerId, UUID homeNation, UUID conflictNation, UUID holdingNation,
                                  long remainingTicks) { }
    public record TimedRelease(UUID playerId, UUID homeNation) { }
    public record Prisoner(UUID playerId, UUID homeNation, UUID conflictNation, UUID holdingNation,
                           UUID capturedBy, long capturedAt, long remainingTicks,
                           ResourceLocation jailDimension, BlockPos jailPos,
                           UUID siegeId, UUID contractId) {
        public Prisoner(UUID playerId, UUID homeNation, UUID conflictNation, UUID holdingNation,
                        UUID capturedBy, long capturedAt, long remainingTicks,
                        ResourceLocation jailDimension, BlockPos jailPos) {
            this(playerId, homeNation, conflictNation, holdingNation, capturedBy, capturedAt,
                    remainingTicks, jailDimension, jailPos, null, null);
        }
        public Prisoner(UUID playerId, UUID homeNation, UUID holdingNation,
                        UUID capturedBy, long capturedAt) {
            this(playerId, homeNation, homeNation, holdingNation, capturedBy, capturedAt,
                    S2TerritoryConfig.captivityMaxTicks(), null, null, null, null);
        }
    }
}
