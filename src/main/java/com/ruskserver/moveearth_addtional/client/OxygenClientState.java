package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.network.S2C_SyncOxygenPacket;

public class OxygenClientState {
    public static float oxygenPercent = 1.0f;
    public static float filterPercent = 1.0f;
    public static boolean hasGasMask = false;
    public static boolean isDangerZone = false;
    public static boolean isExtremeZone = false;
    public static float consumptionRate = 1.0f;
    public static boolean isSprinting = false;
    public static boolean isMining = false;
    public static boolean isCombat = false;

    public static void update(S2C_SyncOxygenPacket packet) {
        oxygenPercent = packet.oxygenPercent();
        filterPercent = packet.filterPercent();
        hasGasMask = packet.hasGasMask();
        isDangerZone = packet.isDangerZone();
        isExtremeZone = packet.isExtremeZone();
        consumptionRate = packet.consumptionRate();
        isSprinting = packet.isSprinting();
        isMining = packet.isMining();
        isCombat = packet.isCombat();
    }
}
