package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import com.ruskserver.moveearth_addtional.s2.S2NationViewService;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;
import java.util.UUID;

public record C2S_NationDiplomacyPacket(int requestId, long expectedRevision,
                                        Action action, UUID targetNationId)
        implements CustomPacketPayload {
    public static final Type<C2S_NationDiplomacyPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "nation_diplomacy"));
    public static final StreamCodec<FriendlyByteBuf, C2S_NationDiplomacyPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeVarInt(packet.requestId);
                buffer.writeLong(packet.expectedRevision);
                buffer.writeByte(packet.action.networkId);
                buffer.writeUUID(packet.targetNationId);
            },
            buffer -> new C2S_NationDiplomacyPacket(buffer.readVarInt(), buffer.readLong(),
                    Action.fromNetworkId(buffer.readUnsignedByte()), buffer.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            NationSavedData data = NationSavedData.get(player.server);
            NationSavedData.DiplomacyResult result = data.changeDiplomacy(
                    player.getUUID(), targetNationId, action.savedAction, expectedRevision);
            if (result.success()) S2NationViewService.INSTANCE.sendHub(player, S2HubTab.DIPLOMACY);
            PacketDistributor.sendToPlayer(player, new S2C_S2ActionResultPacket(
                    requestId, result.success(), result.revision(),
                    "screen.moveearth_addtional.diplomacy.result."
                            + result.status().name().toLowerCase(Locale.ROOT)));
        });
    }

    public enum Action {
        REQUEST_ALLIANCE(0, NationSavedData.DiplomacyAction.REQUEST_ALLIANCE),
        ACCEPT_ALLIANCE(1, NationSavedData.DiplomacyAction.ACCEPT_ALLIANCE),
        DECLINE_ALLIANCE(2, NationSavedData.DiplomacyAction.DECLINE_ALLIANCE),
        END_ALLIANCE(3, NationSavedData.DiplomacyAction.END_ALLIANCE),
        DECLARE_HOSTILE(4, NationSavedData.DiplomacyAction.DECLARE_HOSTILE),
        SET_NEUTRAL(5, NationSavedData.DiplomacyAction.SET_NEUTRAL),
        UNKNOWN(255, NationSavedData.DiplomacyAction.UNKNOWN);

        private final int networkId;
        private final NationSavedData.DiplomacyAction savedAction;

        Action(int networkId, NationSavedData.DiplomacyAction savedAction) {
            this.networkId = networkId;
            this.savedAction = savedAction;
        }

        private static Action fromNetworkId(int id) {
            for (Action action : values()) if (action.networkId == id) return action;
            return UNKNOWN;
        }
    }
}
