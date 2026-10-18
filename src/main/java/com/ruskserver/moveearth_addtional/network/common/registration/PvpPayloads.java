package com.ruskserver.moveearth_addtional.network.common.registration;

import com.ruskserver.moveearth_addtional.network.c2s.pvp.C2S_ClaimPvpTaskPacket;
import com.ruskserver.moveearth_addtional.network.c2s.pvp.C2S_DeleteLoadoutPacket;
import com.ruskserver.moveearth_addtional.network.c2s.pvp.C2S_PvpActionPacket;
import com.ruskserver.moveearth_addtional.network.c2s.pvp.C2S_PvpDeployPacket;
import com.ruskserver.moveearth_addtional.network.c2s.pvp.C2S_PvpExchangePacket;
import com.ruskserver.moveearth_addtional.network.c2s.pvp.C2S_ReorderLoadoutsPacket;
import com.ruskserver.moveearth_addtional.network.c2s.pvp.C2S_RequestPvpTasksPacket;
import com.ruskserver.moveearth_addtional.network.c2s.pvp.C2S_SaveLoadoutPacket;
import com.ruskserver.moveearth_addtional.network.c2s.pvp.C2S_VoteMapPacket;
import com.ruskserver.moveearth_addtional.network.common.C2SPacketGate;
import com.ruskserver.moveearth_addtional.network.common.C2SRateLimiter;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_OpenLoadoutEditorPacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_OpenPvpScreenPacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_OpenPvpTasksPacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpAllyPositionsPacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpDeployPacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpEntryStatePacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpHudPacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpKillcamPacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpResultPacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpTacticalMapPacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpTeamPacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpZonePacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_StartMapVotePacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_SyncLoadoutsPacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_UpdateMapVotePacket;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers the PvP minigame's payloads without changing their wire identifiers or codecs. */
public final class PvpPayloads {
    private PvpPayloads() {}

    public static void register(PayloadRegistrar registrar) {
        // C2S handlers run on the network thread only for the rate-limit check; see C2SPacketGate.
        PayloadRegistrar c2s = registrar.executesOn(HandlerThread.NETWORK);
        registrar.playToClient(S2C_OpenPvpScreenPacket.TYPE, S2C_OpenPvpScreenPacket.STREAM_CODEC, S2C_OpenPvpScreenPacket::handle);
        registrar.playToClient(S2C_PvpEntryStatePacket.TYPE, S2C_PvpEntryStatePacket.STREAM_CODEC, S2C_PvpEntryStatePacket::handle);
        c2s.playToServer(C2S_PvpActionPacket.TYPE, C2S_PvpActionPacket.STREAM_CODEC, C2SPacketGate.action(C2S_PvpActionPacket::handle));
        c2s.playToServer(C2S_PvpExchangePacket.TYPE, C2S_PvpExchangePacket.STREAM_CODEC, C2SPacketGate.action(C2S_PvpExchangePacket::handle));
        registrar.playToClient(S2C_PvpHudPacket.TYPE, S2C_PvpHudPacket.STREAM_CODEC, S2C_PvpHudPacket::handle);
        registrar.playToClient(S2C_PvpZonePacket.TYPE, S2C_PvpZonePacket.STREAM_CODEC, S2C_PvpZonePacket::handle);
        registrar.playToClient(S2C_PvpTeamPacket.TYPE, S2C_PvpTeamPacket.STREAM_CODEC, S2C_PvpTeamPacket::handle);
        registrar.playToClient(S2C_PvpDeployPacket.TYPE, S2C_PvpDeployPacket.STREAM_CODEC, S2C_PvpDeployPacket::handle);
        registrar.playToClient(S2C_PvpAllyPositionsPacket.TYPE, S2C_PvpAllyPositionsPacket.STREAM_CODEC, S2C_PvpAllyPositionsPacket::handle);
        registrar.playToClient(S2C_PvpTacticalMapPacket.TYPE, S2C_PvpTacticalMapPacket.STREAM_CODEC, S2C_PvpTacticalMapPacket::handle);
        c2s.playToServer(C2S_PvpDeployPacket.TYPE, C2S_PvpDeployPacket.STREAM_CODEC, C2SPacketGate.action(C2S_PvpDeployPacket::handle));
        registrar.playToClient(S2C_PvpKillcamPacket.TYPE, S2C_PvpKillcamPacket.STREAM_CODEC, S2C_PvpKillcamPacket::handle);
        registrar.playToClient(S2C_PvpResultPacket.TYPE, S2C_PvpResultPacket.STREAM_CODEC, S2C_PvpResultPacket::handle);
        registrar.playToClient(S2C_OpenPvpTasksPacket.TYPE, S2C_OpenPvpTasksPacket.STREAM_CODEC, S2C_OpenPvpTasksPacket::handle);
        c2s.playToServer(C2S_RequestPvpTasksPacket.TYPE, C2S_RequestPvpTasksPacket.STREAM_CODEC, C2SPacketGate.request(C2S_RequestPvpTasksPacket::handle));
        c2s.playToServer(C2S_ClaimPvpTaskPacket.TYPE, C2S_ClaimPvpTaskPacket.STREAM_CODEC, C2SPacketGate.action(C2S_ClaimPvpTaskPacket::handle));
        registrar.playToClient(S2C_SyncLoadoutsPacket.TYPE, S2C_SyncLoadoutsPacket.STREAM_CODEC, S2C_SyncLoadoutsPacket::handle);
        registrar.playToClient(S2C_OpenLoadoutEditorPacket.TYPE, S2C_OpenLoadoutEditorPacket.STREAM_CODEC, S2C_OpenLoadoutEditorPacket::handle);
        c2s.playToServer(C2S_SaveLoadoutPacket.TYPE, C2S_SaveLoadoutPacket.STREAM_CODEC, C2SPacketGate.action(C2S_SaveLoadoutPacket::handle));
        c2s.playToServer(C2S_DeleteLoadoutPacket.TYPE, C2S_DeleteLoadoutPacket.STREAM_CODEC, C2SPacketGate.action(C2S_DeleteLoadoutPacket::handle));
        c2s.playToServer(C2S_ReorderLoadoutsPacket.TYPE, C2S_ReorderLoadoutsPacket.STREAM_CODEC, C2SPacketGate.guard(
                // Each packet carries the whole order, so only the latest one matters.
                C2SRateLimiter.ACTION, C2SPacketGate.Overflow.COALESCE, C2S_ReorderLoadoutsPacket::handle));
        registrar.playToClient(S2C_StartMapVotePacket.TYPE, S2C_StartMapVotePacket.STREAM_CODEC, S2C_StartMapVotePacket::handle);
        registrar.playToClient(S2C_UpdateMapVotePacket.TYPE, S2C_UpdateMapVotePacket.STREAM_CODEC, S2C_UpdateMapVotePacket::handle);
        c2s.playToServer(C2S_VoteMapPacket.TYPE, C2S_VoteMapPacket.STREAM_CODEC, C2SPacketGate.action(C2S_VoteMapPacket::handle));
    }
}
