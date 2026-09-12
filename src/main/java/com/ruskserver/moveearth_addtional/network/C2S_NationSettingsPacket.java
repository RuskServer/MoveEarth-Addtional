package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import com.ruskserver.moveearth_addtional.s2.S2NationViewService;
import com.ruskserver.moveearth_addtional.s2.nation.NationAdministrationService;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;
import java.util.UUID;

public record C2S_NationSettingsPacket(int requestId, long expectedRevision, Action action,
                                       String name, String tag, UUID targetId)
        implements CustomPacketPayload {
    private static final UUID NO_TARGET = new UUID(0L, 0L);
    public static final Type<C2S_NationSettingsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "nation_settings"));
    public static final StreamCodec<FriendlyByteBuf, C2S_NationSettingsPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeVarInt(packet.requestId);
                buffer.writeLong(packet.expectedRevision);
                buffer.writeByte(packet.action.networkId);
                buffer.writeUtf(packet.name == null ? "" : packet.name, 64);
                buffer.writeUtf(packet.tag == null ? "" : packet.tag, 12);
                buffer.writeUUID(packet.targetId == null ? NO_TARGET : packet.targetId);
            },
            buffer -> new C2S_NationSettingsPacket(buffer.readVarInt(), buffer.readLong(),
                    Action.fromNetworkId(buffer.readUnsignedByte()), buffer.readUtf(64),
                    buffer.readUtf(12), buffer.readUUID()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            NationSavedData.NationAdminResult result = switch (action) {
                case UPDATE_IDENTITY -> NationAdministrationService.updateIdentity(
                        player, name, tag, expectedRevision);
                case TRANSFER_OWNER -> NationAdministrationService.transferOwner(
                        player, targetId, expectedRevision);
                case DISBAND -> NationAdministrationService.disband(player, expectedRevision);
                case UNKNOWN -> new NationSavedData.NationAdminResult(
                        NationSavedData.NationAdminStatus.INVALID,
                        NationSavedData.get(player.server).revision());
            };
            if (result.success()) S2NationViewService.INSTANCE.sendHub(player, S2HubTab.OVERVIEW);
            PacketDistributor.sendToPlayer(player, new S2C_S2ActionResultPacket(
                    requestId, result.success(), result.revision(),
                    "screen.moveearth_addtional.nation.settings.result."
                            + result.status().name().toLowerCase(Locale.ROOT)));
        });
    }

    public enum Action {
        UPDATE_IDENTITY(0), TRANSFER_OWNER(1), DISBAND(2), UNKNOWN(255);
        private final int networkId;
        Action(int networkId) { this.networkId = networkId; }
        private static Action fromNetworkId(int id) {
            for (Action value : values()) if (value.networkId == id) return value;
            return UNKNOWN;
        }
    }
}
