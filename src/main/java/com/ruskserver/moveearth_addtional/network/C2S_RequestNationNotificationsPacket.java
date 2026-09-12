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

public record C2S_RequestNationNotificationsPacket() implements CustomPacketPayload {
    public static final Type<C2S_RequestNationNotificationsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "request_nation_notifications"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2S_RequestNationNotificationsPacket> STREAM_CODEC =
            StreamCodec.unit(new C2S_RequestNationNotificationsPacket());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            NationSavedData nations = NationSavedData.get(player.server);
            UUID nationId = nations.nationIdFor(player.getUUID()).orElse(null);
            if (nationId == null) return;
            NationNotificationSavedData data = NationNotificationSavedData.get(player.server);
            NationNotificationSavedData.Settings settings = data.settings(nationId);
            NationNotificationSavedData.Link link = data.link(nationId);
            PacketDistributor.sendToPlayer(player, new S2C_OpenNationNotificationsPacket(
                    nations.can(player.getUUID(), S2Permission.MANAGE_NOTIFICATIONS), link.linked(),
                    settings.inGame(), settings.discord(), settings.includeCoordinates(),
                    settings.mentionOnSiege(), data.pendingCount(nationId), data.revision()));
        });
    }
}
