package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationSavedData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public record C2S_UpdateNationNotificationsPacket(int requestId, long expectedRevision,
                                                  boolean inGame, boolean discord,
                                                  boolean includeCoordinates, boolean mentionOnSiege)
        implements CustomPacketPayload {
    public static final Type<C2S_UpdateNationNotificationsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "update_nation_notifications"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2S_UpdateNationNotificationsPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> {
                buffer.writeVarInt(packet.requestId);
                buffer.writeLong(packet.expectedRevision);
                buffer.writeBoolean(packet.inGame);
                buffer.writeBoolean(packet.discord);
                buffer.writeBoolean(packet.includeCoordinates);
                buffer.writeBoolean(packet.mentionOnSiege);
            }, buffer -> new C2S_UpdateNationNotificationsPacket(buffer.readVarInt(), buffer.readLong(),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            NationSavedData nations = NationSavedData.get(player.server);
            UUID nationId = nations.nationIdFor(player.getUUID()).orElse(null);
            NationNotificationSavedData data = NationNotificationSavedData.get(player.server);
            String result = "saved";
            boolean success = nationId != null && nations.can(player.getUUID(), S2Permission.MANAGE_NOTIFICATIONS);
            if (!success) result = "no_permission";
            else if (expectedRevision != data.revision()) {
                success = false;
                result = "stale";
            } else if (discord && !data.link(nationId).linked()) {
                success = false;
                result = "not_linked";
            } else {
                data.updateSettings(nationId, new NationNotificationSavedData.Settings(
                        inGame, discord, includeCoordinates, mentionOnSiege));
            }
            PacketDistributor.sendToPlayer(player, new S2C_S2ActionResultPacket(requestId, success,
                    data.revision(), "screen.moveearth_addtional.notifications.result." + result));
        });
    }
}
