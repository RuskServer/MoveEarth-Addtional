package com.ruskserver.moveearth_addtional.network.common.registration;

import com.ruskserver.moveearth_addtional.network.c2s.market.C2S_BalanceActionPacket;
import com.ruskserver.moveearth_addtional.network.c2s.market.C2S_MarketActionPacket;
import com.ruskserver.moveearth_addtional.network.s2c.market.S2C_BalanceSnapshotPacket;
import com.ruskserver.moveearth_addtional.network.s2c.market.S2C_EconomyHudPacket;
import com.ruskserver.moveearth_addtional.network.s2c.market.S2C_MarketSnapshotPacket;
import com.ruskserver.moveearth_addtional.network.s2c.market.S2C_WaypointPacket;
import com.ruskserver.moveearth_addtional.network.common.C2SPacketGate;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers market payloads without changing their wire identifiers or codecs. */
public final class MarketPayloads {
    private MarketPayloads() {}

    public static void register(PayloadRegistrar registrar) {
        // C2S handlers run on the network thread only for the rate-limit check; see C2SPacketGate.
        PayloadRegistrar c2s = registrar.executesOn(HandlerThread.NETWORK);
        c2s.playToServer(C2S_MarketActionPacket.TYPE, C2S_MarketActionPacket.STREAM_CODEC, C2SPacketGate.action(C2S_MarketActionPacket::handle));
        registrar.playToClient(S2C_MarketSnapshotPacket.TYPE, S2C_MarketSnapshotPacket.STREAM_CODEC, S2C_MarketSnapshotPacket::handle);
        registrar.playToClient(S2C_EconomyHudPacket.TYPE, S2C_EconomyHudPacket.STREAM_CODEC, S2C_EconomyHudPacket::handle);
        registrar.playToClient(S2C_WaypointPacket.TYPE, S2C_WaypointPacket.STREAM_CODEC, S2C_WaypointPacket::handle);
        c2s.playToServer(C2S_BalanceActionPacket.TYPE, C2S_BalanceActionPacket.STREAM_CODEC, C2SPacketGate.action(C2S_BalanceActionPacket::handle));
        registrar.playToClient(S2C_BalanceSnapshotPacket.TYPE, S2C_BalanceSnapshotPacket.STREAM_CODEC, S2C_BalanceSnapshotPacket::handle);
    }
}
