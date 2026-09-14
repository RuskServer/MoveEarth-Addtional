package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.nation.NationOnboardingService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public record C2S_NationApplicationActionPacket(long expectedRevision, Action action, UUID applicantId)
        implements CustomPacketPayload {
    private static final UUID NONE = new UUID(0L, 0L);
    public static final Type<C2S_NationApplicationActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "nation_application_action"));
    public static final StreamCodec<FriendlyByteBuf, C2S_NationApplicationActionPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeLong(packet.expectedRevision);
                buffer.writeByte(packet.action.networkId);
                buffer.writeUUID(packet.applicantId == null ? NONE : packet.applicantId);
            }, buffer -> new C2S_NationApplicationActionPacket(buffer.readLong(),
                    Action.fromNetworkId(buffer.readUnsignedByte()), buffer.readUUID()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                NationOnboardingService.handleManagerAction(player, expectedRevision, action,
                        NONE.equals(applicantId) ? null : applicantId);
            }
        });
    }

    public enum Action {
        OPEN(0), APPROVE(1), REJECT(2), UNKNOWN(255);
        private final int networkId;
        Action(int networkId) { this.networkId = networkId; }
        private static Action fromNetworkId(int value) {
            for (Action action : values()) if (action.networkId == value) return action;
            return UNKNOWN;
        }
    }
}
