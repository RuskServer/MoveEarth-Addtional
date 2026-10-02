package com.ruskserver.moveearth_addtional.network.common.registration;

import com.ruskserver.moveearth_addtional.network.c2s.siege.C2S_PrisonerActionPacket;
import com.ruskserver.moveearth_addtional.network.c2s.siege.C2S_RecoveryDispatchActionPacket;
import com.ruskserver.moveearth_addtional.network.c2s.siege.C2S_RequestPrisonerScreenPacket;
import com.ruskserver.moveearth_addtional.network.c2s.siege.C2S_RequestRecoveryDispatchPacket;
import com.ruskserver.moveearth_addtional.network.c2s.siege.C2S_SiegeActionPacket;
import com.ruskserver.moveearth_addtional.network.c2s.siege.C2S_WarConsentPacket;
import com.ruskserver.moveearth_addtional.network.s2c.siege.S2C_PrisonerActionResultPacket;
import com.ruskserver.moveearth_addtional.network.s2c.siege.S2C_PrisonerSnapshotPacket;
import com.ruskserver.moveearth_addtional.network.s2c.siege.S2C_RecoveryDispatchActionResultPacket;
import com.ruskserver.moveearth_addtional.network.s2c.siege.S2C_RecoveryDispatchSnapshotPacket;
import com.ruskserver.moveearth_addtional.network.s2c.siege.S2C_WarConsentPromptPacket;
import com.ruskserver.moveearth_addtional.network.common.C2SPacketGate;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers siege payloads without changing their wire identifiers or codecs. */
public final class SiegePayloads {
    private SiegePayloads() {}

    public static void register(PayloadRegistrar registrar) {
        // C2S handlers run on the network thread only for the rate-limit check; see C2SPacketGate.
        PayloadRegistrar c2s = registrar.executesOn(HandlerThread.NETWORK);
        c2s.playToServer(C2S_SiegeActionPacket.TYPE, C2S_SiegeActionPacket.STREAM_CODEC, C2SPacketGate.action(C2S_SiegeActionPacket::handle));
        c2s.playToServer(C2S_RequestPrisonerScreenPacket.TYPE, C2S_RequestPrisonerScreenPacket.STREAM_CODEC, C2SPacketGate.request(C2S_RequestPrisonerScreenPacket::handle));
        c2s.playToServer(C2S_PrisonerActionPacket.TYPE, C2S_PrisonerActionPacket.STREAM_CODEC, C2SPacketGate.action(C2S_PrisonerActionPacket::handle));
        registrar.playToClient(S2C_PrisonerSnapshotPacket.TYPE, S2C_PrisonerSnapshotPacket.STREAM_CODEC, S2C_PrisonerSnapshotPacket::handle);
        registrar.playToClient(S2C_PrisonerActionResultPacket.TYPE, S2C_PrisonerActionResultPacket.STREAM_CODEC, S2C_PrisonerActionResultPacket::handle);
        c2s.playToServer(C2S_RequestRecoveryDispatchPacket.TYPE, C2S_RequestRecoveryDispatchPacket.STREAM_CODEC, C2SPacketGate.request(C2S_RequestRecoveryDispatchPacket::handle));
        c2s.playToServer(C2S_RecoveryDispatchActionPacket.TYPE, C2S_RecoveryDispatchActionPacket.STREAM_CODEC, C2SPacketGate.heavy(C2S_RecoveryDispatchActionPacket::handle));
        registrar.playToClient(S2C_RecoveryDispatchSnapshotPacket.TYPE, S2C_RecoveryDispatchSnapshotPacket.STREAM_CODEC, S2C_RecoveryDispatchSnapshotPacket::handle);
        registrar.playToClient(S2C_RecoveryDispatchActionResultPacket.TYPE, S2C_RecoveryDispatchActionResultPacket.STREAM_CODEC, S2C_RecoveryDispatchActionResultPacket::handle);
        // Confirming opens war on a nation: the tight budget of server-wide actions; the server also requires a recent prompt.
        c2s.playToServer(C2S_WarConsentPacket.TYPE, C2S_WarConsentPacket.STREAM_CODEC, C2SPacketGate.sensitive(C2S_WarConsentPacket::handle));
        registrar.playToClient(S2C_WarConsentPromptPacket.TYPE, S2C_WarConsentPromptPacket.STREAM_CODEC, S2C_WarConsentPromptPacket::handle);
    }
}
