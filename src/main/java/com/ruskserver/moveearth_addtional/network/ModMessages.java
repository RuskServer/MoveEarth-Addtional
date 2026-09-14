package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModMessages {
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("3.0-detector-admin1-oxygen1-s2ui31");

        registrar.playToServer(C2S_RequestS2HubPacket.TYPE, C2S_RequestS2HubPacket.STREAM_CODEC, C2S_RequestS2HubPacket::handle);
        registrar.playToServer(C2S_S2HubActionPacket.TYPE, C2S_S2HubActionPacket.STREAM_CODEC, C2S_S2HubActionPacket::handle);
        registrar.playToClient(S2C_S2HubSnapshotPacket.TYPE, S2C_S2HubSnapshotPacket.STREAM_CODEC, S2C_S2HubSnapshotPacket::handle);
        registrar.playToClient(S2C_NationNameplatesPacket.TYPE, S2C_NationNameplatesPacket.STREAM_CODEC, S2C_NationNameplatesPacket::handle);
        registrar.playToClient(S2C_NationTreasuryPacket.TYPE, S2C_NationTreasuryPacket.STREAM_CODEC, S2C_NationTreasuryPacket::handle);
        registrar.playToServer(C2S_NationTreasuryPacket.TYPE, C2S_NationTreasuryPacket.STREAM_CODEC, C2S_NationTreasuryPacket::handle);
        registrar.playToClient(S2C_S2ActionResultPacket.TYPE, S2C_S2ActionResultPacket.STREAM_CODEC, S2C_S2ActionResultPacket::handle);
        registrar.playToServer(C2S_RequestTerritoryPreviewPacket.TYPE, C2S_RequestTerritoryPreviewPacket.STREAM_CODEC, C2S_RequestTerritoryPreviewPacket::handle);
        registrar.playToClient(S2C_TerritoryPreviewPacket.TYPE, S2C_TerritoryPreviewPacket.STREAM_CODEC, S2C_TerritoryPreviewPacket::handle);
        registrar.playToClient(S2C_OpenTerritoryCoreScreenPacket.TYPE, S2C_OpenTerritoryCoreScreenPacket.STREAM_CODEC, S2C_OpenTerritoryCoreScreenPacket::handle);
        registrar.playToClient(S2C_TerritoryCoreHealthPacket.TYPE, S2C_TerritoryCoreHealthPacket.STREAM_CODEC, S2C_TerritoryCoreHealthPacket::handle);
        registrar.playToServer(C2S_SetTerritoryCoreRadiusPacket.TYPE, C2S_SetTerritoryCoreRadiusPacket.STREAM_CODEC, C2S_SetTerritoryCoreRadiusPacket::handle);
        registrar.playToServer(C2S_ValidateTerritoryCorePacket.TYPE, C2S_ValidateTerritoryCorePacket.STREAM_CODEC, C2S_ValidateTerritoryCorePacket::handle);
        registrar.playToClient(S2C_TerritoryClosurePacket.TYPE, S2C_TerritoryClosurePacket.STREAM_CODEC, S2C_TerritoryClosurePacket::handle);
        registrar.playToServer(C2S_RequestTerritoryMapPacket.TYPE, C2S_RequestTerritoryMapPacket.STREAM_CODEC, C2S_RequestTerritoryMapPacket::handle);
        registrar.playToClient(S2C_TerritoryMapPacket.TYPE, S2C_TerritoryMapPacket.STREAM_CODEC, S2C_TerritoryMapPacket::handle);
        registrar.playToServer(C2S_RequestReinforcementScanPacket.TYPE, C2S_RequestReinforcementScanPacket.STREAM_CODEC, C2S_RequestReinforcementScanPacket::handle);
        registrar.playToClient(S2C_ReinforcementSnapshotPacket.TYPE, S2C_ReinforcementSnapshotPacket.STREAM_CODEC, S2C_ReinforcementSnapshotPacket::handle);
        registrar.playToClient(S2C_ReinforcementDeltaPacket.TYPE, S2C_ReinforcementDeltaPacket.STREAM_CODEC, S2C_ReinforcementDeltaPacket::handle);
        registrar.playToServer(C2S_SetWeldingBrushPacket.TYPE, C2S_SetWeldingBrushPacket.STREAM_CODEC, C2S_SetWeldingBrushPacket::handle);
        registrar.playToServer(C2S_CreateNationPacket.TYPE, C2S_CreateNationPacket.STREAM_CODEC, C2S_CreateNationPacket::handle);
        registrar.playToServer(C2S_NationMembershipPacket.TYPE, C2S_NationMembershipPacket.STREAM_CODEC, C2S_NationMembershipPacket::handle);
        registrar.playToServer(C2S_NationRolePacket.TYPE, C2S_NationRolePacket.STREAM_CODEC, C2S_NationRolePacket::handle);
        registrar.playToServer(C2S_NationDiplomacyPacket.TYPE, C2S_NationDiplomacyPacket.STREAM_CODEC, C2S_NationDiplomacyPacket::handle);
        registrar.playToServer(C2S_SiegeActionPacket.TYPE, C2S_SiegeActionPacket.STREAM_CODEC, C2S_SiegeActionPacket::handle);
        registrar.playToServer(C2S_NationSettingsPacket.TYPE, C2S_NationSettingsPacket.STREAM_CODEC, C2S_NationSettingsPacket::handle);
        registrar.playToServer(C2S_RequestNationNotificationsPacket.TYPE, C2S_RequestNationNotificationsPacket.STREAM_CODEC, C2S_RequestNationNotificationsPacket::handle);
        registrar.playToServer(C2S_UpdateNationNotificationsPacket.TYPE, C2S_UpdateNationNotificationsPacket.STREAM_CODEC, C2S_UpdateNationNotificationsPacket::handle);
        registrar.playToServer(C2S_LinkNationDiscordPacket.TYPE, C2S_LinkNationDiscordPacket.STREAM_CODEC, C2S_LinkNationDiscordPacket::handle);
        registrar.playToServer(C2S_LinkDiscordAccountPacket.TYPE, C2S_LinkDiscordAccountPacket.STREAM_CODEC, C2S_LinkDiscordAccountPacket::handle);
        registrar.playToClient(S2C_OpenNationNotificationsPacket.TYPE, S2C_OpenNationNotificationsPacket.STREAM_CODEC, S2C_OpenNationNotificationsPacket::handle);
        registrar.playToClient(S2C_OnboardingPacket.TYPE, S2C_OnboardingPacket.STREAM_CODEC, S2C_OnboardingPacket::handle);
        registrar.playToClient(S2C_CloseOnboardingPacket.TYPE, S2C_CloseOnboardingPacket.STREAM_CODEC, S2C_CloseOnboardingPacket::handle);
        registrar.playToServer(C2S_OnboardingActionPacket.TYPE, C2S_OnboardingActionPacket.STREAM_CODEC, C2S_OnboardingActionPacket::handle);
        registrar.playToClient(S2C_NationApplicationsPacket.TYPE, S2C_NationApplicationsPacket.STREAM_CODEC, S2C_NationApplicationsPacket::handle);
        registrar.playToServer(C2S_NationApplicationActionPacket.TYPE, C2S_NationApplicationActionPacket.STREAM_CODEC, C2S_NationApplicationActionPacket::handle);

        registrar.playToClient(S2C_AnnouncementPacket.TYPE, S2C_AnnouncementPacket.STREAM_CODEC, S2C_AnnouncementPacket::handle);
        registrar.playToClient(S2C_OpenDetectorScreenPacket.TYPE, S2C_OpenDetectorScreenPacket.STREAM_CODEC, S2C_OpenDetectorScreenPacket::handle);
        registrar.playToServer(C2S_SetDetectorNamePacket.TYPE, C2S_SetDetectorNamePacket.STREAM_CODEC, C2S_SetDetectorNamePacket::handle);
        registrar.playToClient(S2C_SyncDetectorNamePacket.TYPE, S2C_SyncDetectorNamePacket.STREAM_CODEC, S2C_SyncDetectorNamePacket::handle);
        registrar.playToClient(S2C_OpenStatsScreenPacket.TYPE, S2C_OpenStatsScreenPacket.STREAM_CODEC, S2C_OpenStatsScreenPacket::handle);
        registrar.playToServer(C2S_UpdateWhitelistPacket.TYPE, C2S_UpdateWhitelistPacket.STREAM_CODEC, C2S_UpdateWhitelistPacket::handle);
        registrar.playToClient(S2C_SyncWhitelistPacket.TYPE, S2C_SyncWhitelistPacket.STREAM_CODEC, S2C_SyncWhitelistPacket::handle);
        registrar.playToServer(C2S_UpdateDetectorManagerPacket.TYPE, C2S_UpdateDetectorManagerPacket.STREAM_CODEC, C2S_UpdateDetectorManagerPacket::handle);
        registrar.playToClient(S2C_SyncDetectorManagersPacket.TYPE, S2C_SyncDetectorManagersPacket.STREAM_CODEC, S2C_SyncDetectorManagersPacket::handle);

        registrar.playToClient(S2C_SyncDetectorPaymentPacket.TYPE, S2C_SyncDetectorPaymentPacket.STREAM_CODEC, S2C_SyncDetectorPaymentPacket::handle);
        registrar.playToServer(C2S_ConfigurePaymentPacket.TYPE, C2S_ConfigurePaymentPacket.STREAM_CODEC, C2S_ConfigurePaymentPacket::handle);

        registrar.playToClient(S2C_OpenPvpScreenPacket.TYPE, S2C_OpenPvpScreenPacket.STREAM_CODEC, S2C_OpenPvpScreenPacket::handle);
        registrar.playToClient(S2C_PvpEntryStatePacket.TYPE, S2C_PvpEntryStatePacket.STREAM_CODEC, S2C_PvpEntryStatePacket::handle);
        registrar.playToServer(C2S_PvpActionPacket.TYPE, C2S_PvpActionPacket.STREAM_CODEC, C2S_PvpActionPacket::handle);
        registrar.playToServer(C2S_ExchangeWeaponCratePacket.TYPE, C2S_ExchangeWeaponCratePacket.STREAM_CODEC, C2S_ExchangeWeaponCratePacket::handle);
        registrar.playToClient(S2C_PvpHudPacket.TYPE, S2C_PvpHudPacket.STREAM_CODEC, S2C_PvpHudPacket::handle);
        registrar.playToClient(S2C_PvpZonePacket.TYPE, S2C_PvpZonePacket.STREAM_CODEC, S2C_PvpZonePacket::handle);
        registrar.playToClient(S2C_PvpTeamPacket.TYPE, S2C_PvpTeamPacket.STREAM_CODEC, S2C_PvpTeamPacket::handle);
        registrar.playToClient(S2C_PvpKillcamPacket.TYPE, S2C_PvpKillcamPacket.STREAM_CODEC, S2C_PvpKillcamPacket::handle);
        registrar.playToClient(S2C_PvpResultPacket.TYPE, S2C_PvpResultPacket.STREAM_CODEC, S2C_PvpResultPacket::handle);
        registrar.playToClient(S2C_OpenPvpTasksPacket.TYPE, S2C_OpenPvpTasksPacket.STREAM_CODEC, S2C_OpenPvpTasksPacket::handle);
        registrar.playToServer(C2S_RequestPvpTasksPacket.TYPE, C2S_RequestPvpTasksPacket.STREAM_CODEC, C2S_RequestPvpTasksPacket::handle);
        registrar.playToServer(C2S_ClaimPvpTaskPacket.TYPE, C2S_ClaimPvpTaskPacket.STREAM_CODEC, C2S_ClaimPvpTaskPacket::handle);
        registrar.playToClient(S2C_OpenJobsScreenPacket.TYPE, S2C_OpenJobsScreenPacket.STREAM_CODEC, S2C_OpenJobsScreenPacket::handle);
        registrar.playToClient(S2C_JobsLeaderboardPacket.TYPE, S2C_JobsLeaderboardPacket.STREAM_CODEC, S2C_JobsLeaderboardPacket::handle);
        registrar.playToClient(S2C_JobShopPacket.TYPE, S2C_JobShopPacket.STREAM_CODEC, S2C_JobShopPacket::handle);
        registrar.playToServer(C2S_JobsActionPacket.TYPE, C2S_JobsActionPacket.STREAM_CODEC, C2S_JobsActionPacket::handle);
        registrar.playToServer(C2S_JobShopActionPacket.TYPE, C2S_JobShopActionPacket.STREAM_CODEC, C2S_JobShopActionPacket::handle);
        registrar.playToClient(S2C_SyncLoadoutsPacket.TYPE, S2C_SyncLoadoutsPacket.STREAM_CODEC, S2C_SyncLoadoutsPacket::handle);
        registrar.playToClient(S2C_OpenLoadoutEditorPacket.TYPE, S2C_OpenLoadoutEditorPacket.STREAM_CODEC, S2C_OpenLoadoutEditorPacket::handle);
        registrar.playToServer(C2S_SaveLoadoutPacket.TYPE, C2S_SaveLoadoutPacket.STREAM_CODEC, C2S_SaveLoadoutPacket::handle);
        registrar.playToServer(C2S_DeleteLoadoutPacket.TYPE, C2S_DeleteLoadoutPacket.STREAM_CODEC, C2S_DeleteLoadoutPacket::handle);
        registrar.playToServer(C2S_ReorderLoadoutsPacket.TYPE, C2S_ReorderLoadoutsPacket.STREAM_CODEC, C2S_ReorderLoadoutsPacket::handle);
        registrar.playToClient(S2C_StartMapVotePacket.TYPE, S2C_StartMapVotePacket.STREAM_CODEC, S2C_StartMapVotePacket::handle);
        registrar.playToClient(S2C_UpdateMapVotePacket.TYPE, S2C_UpdateMapVotePacket.STREAM_CODEC, S2C_UpdateMapVotePacket::handle);
        registrar.playToServer(C2S_VoteMapPacket.TYPE, C2S_VoteMapPacket.STREAM_CODEC, C2S_VoteMapPacket::handle);
        registrar.playToClient(S2C_KillcamReplayPacket.TYPE, S2C_KillcamReplayPacket.STREAM_CODEC, S2C_KillcamReplayPacket::handle);
        registrar.playToClient(S2C_SyncOxygenPacket.TYPE, S2C_SyncOxygenPacket.STREAM_CODEC, S2C_SyncOxygenPacket::handle);
    }
}
