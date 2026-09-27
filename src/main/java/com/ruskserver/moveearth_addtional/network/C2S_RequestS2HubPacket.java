package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import com.ruskserver.moveearth_addtional.s2.S2NationViewService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record C2S_RequestS2HubPacket(S2HubTab tab) implements CustomPacketPayload {
    public static final Type<C2S_RequestS2HubPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "s2_hub_request"));
    public static final StreamCodec<FriendlyByteBuf, C2S_RequestS2HubPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> buffer.writeByte(packet.tab.networkId()),
            buffer -> new C2S_RequestS2HubPacket(S2HubTab.fromNetworkId(buffer.readUnsignedByte())));

    public C2S_RequestS2HubPacket {
        if (tab == null) tab = S2HubTab.OVERVIEW;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                S2NationViewService.INSTANCE.sendHub(player, tab);
            }
        });
    }
}
