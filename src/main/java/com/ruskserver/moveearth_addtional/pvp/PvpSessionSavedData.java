package com.ruskserver.moveearth_addtional.pvp;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Crash-safe storage for inventories and player state replaced by an active PvP match.
 *
 * <p>Each stash carries a token. Restoring writes the token into the player's own
 * data ({@link PvpRestoreMarker}); the stash is only dropped once that player file
 * has been saved, so a crash on either side of a restore can neither lose the
 * original inventory nor apply it twice.
 */
public final class PvpSessionSavedData extends SavedData {
    private final Map<UUID, PvpPlayerSnapshot> snapshots = new HashMap<>();
    private final Map<UUID, UUID> tokens = new HashMap<>();

    public void put(UUID playerId, PvpPlayerSnapshot snapshot) {
        snapshots.put(playerId, snapshot);
        tokens.put(playerId, UUID.randomUUID());
        setDirty();
    }

    /** The current stash's token, or null for a stash saved before tokens existed. */
    public UUID token(UUID playerId) {
        return tokens.get(playerId);
    }

    /**
     * Writes the stashes to disk now. Called before a match replaces anyone's
     * inventory: the autosave writes player files before this data, so a stash
     * only marked dirty could be lost while the match inventory is saved.
     */
    public static void persistNow(MinecraftServer server) {
        server.overworld().getDataStorage().save();
    }

    public PvpPlayerSnapshot get(UUID playerId) {
        return snapshots.get(playerId);
    }

    public boolean contains(UUID playerId) {
        return snapshots.containsKey(playerId);
    }

    public void remove(UUID playerId) {
        tokens.remove(playerId);
        if (snapshots.remove(playerId) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        snapshots.forEach((id, snapshot) -> all.put(id.toString(), snapshot.save(registries)));
        tag.put("Snapshots", all);
        CompoundTag tokenTag = new CompoundTag();
        tokens.forEach((id, token) -> tokenTag.putUUID(id.toString(), token));
        tag.put("Tokens", tokenTag);
        return tag;
    }

    public static PvpSessionSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PvpSessionSavedData data = new PvpSessionSavedData();
        CompoundTag all = tag.getCompound("Snapshots");
        for (String key : all.getAllKeys()) {
            try {
                data.snapshots.put(UUID.fromString(key), PvpPlayerSnapshot.load(all.getCompound(key), registries));
            } catch (IllegalArgumentException ignored) {
            }
        }
        CompoundTag tokenTag = tag.getCompound("Tokens");
        for (String key : tokenTag.getAllKeys()) {
            try {
                UUID id = UUID.fromString(key);
                if (data.snapshots.containsKey(id) && tokenTag.hasUUID(key)) data.tokens.put(id, tokenTag.getUUID(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    public static PvpSessionSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PvpSessionSavedData::new, PvpSessionSavedData::load, null),
                "moveearth_pvp_sessions");
    }
}
