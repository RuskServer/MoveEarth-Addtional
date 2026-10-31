package com.ruskserver.moveearth_addtional.client.loading;

import com.ruskserver.moveearth_addtional.config.ClientDisplayConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Only used after vanilla receives LEVEL_CHUNKS_LOAD_START; never bypasses the server hand-off. */
public final class EarlyWorldEntry {
    private EarlyWorldEntry() { }

    public static boolean receivedFootprint(ClientLevel level, LocalPlayer player) {
        if (!ClientDisplayConfig.EARLY_WORLD_ENTRY.getAsBoolean()) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != level || minecraft.player != player || player.level() != level) return false;
        var connection = minecraft.getConnection();
        if (connection == null || !connection.getConnection().isConnected()) return false;
        var bounds = player.getBoundingBox();
        return EarlyWorldEntryPolicy.footprintReady(bounds.minX, bounds.minZ, bounds.maxX, bounds.maxZ,
                // ClientLevel.hasChunk is always true, and getChunk(..., true) returns an empty placeholder.
                // Neither proves that this destination's real packet data arrived.
                (x, z) -> level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false) != null);
    }
}
