package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import com.ruskserver.moveearth_addtional.s2.S2NationSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2C_S2HubSnapshotPacket(S2HubTab tab, S2NationSnapshot snapshot)
        implements CustomPacketPayload {
    public static final Type<S2C_S2HubSnapshotPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "s2_hub_snapshot"));
    public static final StreamCodec<FriendlyByteBuf, S2C_S2HubSnapshotPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeByte(packet.tab.networkId());
                S2PacketCodec.writeSnapshot(buffer, packet.snapshot);
            },
            buffer -> new S2C_S2HubSnapshotPacket(
                    S2HubTab.fromNetworkId(buffer.readUnsignedByte()), S2PacketCodec.readSnapshot(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() ->
                com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleS2HubSnapshot(this));
    }
}
