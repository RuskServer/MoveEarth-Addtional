package com.ruskserver.moveearth_addtional.detector;

import com.ruskserver.moveearth_addtional.block.entity.PlayerDetectorBlockEntity;
import com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_DetectorStatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends a player the detector's screen, to open it or to refresh it after a change. */
public final class DetectorScreenSync {
    private DetectorScreenSync() {
    }

    public static void open(ServerPlayer player, PlayerDetectorBlockEntity detector) {
        send(player, detector, true, true, "");
    }

    public static void refresh(ServerPlayer player, PlayerDetectorBlockEntity detector, boolean success, String message) {
        send(player, detector, false, success, message);
    }

    private static void send(ServerPlayer player, PlayerDetectorBlockEntity detector, boolean open,
                             boolean success, String message) {
        long balance = detector.getOwnerUUID() == null && detector.getOwnerNation() == null ? 0L
                : EconomyLedgerSavedData.get(player.server).balance(DetectorOwnership.account(detector));
        PacketDistributor.sendToPlayer(player, new S2C_DetectorStatePacket(
                detector.getBlockPos(),
                open,
                detector.getDetectorName(),
                clip(DetectorOwnership.ownerLabel(player.server, detector), 64),
                detector.getOwnerNation() != null,
                DetectorOwnership.mayManage(player, detector),
                detector.isActive(),
                detector.getNextPaymentTime(),
                detector.getPlacedTime(),
                balance,
                success,
                clip(message, 160)));
    }

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
