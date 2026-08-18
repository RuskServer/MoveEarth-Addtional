package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record S2C_SyncWhitelistPacket(List<String> whitelist, List<String> onlinePlayers) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2C_SyncWhitelistPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "sync_whitelist"));

    public static final StreamCodec<FriendlyByteBuf, S2C_SyncWhitelistPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeCollection(packet.whitelist(), FriendlyByteBuf::writeUtf);
                buf.writeCollection(packet.onlinePlayers(), FriendlyByteBuf::writeUtf);
            },
            buf -> new S2C_SyncWhitelistPacket(
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
            com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleSyncWhitelist(this);
        });
    }
}
