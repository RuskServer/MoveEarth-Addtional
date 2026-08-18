package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record S2C_OpenDetectorScreenPacket(String ownerName, List<String> whitelist, List<String> onlinePlayers) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2C_OpenDetectorScreenPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "open_detector_screen"));

    public static final StreamCodec<FriendlyByteBuf, S2C_OpenDetectorScreenPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeUtf(packet.ownerName());
                buf.writeCollection(packet.whitelist(), FriendlyByteBuf::writeUtf);
                buf.writeCollection(packet.onlinePlayers(), FriendlyByteBuf::writeUtf);
            },
            buf -> new S2C_OpenDetectorScreenPacket(
                    buf.readUtf(),
                    buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf),
                    buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf)
            )
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleOpenDetectorScreen(this);
        });
    }
}
