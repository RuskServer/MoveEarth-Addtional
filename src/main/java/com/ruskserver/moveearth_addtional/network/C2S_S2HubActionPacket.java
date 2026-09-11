package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import com.ruskserver.moveearth_addtional.s2.S2NationViewService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public record C2S_S2HubActionPacket(int requestId, long expectedRevision, S2HubTab tab, Action action)
        implements CustomPacketPayload {
    public static final Type<C2S_S2HubActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "s2_hub_action"));
    public static final StreamCodec<FriendlyByteBuf, C2S_S2HubActionPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeVarInt(packet.requestId);
                buffer.writeLong(packet.expectedRevision);
                buffer.writeByte(packet.tab.networkId());
                buffer.writeByte(packet.action.networkId);
            },
            buffer -> new C2S_S2HubActionPacket(buffer.readVarInt(), buffer.readLong(),
                    S2HubTab.fromNetworkId(buffer.readUnsignedByte()),
                    Action.fromNetworkId(buffer.readUnsignedByte())));

    public C2S_S2HubActionPacket {
        if (tab == null) tab = S2HubTab.OVERVIEW;
        if (action == null) action = Action.REFRESH;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (action == Action.REFRESH) {
                var snapshot = S2NationViewService.INSTANCE.snapshotFor(player);
                PacketDistributor.sendToPlayer(player, new S2C_S2HubSnapshotPacket(tab, snapshot));
                PacketDistributor.sendToPlayer(player, new S2C_S2ActionResultPacket(
                        requestId, true, snapshot.revision(), "screen.moveearth_addtional.s2.refreshed"));
            } else {
                PacketDistributor.sendToPlayer(player, new S2C_S2ActionResultPacket(
                        requestId, false, S2NationViewService.INSTANCE.snapshotFor(player).revision(),
                        "screen.moveearth_addtional.s2.unsupported_action"));
            }
        });
    }

    public enum Action {
        REFRESH(0),
        UNKNOWN(255);

        private final int networkId;

        Action(int networkId) {
            this.networkId = networkId;
        }

        static Action fromNetworkId(int id) {
            return id == REFRESH.networkId ? REFRESH : UNKNOWN;
        }
    }
}
