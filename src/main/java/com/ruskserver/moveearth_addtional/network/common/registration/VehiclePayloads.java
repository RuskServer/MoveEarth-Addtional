package com.ruskserver.moveearth_addtional.network.common.registration;

import com.ruskserver.moveearth_addtional.network.s2c.vehicle.S2C_OpenVehicleCoreScreenPacket;
import com.ruskserver.moveearth_addtional.network.s2c.vehicle.S2C_WeldingTargetPacket;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers vehicle payloads without changing their wire identifiers or codecs. */
public final class VehiclePayloads {
    private VehiclePayloads() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(S2C_OpenVehicleCoreScreenPacket.TYPE, S2C_OpenVehicleCoreScreenPacket.STREAM_CODEC, S2C_OpenVehicleCoreScreenPacket::handle);
        registrar.playToClient(S2C_WeldingTargetPacket.TYPE, S2C_WeldingTargetPacket.STREAM_CODEC, S2C_WeldingTargetPacket::handle);
    }
}
