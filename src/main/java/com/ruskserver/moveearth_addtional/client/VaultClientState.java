package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.s2.S2NationSnapshot;
import net.minecraft.resources.ResourceLocation;

/** Last server-authoritative vault position, used only while territory preview is visible. */
public final class VaultClientState {
    private static VaultMarker marker;

    private VaultClientState() { }

    public static void update(S2NationSnapshot snapshot) {
        if (!snapshot.vaultConfigured()) {
            marker = null;
            return;
        }
        try {
            marker = new VaultMarker(ResourceLocation.parse(snapshot.vaultDimension()),
                    snapshot.vaultChunkX(), snapshot.vaultChunkZ());
        } catch (IllegalArgumentException exception) {
            marker = null;
        }
    }

    public static VaultMarker marker() { return marker; }
    public static void clear() { marker = null; }

    public record VaultMarker(ResourceLocation dimension, int chunkX, int chunkZ) { }
}
