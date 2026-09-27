package com.ruskserver.moveearth_addtional.network.common.registration;

import com.ruskserver.moveearth_addtional.network.c2s.siege.C2S_PrisonerActionPacket;
import com.ruskserver.moveearth_addtional.network.c2s.siege.C2S_RecoveryDispatchActionPacket;
import com.ruskserver.moveearth_addtional.network.c2s.siege.C2S_RequestPrisonerScreenPacket;
import com.ruskserver.moveearth_addtional.network.c2s.siege.C2S_RequestRecoveryDispatchPacket;
import com.ruskserver.moveearth_addtional.network.c2s.siege.C2S_SiegeActionPacket;
import com.ruskserver.moveearth_addtional.network.s2c.siege.S2C_PrisonerActionResultPacket;
import com.ruskserver.moveearth_addtional.network.s2c.siege.S2C_PrisonerSnapshotPacket;
import com.ruskserver.moveearth_addtional.network.s2c.siege.S2C_RecoveryDispatchActionResultPacket;
import com.ruskserver.moveearth_addtional.network.s2c.siege.S2C_RecoveryDispatchSnapshotPacket;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers siege payloads without changing their wire identifiers or codecs. */
public final class SiegePayloads {
    private SiegePayloads() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(C2S_SiegeActionPacket.TYPE, C2S_SiegeActionPacket.STREAM_CODEC, C2S_SiegeActionPacket::handle);
        registrar.playToServer(C2S_RequestPrisonerScreenPacket.TYPE, C2S_RequestPrisonerScreenPacket.STREAM_CODEC, C2S_RequestPrisonerScreenPacket::handle);
        registrar.playToServer(C2S_PrisonerActionPacket.TYPE, C2S_PrisonerActionPacket.STREAM_CODEC, C2S_PrisonerActionPacket::handle);
        registrar.playToClient(S2C_PrisonerSnapshotPacket.TYPE, S2C_PrisonerSnapshotPacket.STREAM_CODEC, S2C_PrisonerSnapshotPacket::handle);
        registrar.playToClient(S2C_PrisonerActionResultPacket.TYPE, S2C_PrisonerActionResultPacket.STREAM_CODEC, S2C_PrisonerActionResultPacket::handle);
        registrar.playToServer(C2S_RequestRecoveryDispatchPacket.TYPE, C2S_RequestRecoveryDispatchPacket.STREAM_CODEC, C2S_RequestRecoveryDispatchPacket::handle);
        registrar.playToServer(C2S_RecoveryDispatchActionPacket.TYPE, C2S_RecoveryDispatchActionPacket.STREAM_CODEC, C2S_RecoveryDispatchActionPacket::handle);
        registrar.playToClient(S2C_RecoveryDispatchSnapshotPacket.TYPE, S2C_RecoveryDispatchSnapshotPacket.STREAM_CODEC, S2C_RecoveryDispatchSnapshotPacket::handle);
        registrar.playToClient(S2C_RecoveryDispatchActionResultPacket.TYPE, S2C_RecoveryDispatchActionResultPacket.STREAM_CODEC, S2C_RecoveryDispatchActionResultPacket::handle);
    }
}
