package com.ruskserver.moveearth_addtional.network.common.registration;

import com.ruskserver.moveearth_addtional.network.c2s.other.C2S_ConfigurePaymentPacket;
import com.ruskserver.moveearth_addtional.network.c2s.other.C2S_EventScreenActionPacket;
import com.ruskserver.moveearth_addtional.network.c2s.other.C2S_JobsActionPacket;
import com.ruskserver.moveearth_addtional.network.c2s.other.C2S_RequestRegionViewPacket;
import com.ruskserver.moveearth_addtional.network.c2s.other.C2S_WikiOpenedPacket;
import com.ruskserver.moveearth_addtional.network.c2s.other.C2S_RequestReinforcementScanPacket;
import com.ruskserver.moveearth_addtional.network.c2s.other.C2S_RequestTerritoryMapPacket;
import com.ruskserver.moveearth_addtional.network.c2s.other.C2S_RequestTerritoryPreviewPacket;
import com.ruskserver.moveearth_addtional.network.c2s.other.C2S_SetDetectorNamePacket;
import com.ruskserver.moveearth_addtional.network.c2s.other.C2S_SetTerritoryCoreRadiusPacket;
import com.ruskserver.moveearth_addtional.network.c2s.other.C2S_SetWeldingBrushPacket;
import com.ruskserver.moveearth_addtional.network.c2s.other.C2S_ValidateTerritoryCorePacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_AnnouncementPacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_EventHudPacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_EventScreenPacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_JobsLeaderboardPacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_DetectorStatePacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_OpenJobsScreenPacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_OpenStatsScreenPacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_OpenTerritoryCoreScreenPacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_WarehouseZonesPacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_JoinCinematicPacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_LiveStreamersPacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_RegionSnapshotPacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_ReinforcementDeltaPacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_ReinforcementSnapshotPacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_S2ActionResultPacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_SyncOxygenPacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_MekaSuitShieldPacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_TutorialPacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_TerritoryClosurePacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_TerritoryCoreHealthPacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_TerritoryMapPacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_TerritoryPreviewPacket;
import com.ruskserver.moveearth_addtional.network.common.C2SPacketGate;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers other payloads without changing their wire identifiers or codecs. */
public final class OtherPayloads {
    private OtherPayloads() {}

