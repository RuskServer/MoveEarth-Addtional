package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.network.S2C_TerritoryPreviewPacket;
import com.ruskserver.moveearth_addtional.network.S2C_TerritoryClosurePacket;

public final class TerritoryPreviewClientState {
    private static S2C_TerritoryPreviewPacket preview;
    private static S2C_TerritoryClosurePacket closure;

    private TerritoryPreviewClientState() {
    }

    public static void update(S2C_TerritoryPreviewPacket packet) {
        preview = packet;
    }

    public static S2C_TerritoryPreviewPacket preview() {
        return preview;
    }

    public static void updateClosure(S2C_TerritoryClosurePacket packet) {
        closure = packet;
    }

    public static S2C_TerritoryClosurePacket closure() {
        return closure;
    }

    public static boolean active() {
        return preview != null || closure != null;
    }

    public static void clear() {
        preview = null;
        closure = null;
    }
}
