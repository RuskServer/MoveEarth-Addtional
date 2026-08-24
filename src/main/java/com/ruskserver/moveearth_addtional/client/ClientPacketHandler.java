package com.ruskserver.moveearth_addtional.client;

import net.minecraft.client.Minecraft;

public class ClientPacketHandler {

    public static void handleOpenPvp(com.ruskserver.moveearth_addtional.network.S2C_OpenPvpScreenPacket packet) {
        Minecraft.getInstance().setScreen(new PvpScreen(packet.joined(), packet.active(), packet.hosting(),
                packet.matchRunning(), packet.entryCount(), packet.points(), packet.tasks(), packet.selectedLoadoutId()));
    }

    public static void handlePvpEntryState(com.ruskserver.moveearth_addtional.network.S2C_PvpEntryStatePacket packet) {
        if (Minecraft.getInstance().screen instanceof PvpScreen screen) {
            screen.updateEntryState(packet.joined(), packet.active(), packet.hosting(),
                    packet.matchRunning(), packet.entryCount());
        }
    }

    public static void handlePvpHud(com.ruskserver.moveearth_addtional.network.S2C_PvpHudPacket packet) {
        PvpClientState.updateHud(packet);
    }

    public static void handlePvpTeam(com.ruskserver.moveearth_addtional.network.S2C_PvpTeamPacket packet) {
        PvpClientState.updateAllies(packet.allies());
    }

    public static void handlePvpKillcam(com.ruskserver.moveearth_addtional.network.S2C_PvpKillcamPacket packet) {
        PvpClientState.startKillcam(packet);
    }

    public static void handlePvpResult(com.ruskserver.moveearth_addtional.network.S2C_PvpResultPacket packet) {
        PvpClientState.showResult(packet);
    }

    public static void handleOpenPvpTasks(com.ruskserver.moveearth_addtional.network.S2C_OpenPvpTasksPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof PvpTaskScreen screen) screen.update(packet);
        else minecraft.setScreen(new PvpTaskScreen(packet));
    }

    public static void handleOpenStatsScreen(com.ruskserver.moveearth_addtional.network.S2C_OpenStatsScreenPacket packet) {
        Minecraft.getInstance().setScreen(new StatsScreen(packet));
    }

    public static void handleAnnouncement(com.ruskserver.moveearth_addtional.network.S2C_AnnouncementPacket packet) {
        AnnouncementOverlay.showAnnouncement(packet.message());
    }

    public static void handleOpenDetectorScreen(com.ruskserver.moveearth_addtional.network.S2C_OpenDetectorScreenPacket packet) {
        Minecraft.getInstance().setScreen(new PlayerDetectorScreen(
                net.minecraft.network.chat.Component.literal("プレイヤー検知ブロック設定"),
                packet.ownerName(),
                packet.whitelist(),
                packet.onlinePlayers()
        ));
    }

    public static void handleSyncWhitelist(com.ruskserver.moveearth_addtional.network.S2C_SyncWhitelistPacket packet) {
        net.minecraft.client.gui.screens.Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof PlayerDetectorScreen detectorScreen) {
            detectorScreen.updateData(packet.whitelist(), packet.onlinePlayers());
        }
    }

    public static void handleSyncDetectorPayment(com.ruskserver.moveearth_addtional.network.S2C_SyncDetectorPaymentPacket packet) {
        net.minecraft.client.gui.screens.Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof PlayerDetectorScreen detectorScreen) {
            detectorScreen.updatePaymentData(
                    packet.pos(),
                    packet.isActive(),
                    packet.nextPaymentTime(),
                    packet.placedTime(),
                    packet.currentReference(),
                    packet.availableAccounts(),
                    packet.availableAccountNames()
            );
        }
    }
}
