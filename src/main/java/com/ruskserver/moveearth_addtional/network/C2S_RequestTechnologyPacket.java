package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.technology.TechnologyViewService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record C2S_RequestTechnologyPacket() implements CustomPacketPayload {
    public static final Type<C2S_RequestTechnologyPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "technology_request"));
    public static final StreamCodec<FriendlyByteBuf, C2S_RequestTechnologyPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> { }, buffer -> new C2S_RequestTechnologyPacket());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                com.ruskserver.moveearth_addtional.s2.technology.NationTechnologySavedData.get(player.server)
                        .recordAction(player, "technology_screen_opened");
                TechnologyViewService.send(player);
            }
        });
    }
}
