package com.ruskserver.moveearth_addtional.pvp;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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
 *
 * <p><b>Why a stash can never be lost.</b> This file is written only by
 * {@link #writeNow}: synchronously on the server thread, as a whole, through
 * NeoForge's atomic write (temp file, fsync, atomic rename). It never goes through
 * NeoForge's asynchronous SavedData IO queue ({@link #save(File, HolderLookup.Provider)}
 * is overridden), so writes reach the disk in the order they were made and an older
 * queued write can never land on top of a newer stash. A match replaces a player's
 * inventory only after {@link #persistNow} has returned true for a file holding that
 * player's stash, so any later save of the player file (match inventory) follows the
 * stash on disk. If the server dies before the write, the old file is intact and the
 * player file still holds the original inventory; after it, the stash is on disk and
 * {@code PvpMatchManager.recoverIfNeeded} restores it at the next login. When the
 * write fails the join or start is abandoned before any inventory changes.
 */
public final class PvpSessionSavedData extends SavedData {
    private static final String FILE_ID = "moveearth_pvp_sessions";

    private final Map<UUID, PvpPlayerSnapshot> snapshots = new HashMap<>();
    private final Map<UUID, UUID> tokens = new HashMap<>();

    /** A player's stash and token, kept so a failed write can be undone exactly. */
    record Entry(PvpPlayerSnapshot snapshot, UUID token) {}

    /** Stores a new stash for the player and returns the one it replaced, or null. */
    public Entry put(UUID playerId, PvpPlayerSnapshot snapshot) {
        PvpPlayerSnapshot previousSnapshot = snapshots.put(playerId, snapshot);
        UUID previousToken = tokens.put(playerId, UUID.randomUUID());
        setDirty();
        return previousSnapshot == null ? null : new Entry(previousSnapshot, previousToken);
    }

    /** Undoes a {@link #put} whose write failed, so memory matches what is still on disk. */
    void reinstate(UUID playerId, Entry previous) {
        if (previous == null) {
            snapshots.remove(playerId);
            tokens.remove(playerId);
        } else {
            snapshots.put(playerId, previous.snapshot());
            if (previous.token() == null) tokens.remove(playerId);
            else tokens.put(playerId, previous.token());
        }
        setDirty();
    }

    /** The current stash's token, or null for a stash saved before tokens existed. */
    public UUID token(UUID playerId) {
        return tokens.get(playerId);
    }

    /**
     * Writes the stashes to disk now and returns whether they are there. Called before
     * a match replaces anyone's inventory: the autosave writes player files before this
     * data, so a stash only marked dirty could be lost while the match inventory is saved.
     * Writes only this file (not every SavedData of the overworld) and does not wait
     * on NeoForge's shared IO queue.
     */
    public static boolean persistNow(MinecraftServer server) {
        return get(server).writeNow(dataFile(server), server.registryAccess());
    }

    /** Autosave and shutdown path: same synchronous atomic write, never the async IO queue. */
    @Override
    public void save(File file, HolderLookup.Provider registries) {
        if (isDirty()) writeNow(file.toPath(), registries);
    }

    private boolean writeNow(Path file, HolderLookup.Provider registries) {
        CompoundTag root = new CompoundTag();
        root.put("data", save(new CompoundTag(), registries));
        NbtUtils.addCurrentDataVersion(root);
        try {
            IOUtilities.writeNbtCompressed(root, file);
            setDirty(false);
            return true;
        } catch (IOException | RuntimeException exception) {
            Moveearth_addtional.LOGGER.error("Could not write PvP inventory stashes to {}", file, exception);
            setDirty(true);
            return false;
        }
    }

    /** The file {@link #get} loads from: the overworld's data folder, as DimensionDataStorage names it. */
    private static Path dataFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE_ID + ".dat").normalize();
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
                FILE_ID);
    }
}
