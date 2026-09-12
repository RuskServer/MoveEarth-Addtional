package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.client.ClientPacketHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2C_OpenNationNotificationsPacket(boolean canManage, boolean linked, boolean inGame,
                                                 boolean discord, boolean includeCoordinates,
                                                 boolean mentionOnSiege, int pendingCount, long revision)
        implements CustomPacketPayload {
    public static final Type<S2C_OpenNationNotificationsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "open_nation_notifications"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2C_OpenNationNotificationsPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> {
                buffer.writeBoolean(packet.canManage);
                buffer.writeBoolean(packet.linked);
                buffer.writeBoolean(packet.inGame);
                buffer.writeBoolean(packet.discord);
                buffer.writeBoolean(packet.includeCoordinates);
                buffer.writeBoolean(packet.mentionOnSiege);
                buffer.writeVarInt(packet.pendingCount);
                buffer.writeLong(packet.revision);
            }, buffer -> new S2C_OpenNationNotificationsPacket(buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readVarInt(), buffer.readLong()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandler.handleOpenNationNotifications(this));
    }
}
