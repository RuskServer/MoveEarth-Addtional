package com.ruskserver.moveearth_addtional.network.c2s.nation;

import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_S2ActionResultPacket;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NotificationCategory;
import com.ruskserver.moveearth_addtional.s2.notification.NotificationPreference;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

public record C2S_UpdateNationNotificationsPacket(int requestId, long expectedRevision,
                                                  List<PreferenceUpdate> categories,
                                                  boolean includeCoordinates, int digestMinutes,
                                                  int mentionCooldownMinutes) implements CustomPacketPayload {
    public static final Type<C2S_UpdateNationNotificationsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "update_nation_notifications"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2S_UpdateNationNotificationsPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> {
                buffer.writeVarInt(packet.requestId);
                buffer.writeLong(packet.expectedRevision);
                buffer.writeCollection(packet.categories, (target, value) -> {
                    target.writeByte(value.category.ordinal());
                    target.writeByte(value.inGame.ordinal());
                    target.writeByte(value.discord.ordinal());
                    target.writeByte(value.mention.ordinal());
                });
                buffer.writeBoolean(packet.includeCoordinates);
                buffer.writeVarInt(packet.digestMinutes);
                buffer.writeVarInt(packet.mentionCooldownMinutes);
            }, buffer -> new C2S_UpdateNationNotificationsPacket(buffer.readVarInt(), buffer.readLong(),
                    buffer.readCollection(ArrayList::new, source -> new PreferenceUpdate(
                            enumAt(NotificationCategory.values(), source.readUnsignedByte(), NotificationCategory.DEFENSE),
                            enumAt(NotificationPreference.InGameMode.values(), source.readUnsignedByte(),
                                    NotificationPreference.InGameMode.IMMEDIATE),
                            enumAt(NotificationPreference.DiscordMode.values(), source.readUnsignedByte(),
                                    NotificationPreference.DiscordMode.OFF),
                            enumAt(NotificationPreference.MentionPolicy.values(), source.readUnsignedByte(),
                                    NotificationPreference.MentionPolicy.NONE))),
                    buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt()));

    public C2S_UpdateNationNotificationsPacket {
        categories = categories == null ? List.of() : List.copyOf(categories).stream().limit(5).toList();
    }

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
            } else {
                EnumMap<NotificationCategory, NotificationPreference> values =
                        new EnumMap<>(NotificationCategory.class);
                for (PreferenceUpdate update : categories) values.put(update.category,
                        new NotificationPreference(update.inGame, update.discord, update.mention));
                boolean wantsDiscord = values.values().stream().anyMatch(value ->
                        value.discord() != NotificationPreference.DiscordMode.OFF);
                if (wantsDiscord && !data.link(nationId).linked()) {
                    success = false;
                    result = "not_linked";
                } else data.updateCategorySettings(nationId, values, includeCoordinates,
                        digestMinutes, mentionCooldownMinutes);
            }
            PacketDistributor.sendToPlayer(player, new S2C_S2ActionResultPacket(requestId, success,
                    data.revision(), "screen.moveearth_addtional.notifications.result." + result));
            if (success) PacketDistributor.sendToPlayer(player, NationNotificationSnapshotFactory.create(player));
        });
    }

    private static <T> T enumAt(T[] values, int index, T fallback) {
        return index >= 0 && index < values.length ? values[index] : fallback;
    }

    public record PreferenceUpdate(NotificationCategory category,
                                   NotificationPreference.InGameMode inGame,
                                   NotificationPreference.DiscordMode discord,
                                   NotificationPreference.MentionPolicy mention) { }
}
