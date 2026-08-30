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
        final PayloadRegistrar registrar = event.registrar("2.0-jobs1-hardpoint1");

        registrar.playToClient(
                S2C_AnnouncementPacket.TYPE,
                S2C_AnnouncementPacket.STREAM_CODEC,
                S2C_AnnouncementPacket::handle
        );

        registrar.playToClient(
                S2C_OpenDetectorScreenPacket.TYPE,
                S2C_OpenDetectorScreenPacket.STREAM_CODEC,
                S2C_OpenDetectorScreenPacket::handle
        );

        registrar.playToClient(
                S2C_OpenStatsScreenPacket.TYPE,
                S2C_OpenStatsScreenPacket.STREAM_CODEC,
                S2C_OpenStatsScreenPacket::handle
        );

        registrar.playToServer(
                C2S_UpdateWhitelistPacket.TYPE,
                C2S_UpdateWhitelistPacket.STREAM_CODEC,
                C2S_UpdateWhitelistPacket::handle
        );

        registrar.playToClient(
                S2C_SyncWhitelistPacket.TYPE,
                S2C_SyncWhitelistPacket.STREAM_CODEC,
                S2C_SyncWhitelistPacket::handle
        );

        // 新規追加：決済関連パケット
        registrar.playToClient(
                S2C_SyncDetectorPaymentPacket.TYPE,
                S2C_SyncDetectorPaymentPacket.STREAM_CODEC,
                S2C_SyncDetectorPaymentPacket::handle
        );

        registrar.playToServer(
                C2S_ConfigurePaymentPacket.TYPE,
                C2S_ConfigurePaymentPacket.STREAM_CODEC,
                C2S_ConfigurePaymentPacket::handle
        );

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
    }
}
