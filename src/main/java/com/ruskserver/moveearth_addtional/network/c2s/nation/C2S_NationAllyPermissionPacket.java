package com.ruskserver.moveearth_addtional.network.c2s.nation;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_S2ActionResultPacket;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import com.ruskserver.moveearth_addtional.s2.S2NationViewService;
import com.ruskserver.moveearth_addtional.s2.nation.AllyPermission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;
import java.util.UUID;

/**
 * Sets one grant the sender's nation gives an allied nation inside the sender's territory. A zero
 * {@code targetPlayerId} means the whole allied nation; otherwise one of its members. The server
 * re-checks the sender's diplomacy permission and the alliance.
 */
public record C2S_NationAllyPermissionPacket(int requestId, long expectedRevision, UUID allyNationId,
                                             UUID targetPlayerId, int permissionId, boolean enabled)
        implements CustomPacketPayload {
    public static final UUID NATION_WIDE = new UUID(0L, 0L);
    public static final Type<C2S_NationAllyPermissionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "nation_ally_permission"));
    public static final StreamCodec<FriendlyByteBuf, C2S_NationAllyPermissionPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeVarInt(packet.requestId);
                buffer.writeLong(packet.expectedRevision);
                buffer.writeUUID(packet.allyNationId);
                buffer.writeUUID(packet.targetPlayerId == null ? NATION_WIDE : packet.targetPlayerId);
                buffer.writeByte(packet.permissionId);
                buffer.writeBoolean(packet.enabled);
            },
            buffer -> new C2S_NationAllyPermissionPacket(buffer.readVarInt(), buffer.readLong(),
                    buffer.readUUID(), buffer.readUUID(), buffer.readUnsignedByte(), buffer.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            NationSavedData data = NationSavedData.get(player.server);
            UUID target = targetPlayerId == null || NATION_WIDE.equals(targetPlayerId) ? null : targetPlayerId;
            NationSavedData.DiplomacyResult result = data.setAllyPermission(player.getUUID(), allyNationId,
                    target, AllyPermission.fromNetworkId(permissionId), enabled, expectedRevision);
            if (result.success()) S2NationViewService.INSTANCE.sendHub(player, S2HubTab.DIPLOMACY);
            PacketDistributor.sendToPlayer(player, new S2C_S2ActionResultPacket(
                    requestId, result.success(), result.revision(),
                    "screen.moveearth_addtional.diplomacy.result."
                            + result.status().name().toLowerCase(Locale.ROOT)));
        });
    }
}
