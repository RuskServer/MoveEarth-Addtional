package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import com.ruskserver.moveearth_addtional.s2.S2NationViewService;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public record C2S_NationMembershipPacket(int requestId, long expectedRevision,
                                         Action action, UUID targetId)
        implements CustomPacketPayload {
    private static final UUID NO_TARGET = new UUID(0L, 0L);
    public static final Type<C2S_NationMembershipPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "nation_membership"));
    public static final StreamCodec<FriendlyByteBuf, C2S_NationMembershipPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeVarInt(packet.requestId);
                buffer.writeLong(packet.expectedRevision);
                buffer.writeByte(packet.action.networkId);
                buffer.writeUUID(packet.targetId == null ? NO_TARGET : packet.targetId);
            },
            buffer -> new C2S_NationMembershipPacket(buffer.readVarInt(), buffer.readLong(),
                    Action.fromNetworkId(buffer.readUnsignedByte()), buffer.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            NationSavedData data = NationSavedData.get(player.server);
            NationSavedData.MembershipResult result = new NationSavedData.MembershipResult(
                    NationSavedData.MembershipStatus.NO_PERMISSION, data.revision());
            ServerPlayer affectedPlayer = null;
            switch (action) {
                case INVITE -> {
                    affectedPlayer = player.server.getPlayerList().getPlayer(targetId);
                    result = affectedPlayer == null
                            ? new NationSavedData.MembershipResult(
                            NationSavedData.MembershipStatus.TARGET_OFFLINE, data.revision())
                            : data.invite(player.getUUID(), targetId,
                            affectedPlayer.getGameProfile().getName(), expectedRevision);
                }
                case ACCEPT -> result = data.accept(player.getUUID(), targetId,
                        player.getGameProfile().getName(), expectedRevision);
                case DECLINE -> result = data.decline(player.getUUID(), targetId, expectedRevision);
                case LEAVE -> result = data.leave(player.getUUID(), expectedRevision);
                case KICK -> {
                    affectedPlayer = player.server.getPlayerList().getPlayer(targetId);
                    result = data.kick(player.getUUID(), targetId, expectedRevision);
                }
                case UNKNOWN -> result = new NationSavedData.MembershipResult(
                        NationSavedData.MembershipStatus.NO_PERMISSION, data.revision());
            }
            String messageKey = "screen.moveearth_addtional.nation.membership."
                    + result.status().name().toLowerCase(java.util.Locale.ROOT);
            if (result.success()) {
                S2HubTab tab = action == Action.INVITE || action == Action.KICK
                        ? S2HubTab.MEMBERS : S2HubTab.OVERVIEW;
                S2NationViewService.INSTANCE.sendHub(player, tab);
                if (affectedPlayer != null) {
                    String affectedMessageKey = action == Action.INVITE
                            ? "screen.moveearth_addtional.nation.membership.invitation_received"
                            : messageKey;
                    affectedPlayer.sendSystemMessage(MoveEarthMessage.info(
                            net.minecraft.network.chat.Component.translatable(affectedMessageKey)));
                }
            }
            PacketDistributor.sendToPlayer(player, new S2C_S2ActionResultPacket(
                    requestId, result.success(), result.revision(), messageKey));
        });
    }

    public enum Action {
        INVITE(0), ACCEPT(1), DECLINE(2), LEAVE(3), KICK(4), UNKNOWN(255);
        private final int networkId;
        Action(int networkId) { this.networkId = networkId; }
        private static Action fromNetworkId(int id) {
            for (Action action : values()) if (action.networkId == id) return action;
            return UNKNOWN;
        }
    }
}
