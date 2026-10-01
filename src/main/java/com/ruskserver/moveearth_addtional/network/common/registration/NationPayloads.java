package com.ruskserver.moveearth_addtional.network.common.registration;

import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_CreateNationPacket;
import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_LinkDiscordAccountPacket;
import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_LinkNationDiscordPacket;
import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_NationApplicationActionPacket;
import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_NationAllyPermissionPacket;
import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_NationDiplomacyPacket;
import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_NationMembershipPacket;
import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_NationRolePacket;
import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_NationSettingsPacket;
import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_NationTreasuryPacket;
import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_NotificationActionPacket;
import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_OnboardingActionPacket;
import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_RequestNationNotificationsPacket;
import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_RequestS2HubPacket;
import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_S2HubActionPacket;
import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_UpdateNationNotificationsPacket;
import com.ruskserver.moveearth_addtional.network.s2c.nation.S2C_CloseOnboardingPacket;
import com.ruskserver.moveearth_addtional.network.s2c.nation.S2C_NationApplicationsPacket;
import com.ruskserver.moveearth_addtional.network.s2c.nation.S2C_NationNameplatesPacket;
import com.ruskserver.moveearth_addtional.network.s2c.nation.S2C_NationTreasuryPacket;
import com.ruskserver.moveearth_addtional.network.s2c.nation.S2C_OnboardingPacket;
import com.ruskserver.moveearth_addtional.network.s2c.nation.S2C_OpenNationNotificationsPacket;
import com.ruskserver.moveearth_addtional.network.s2c.nation.S2C_S2HubSnapshotPacket;
import com.ruskserver.moveearth_addtional.network.common.C2SPacketGate;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers nation payloads without changing their wire identifiers or codecs. */
public final class NationPayloads {
    private NationPayloads() {}

    public static void register(PayloadRegistrar registrar) {
        // C2S handlers run on the network thread only for the rate-limit check; see C2SPacketGate.
        PayloadRegistrar c2s = registrar.executesOn(HandlerThread.NETWORK);
        c2s.playToServer(C2S_RequestS2HubPacket.TYPE, C2S_RequestS2HubPacket.STREAM_CODEC, C2SPacketGate.request(C2S_RequestS2HubPacket::handle));
        c2s.playToServer(C2S_S2HubActionPacket.TYPE, C2S_S2HubActionPacket.STREAM_CODEC, C2SPacketGate.request(C2S_S2HubActionPacket::handle));
        registrar.playToClient(S2C_S2HubSnapshotPacket.TYPE, S2C_S2HubSnapshotPacket.STREAM_CODEC, S2C_S2HubSnapshotPacket::handle);
        registrar.playToClient(S2C_NationNameplatesPacket.TYPE, S2C_NationNameplatesPacket.STREAM_CODEC, S2C_NationNameplatesPacket::handle);
        registrar.playToClient(S2C_NationTreasuryPacket.TYPE, S2C_NationTreasuryPacket.STREAM_CODEC, S2C_NationTreasuryPacket::handle);
        c2s.playToServer(C2S_NationTreasuryPacket.TYPE, C2S_NationTreasuryPacket.STREAM_CODEC, C2SPacketGate.heavy(C2S_NationTreasuryPacket::handle));
        c2s.playToServer(C2S_CreateNationPacket.TYPE, C2S_CreateNationPacket.STREAM_CODEC, C2SPacketGate.sensitive(C2S_CreateNationPacket::handle));
        c2s.playToServer(C2S_NationMembershipPacket.TYPE, C2S_NationMembershipPacket.STREAM_CODEC, C2SPacketGate.action(C2S_NationMembershipPacket::handle));
        c2s.playToServer(C2S_NationRolePacket.TYPE, C2S_NationRolePacket.STREAM_CODEC, C2SPacketGate.action(C2S_NationRolePacket::handle));
        c2s.playToServer(C2S_NationDiplomacyPacket.TYPE, C2S_NationDiplomacyPacket.STREAM_CODEC, C2SPacketGate.action(C2S_NationDiplomacyPacket::handle));
        c2s.playToServer(C2S_NationAllyPermissionPacket.TYPE, C2S_NationAllyPermissionPacket.STREAM_CODEC, C2SPacketGate.action(C2S_NationAllyPermissionPacket::handle));
        c2s.playToServer(C2S_NationSettingsPacket.TYPE, C2S_NationSettingsPacket.STREAM_CODEC, C2SPacketGate.action(C2S_NationSettingsPacket::handle));
        c2s.playToServer(C2S_RequestNationNotificationsPacket.TYPE, C2S_RequestNationNotificationsPacket.STREAM_CODEC, C2SPacketGate.request(C2S_RequestNationNotificationsPacket::handle));
        c2s.playToServer(C2S_UpdateNationNotificationsPacket.TYPE, C2S_UpdateNationNotificationsPacket.STREAM_CODEC, C2SPacketGate.action(C2S_UpdateNationNotificationsPacket::handle));
        c2s.playToServer(C2S_LinkNationDiscordPacket.TYPE, C2S_LinkNationDiscordPacket.STREAM_CODEC, C2SPacketGate.sensitive(C2S_LinkNationDiscordPacket::handle));
        c2s.playToServer(C2S_LinkDiscordAccountPacket.TYPE, C2S_LinkDiscordAccountPacket.STREAM_CODEC, C2SPacketGate.sensitive(C2S_LinkDiscordAccountPacket::handle));
        c2s.playToServer(C2S_NotificationActionPacket.TYPE, C2S_NotificationActionPacket.STREAM_CODEC, C2SPacketGate.heavy(C2S_NotificationActionPacket::handle));
        registrar.playToClient(S2C_OpenNationNotificationsPacket.TYPE, S2C_OpenNationNotificationsPacket.STREAM_CODEC, S2C_OpenNationNotificationsPacket::handle);
        registrar.playToClient(S2C_OnboardingPacket.TYPE, S2C_OnboardingPacket.STREAM_CODEC, S2C_OnboardingPacket::handle);
        registrar.playToClient(S2C_CloseOnboardingPacket.TYPE, S2C_CloseOnboardingPacket.STREAM_CODEC, S2C_CloseOnboardingPacket::handle);
        c2s.playToServer(C2S_OnboardingActionPacket.TYPE, C2S_OnboardingActionPacket.STREAM_CODEC, C2SPacketGate.action(C2S_OnboardingActionPacket::handle));
        registrar.playToClient(S2C_NationApplicationsPacket.TYPE, S2C_NationApplicationsPacket.STREAM_CODEC, S2C_NationApplicationsPacket::handle);
        c2s.playToServer(C2S_NationApplicationActionPacket.TYPE, C2S_NationApplicationActionPacket.STREAM_CODEC, C2SPacketGate.action(C2S_NationApplicationActionPacket::handle));
    }
}
