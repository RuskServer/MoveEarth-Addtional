package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2C_AnnouncementPacket(String message) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<S2C_AnnouncementPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "announcement"));

    public static final StreamCodec<FriendlyByteBuf, S2C_AnnouncementPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.write(buf),
            S2C_AnnouncementPacket::new
    );

    public S2C_AnnouncementPacket(FriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.message);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleAnnouncement(this);
        });
    }
}
