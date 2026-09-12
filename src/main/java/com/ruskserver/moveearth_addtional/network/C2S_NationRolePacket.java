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

public record C2S_NationRolePacket(int requestId, long expectedRevision, Action action,
                                   String roleId, String displayName, long permissionMask, UUID targetId)
        implements CustomPacketPayload {
    private static final UUID NO_TARGET = new UUID(0L, 0L);
    public static final Type<C2S_NationRolePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "nation_role"));
    public static final StreamCodec<FriendlyByteBuf, C2S_NationRolePacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeVarInt(packet.requestId);
                buffer.writeLong(packet.expectedRevision);
                buffer.writeByte(packet.action.networkId);
                buffer.writeUtf(packet.roleId == null ? "" : packet.roleId, 48);
                buffer.writeUtf(packet.displayName == null ? "" : packet.displayName, 24);
                buffer.writeLong(packet.permissionMask);
                buffer.writeUUID(packet.targetId == null ? NO_TARGET : packet.targetId);
            },
            buffer -> new C2S_NationRolePacket(buffer.readVarInt(), buffer.readLong(),
                    Action.fromNetworkId(buffer.readUnsignedByte()), buffer.readUtf(48),
                    buffer.readUtf(24), buffer.readLong(), buffer.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            NationSavedData data = NationSavedData.get(player.server);
            NationSavedData.RoleResult result = switch (action) {
                case SAVE -> data.saveRole(player.getUUID(), roleId, displayName,
                        permissionMask, expectedRevision);
                case ASSIGN -> data.assignRole(player.getUUID(), targetId, roleId, expectedRevision);
                case DELETE -> data.deleteRole(player.getUUID(), roleId, expectedRevision);
                case UNKNOWN -> new NationSavedData.RoleResult(
                        NationSavedData.RoleStatus.NO_PERMISSION, data.revision(), "");
            };
            String messageKey = "screen.moveearth_addtional.nation.role.result."
                    + result.status().name().toLowerCase(Locale.ROOT);
            if (result.success()) {
                S2NationViewService.INSTANCE.sendHub(player,
                        action == Action.ASSIGN ? S2HubTab.MEMBERS : S2HubTab.ROLES);
            }
            PacketDistributor.sendToPlayer(player, new S2C_S2ActionResultPacket(
                    requestId, result.success(), result.revision(), messageKey));
        });
    }

    public enum Action {
        SAVE(0), ASSIGN(1), DELETE(2), UNKNOWN(255);

        private final int networkId;

        Action(int networkId) {
            this.networkId = networkId;
        }

        private static Action fromNetworkId(int id) {
            for (Action action : values()) if (action.networkId == id) return action;
            return UNKNOWN;
        }
    }
}
