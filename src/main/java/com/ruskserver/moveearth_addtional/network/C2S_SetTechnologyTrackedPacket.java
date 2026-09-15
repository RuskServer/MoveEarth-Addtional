package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.technology.NationTechnologySavedData;
import com.ruskserver.moveearth_addtional.s2.technology.TechnologyViewService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record C2S_SetTechnologyTrackedPacket(ResourceLocation technologyId, boolean tracked)
        implements CustomPacketPayload {
    public static final Type<C2S_SetTechnologyTrackedPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "technology_tracked"));
    public static final StreamCodec<FriendlyByteBuf, C2S_SetTechnologyTrackedPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> { buffer.writeResourceLocation(packet.technologyId); buffer.writeBoolean(packet.tracked); },
            buffer -> new C2S_SetTechnologyTrackedPacket(buffer.readResourceLocation(), buffer.readBoolean()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            NationTechnologySavedData.TrackResult result = NationTechnologySavedData.get(player.server)
                    .setTracked(player, technologyId, tracked);
            if (result == NationTechnologySavedData.TrackResult.CHANGED) {
                TechnologyViewService.syncNation(player, technologyId);
            }
        });
    }
}
