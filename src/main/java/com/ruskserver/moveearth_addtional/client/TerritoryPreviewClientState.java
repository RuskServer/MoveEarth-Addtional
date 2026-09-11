package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.network.S2C_TerritoryPreviewPacket;

public final class TerritoryPreviewClientState {
    private static S2C_TerritoryPreviewPacket preview;

    private TerritoryPreviewClientState() {
    }

    public static void update(S2C_TerritoryPreviewPacket packet) {
        preview = packet;
    }

    public static S2C_TerritoryPreviewPacket preview() {
        return preview;
    }

    public static boolean active() {
        return preview != null;
    }

    public static void clear() {
        preview = null;
    }
}
