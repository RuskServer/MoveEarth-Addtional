package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record C2S_RequestReinforcementScanPacket(int radius) implements CustomPacketPayload {
    public static final Type<C2S_RequestReinforcementScanPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "reinforcement_scan_request"));
    public static final StreamCodec<FriendlyByteBuf, C2S_RequestReinforcementScanPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> buffer.writeByte(packet.radius),
            buffer -> new C2S_RequestReinforcementScanPacket(buffer.readUnsignedByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ReinforcementService.sendScan(player, radius);
            }
        });
    }
}