    public static void register(PayloadRegistrar registrar) {
        // C2S handlers run on the network thread only for the rate-limit check; see C2SPacketGate.
        PayloadRegistrar c2s = registrar.executesOn(HandlerThread.NETWORK);
        c2s.playToServer(C2S_RequestRegionViewPacket.TYPE, C2S_RequestRegionViewPacket.STREAM_CODEC, C2SPacketGate.request(C2S_RequestRegionViewPacket::handle));
        c2s.playToServer(C2S_WikiOpenedPacket.TYPE, C2S_WikiOpenedPacket.STREAM_CODEC, C2SPacketGate.request(C2S_WikiOpenedPacket::handle));
        registrar.playToClient(S2C_RegionSnapshotPacket.TYPE, S2C_RegionSnapshotPacket.STREAM_CODEC, S2C_RegionSnapshotPacket::handle);
        registrar.playToClient(S2C_S2ActionResultPacket.TYPE, S2C_S2ActionResultPacket.STREAM_CODEC, S2C_S2ActionResultPacket::handle);
        c2s.playToServer(C2S_RequestTerritoryPreviewPacket.TYPE, C2S_RequestTerritoryPreviewPacket.STREAM_CODEC, C2SPacketGate.request(C2S_RequestTerritoryPreviewPacket::handle));
        registrar.playToClient(S2C_TerritoryPreviewPacket.TYPE, S2C_TerritoryPreviewPacket.STREAM_CODEC, S2C_TerritoryPreviewPacket::handle);
        registrar.playToClient(S2C_OpenTerritoryCoreScreenPacket.TYPE, S2C_OpenTerritoryCoreScreenPacket.STREAM_CODEC, S2C_OpenTerritoryCoreScreenPacket::handle);
        registrar.playToClient(S2C_TerritoryCoreHealthPacket.TYPE, S2C_TerritoryCoreHealthPacket.STREAM_CODEC, S2C_TerritoryCoreHealthPacket::handle);
        c2s.playToServer(C2S_SetTerritoryCoreRadiusPacket.TYPE, C2S_SetTerritoryCoreRadiusPacket.STREAM_CODEC, C2SPacketGate.action(C2S_SetTerritoryCoreRadiusPacket::handle));
        c2s.playToServer(C2S_ValidateTerritoryCorePacket.TYPE, C2S_ValidateTerritoryCorePacket.STREAM_CODEC, C2SPacketGate.heavy(C2S_ValidateTerritoryCorePacket::handle));
        registrar.playToClient(S2C_TerritoryClosurePacket.TYPE, S2C_TerritoryClosurePacket.STREAM_CODEC, S2C_TerritoryClosurePacket::handle);
        c2s.playToServer(C2S_RequestTerritoryMapPacket.TYPE, C2S_RequestTerritoryMapPacket.STREAM_CODEC, C2SPacketGate.request(C2S_RequestTerritoryMapPacket::handle));
        registrar.playToClient(S2C_TerritoryMapPacket.TYPE, S2C_TerritoryMapPacket.STREAM_CODEC, S2C_TerritoryMapPacket::handle);
        c2s.playToServer(C2S_RequestReinforcementScanPacket.TYPE, C2S_RequestReinforcementScanPacket.STREAM_CODEC, C2SPacketGate.request(C2S_RequestReinforcementScanPacket::handle));
        registrar.playToClient(S2C_ReinforcementSnapshotPacket.TYPE, S2C_ReinforcementSnapshotPacket.STREAM_CODEC, S2C_ReinforcementSnapshotPacket::handle);
        registrar.playToClient(S2C_ReinforcementDeltaPacket.TYPE, S2C_ReinforcementDeltaPacket.STREAM_CODEC, S2C_ReinforcementDeltaPacket::handle);
        c2s.playToServer(C2S_SetWeldingBrushPacket.TYPE, C2S_SetWeldingBrushPacket.STREAM_CODEC, C2SPacketGate.input(C2S_SetWeldingBrushPacket::handle));
        registrar.playToClient(S2C_AnnouncementPacket.TYPE, S2C_AnnouncementPacket.STREAM_CODEC, S2C_AnnouncementPacket::handle);
        registrar.playToClient(S2C_DetectorStatePacket.TYPE, S2C_DetectorStatePacket.STREAM_CODEC, S2C_DetectorStatePacket::handle);
        c2s.playToServer(C2S_SetDetectorNamePacket.TYPE, C2S_SetDetectorNamePacket.STREAM_CODEC, C2SPacketGate.action(C2S_SetDetectorNamePacket::handle));
        registrar.playToClient(S2C_OpenStatsScreenPacket.TYPE, S2C_OpenStatsScreenPacket.STREAM_CODEC, S2C_OpenStatsScreenPacket::handle);
        c2s.playToServer(C2S_ConfigurePaymentPacket.TYPE, C2S_ConfigurePaymentPacket.STREAM_CODEC, C2SPacketGate.action(C2S_ConfigurePaymentPacket::handle));
        registrar.playToClient(S2C_WarehouseZonesPacket.TYPE, S2C_WarehouseZonesPacket.STREAM_CODEC, S2C_WarehouseZonesPacket::handle);
        registrar.playToClient(S2C_JoinCinematicPacket.TYPE, S2C_JoinCinematicPacket.STREAM_CODEC, S2C_JoinCinematicPacket::handle);
        registrar.playToClient(S2C_LiveStreamersPacket.TYPE, S2C_LiveStreamersPacket.STREAM_CODEC, S2C_LiveStreamersPacket::handle);
        registrar.playToClient(S2C_OpenJobsScreenPacket.TYPE, S2C_OpenJobsScreenPacket.STREAM_CODEC, S2C_OpenJobsScreenPacket::handle);
        registrar.playToClient(S2C_JobsLeaderboardPacket.TYPE, S2C_JobsLeaderboardPacket.STREAM_CODEC, S2C_JobsLeaderboardPacket::handle);
        c2s.playToServer(C2S_JobsActionPacket.TYPE, C2S_JobsActionPacket.STREAM_CODEC, C2SPacketGate.action(C2S_JobsActionPacket::handle));
        registrar.playToClient(S2C_EventHudPacket.TYPE, S2C_EventHudPacket.STREAM_CODEC, S2C_EventHudPacket::handle);
        registrar.playToClient(S2C_EventScreenPacket.TYPE, S2C_EventScreenPacket.STREAM_CODEC, S2C_EventScreenPacket::handle);
        c2s.playToServer(C2S_EventScreenActionPacket.TYPE, C2S_EventScreenActionPacket.STREAM_CODEC, C2SPacketGate.action(C2S_EventScreenActionPacket::handle));
        registrar.playToClient(S2C_SyncOxygenPacket.TYPE, S2C_SyncOxygenPacket.STREAM_CODEC, S2C_SyncOxygenPacket::handle);
        registrar.playToClient(S2C_MekaSuitShieldPacket.TYPE, S2C_MekaSuitShieldPacket.STREAM_CODEC, S2C_MekaSuitShieldPacket::handle);
        registrar.playToClient(S2C_TutorialPacket.TYPE, S2C_TutorialPacket.STREAM_CODEC, S2C_TutorialPacket::handle);
    }
}
