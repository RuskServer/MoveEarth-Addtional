package com.ruskserver.moveearth_addtional.network.c2s.nation;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public record C2S_RequestNationNotificationsPacket() implements CustomPacketPayload {
    public static final Type<C2S_RequestNationNotificationsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "request_nation_notifications"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2S_RequestNationNotificationsPacket> STREAM_CODEC =
            StreamCodec.unit(new C2S_RequestNationNotificationsPacket());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            PacketDistributor.sendToPlayer(player, NationNotificationSnapshotFactory.create(player));
        });
    }
}
