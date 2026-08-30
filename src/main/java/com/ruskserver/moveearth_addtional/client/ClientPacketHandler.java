package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ClientPacketHandler {

    public static void handleOpenJobs(S2C_OpenJobsScreenPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof JobsScreen screen) screen.update(packet);
        else minecraft.setScreen(new JobsScreen(packet));
    }

    public static void handleJobsLeaderboard(S2C_JobsLeaderboardPacket packet) {
        if (Minecraft.getInstance().screen instanceof JobsScreen screen) {
            screen.updateLeaderboard(packet);
        }
    }

    public static void handleJobShop(S2C_JobShopPacket packet) {
        if (Minecraft.getInstance().screen instanceof JobsScreen screen) {
            screen.updateShop(packet);
        }
    }

    public static void handleOpenPvp(S2C_OpenPvpScreenPacket packet) {
        Minecraft.getInstance().setScreen(new PvpScreen(packet.joined(), packet.active(), packet.hosting(),
                packet.matchRunning(), packet.entryCount(), packet.points(), packet.tasks(), packet.selectedLoadoutId()));
    }

    public static void handlePvpEntryState(S2C_PvpEntryStatePacket packet) {
        if (Minecraft.getInstance().screen instanceof PvpScreen screen) {
            screen.updateEntryState(packet.joined(), packet.active(), packet.hosting(),
                    packet.matchRunning(), packet.entryCount());
        }
    }

    public static void handlePvpHud(S2C_PvpHudPacket packet) {
        PvpClientState.updateHud(packet);
    }

    public static void handlePvpZone(S2C_PvpZonePacket packet) {
        PvpHardpointClientState.update(packet);
    }

    public static void handlePvpTeam(S2C_PvpTeamPacket packet) {
        PvpClientState.updateAllies(packet.allies());
    }

    public static void handlePvpKillcam(S2C_PvpKillcamPacket packet) {
        PvpClientState.startKillcam(packet);
    }

    public static void handlePvpResult(S2C_PvpResultPacket packet) {
        PvpClientState.showResult(packet);
    }

    public static void handleOpenPvpTasks(S2C_OpenPvpTasksPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof PvpTaskScreen screen) screen.update(packet);
        else minecraft.setScreen(new PvpTaskScreen(packet));
    }

    public static void handleOpenStatsScreen(S2C_OpenStatsScreenPacket packet) {
        Minecraft.getInstance().setScreen(new StatsScreen(packet));
    }

    public static void handleAnnouncement(S2C_AnnouncementPacket packet) {
        AnnouncementOverlay.showAnnouncement(packet.message());
    }

    public static void handleOpenDetectorScreen(S2C_OpenDetectorScreenPacket packet) {
        Minecraft.getInstance().setScreen(new PlayerDetectorScreen(
                Component.literal("プレイヤー検知ブロック設定"),
                packet.ownerName(),
                packet.whitelist(),
                packet.onlinePlayers()
        ));
    }

    public static void handleSyncWhitelist(S2C_SyncWhitelistPacket packet) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof PlayerDetectorScreen detectorScreen) {
            detectorScreen.updateData(packet.whitelist(), packet.onlinePlayers());
        }
    }

    public static void handleSyncDetectorPayment(S2C_SyncDetectorPaymentPacket packet) {
        Screen screen = Minecraft.getInstance().screen;
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

    public static void handleSyncLoadouts(S2C_SyncLoadoutsPacket packet) {
        PvpClientState.updateLoadouts(packet.loadouts());
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof PvpLoadoutEditorScreen editorScreen) {
            editorScreen.updateLoadouts(packet.loadouts());
        } else if (screen instanceof PvpScreen pvpScreen) {
            pvpScreen.updateLoadouts(packet.loadouts());
        }
    }

    public static void handleOpenLoadoutEditor(S2C_OpenLoadoutEditorPacket packet) {
        PvpClientState.updateLoadouts(packet.loadouts());
        Minecraft.getInstance().setScreen(new PvpLoadoutEditorScreen(packet.loadouts()));
    }

    public static void handleStartMapVote(S2C_StartMapVotePacket packet) {
        Minecraft.getInstance().setScreen(new PvpMapVoteScreen(packet.candidates(), packet.durationSeconds()));
    }

    public static void handleUpdateMapVote(S2C_UpdateMapVotePacket packet) {
        if (Minecraft.getInstance().screen instanceof PvpMapVoteScreen voteScreen) {
            voteScreen.updateVotes(packet.votes(), packet.secondsRemaining());
        }
    }

    public static void handleKillcamReplay(S2C_KillcamReplayPacket packet) {
        PvpClientState.startReplay(packet);
    }
}
