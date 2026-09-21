package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.region.RegionViewService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Asks the server what this player may know about the regions around them. */
public record C2S_RequestRegionViewPacket() implements CustomPacketPayload {

    public static final Type<C2S_RequestRegionViewPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "region_view_request"));

    public static final StreamCodec<FriendlyByteBuf, C2S_RequestRegionViewPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> { }, buffer -> new C2S_RequestRegionViewPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                RegionViewService.send(player);
            }
        });
    }
}
