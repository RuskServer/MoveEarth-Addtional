package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import com.ruskserver.moveearth_addtional.s2.S2NationViewService;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeActionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;
import java.util.UUID;

public record C2S_SiegeActionPacket(int requestId, long expectedRevision, Action action,
                                    UUID targetId, long goldCompensation)
        implements CustomPacketPayload {
    private static final UUID NO_TARGET = new UUID(0L, 0L);
    public static final Type<C2S_SiegeActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "siege_action"));
    public static final StreamCodec<FriendlyByteBuf, C2S_SiegeActionPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeVarInt(packet.requestId);
                buffer.writeLong(packet.expectedRevision);
                buffer.writeByte(packet.action.networkId);
                buffer.writeUUID(packet.targetId == null ? NO_TARGET : packet.targetId);
                buffer.writeVarLong(Math.max(0L, packet.goldCompensation));
            },
            buffer -> new C2S_SiegeActionPacket(buffer.readVarInt(), buffer.readLong(),
                    Action.fromNetworkId(buffer.readUnsignedByte()), buffer.readUUID(), buffer.readVarLong()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            SiegeActionService.Result result = switch (action) {
                case SET_VAULT -> SiegeActionService.setVault(player, expectedRevision);
                case PROPOSE_PEACE -> SiegeActionService.proposePeace(
                        player, targetId, goldCompensation, expectedRevision);
                case ACCEPT_PEACE -> SiegeActionService.respondPeace(
                        player, targetId, true, expectedRevision);
                case REJECT_PEACE -> SiegeActionService.respondPeace(
                        player, targetId, false, expectedRevision);
                case CANCEL_PEACE -> SiegeActionService.cancelPeace(player, targetId, expectedRevision);
                case SURRENDER -> SiegeActionService.surrender(player, targetId, expectedRevision);
                case UNKNOWN -> SiegeActionService.Result.INVALID;
            };
            long revision = NationSavedData.get(player.server).revision();
            if (result.success()) S2NationViewService.INSTANCE.sendHub(player,
                    action == Action.SET_VAULT ? S2HubTab.OVERVIEW : S2HubTab.SIEGE);
            PacketDistributor.sendToPlayer(player, new S2C_S2ActionResultPacket(
                    requestId, result.success(), revision,
                    "screen.moveearth_addtional.siege.action."
                            + result.name().toLowerCase(Locale.ROOT)));
        });
    }

    public enum Action {
        SET_VAULT(0), PROPOSE_PEACE(1), ACCEPT_PEACE(2), REJECT_PEACE(3),
        CANCEL_PEACE(4), SURRENDER(5), UNKNOWN(255);
        private final int networkId;
        Action(int networkId) { this.networkId = networkId; }
        private static Action fromNetworkId(int id) {
            for (Action action : values()) if (action.networkId == id) return action;
            return UNKNOWN;
        }
    }
}
