package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.nation.NationOnboardingService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public record C2S_OnboardingActionPacket(long expectedRevision, Action action, UUID nationId)
        implements CustomPacketPayload {
    private static final UUID NONE = new UUID(0L, 0L);
    public static final Type<C2S_OnboardingActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "onboarding_action"));
    public static final StreamCodec<FriendlyByteBuf, C2S_OnboardingActionPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeLong(packet.expectedRevision);
                buffer.writeByte(packet.action.networkId);
                buffer.writeUUID(packet.nationId == null ? NONE : packet.nationId);
            }, buffer -> new C2S_OnboardingActionPacket(buffer.readLong(),
                    Action.fromNetworkId(buffer.readUnsignedByte()), buffer.readUUID()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                NationOnboardingService.handlePlayerAction(player, expectedRevision, action,
                        NONE.equals(nationId) ? null : nationId);
            }
        });
    }

    public enum Action {
        APPLY(0), CANCEL(1), WILDERNESS(2), REFRESH(3), UNKNOWN(255);
        private final int networkId;
        Action(int networkId) { this.networkId = networkId; }
        private static Action fromNetworkId(int value) {
            for (Action action : values()) if (action.networkId == value) return action;
            return UNKNOWN;
        }
    }
}
