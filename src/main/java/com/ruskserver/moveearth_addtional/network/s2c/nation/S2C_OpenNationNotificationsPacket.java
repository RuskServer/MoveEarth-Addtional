package com.ruskserver.moveearth_addtional.network.s2c.nation;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.client.ClientPacketHandler;
import com.ruskserver.moveearth_addtional.s2.notification.DiscordLinkAccess;
import com.ruskserver.moveearth_addtional.s2.notification.NotificationCategory;
import com.ruskserver.moveearth_addtional.s2.notification.NotificationPreference;
import com.ruskserver.moveearth_addtional.s2.notification.NotificationPreset;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Versioned, ID-free client snapshot for the notification center. */
public record S2C_OpenNationNotificationsPacket(
        int schemaVersion, boolean canManage, DiscordLinkAccess.BotState botState,
        boolean accountLinked, boolean nationLinked, String guildName, String channelName, String roleName,
        NotificationPreset preset, List<CategorySetting> categories, boolean includeCoordinates,
        int digestMinutes, int mentionCooldownMinutes, int pendingCount, int retryingCount,
        long droppedCount, long expiredCount, long lastSuccessAtMillis, long lastFailureAtMillis,
        String lastFailureReason, List<HistoryEntry> history, long revision, String inviteUrl)
        implements CustomPacketPayload {
    public static final int CURRENT_SCHEMA = 3;
    public static final Type<S2C_OpenNationNotificationsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "open_nation_notifications"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2C_OpenNationNotificationsPacket> STREAM_CODEC =
            StreamCodec.of(S2C_OpenNationNotificationsPacket::write, S2C_OpenNationNotificationsPacket::read);

    public S2C_OpenNationNotificationsPacket {
        if (botState == null) botState = DiscordLinkAccess.BotState.UNAVAILABLE;
        guildName = safe(guildName);
        channelName = safe(channelName);
        roleName = safe(roleName);
        if (preset == null) preset = NotificationPreset.CUSTOM;
        categories = categories == null ? List.of() : List.copyOf(categories);
        lastFailureReason = safe(lastFailureReason);
        history = history == null ? List.of() : List.copyOf(history);
        inviteUrl = inviteUrl == null || inviteUrl.length() > 256 ? "" : inviteUrl;
    }

    private static void write(RegistryFriendlyByteBuf buffer, S2C_OpenNationNotificationsPacket packet) {
        buffer.writeVarInt(packet.schemaVersion);
        buffer.writeBoolean(packet.canManage);
        buffer.writeByte(packet.botState.ordinal());
        buffer.writeBoolean(packet.accountLinked);
        buffer.writeBoolean(packet.nationLinked);
        buffer.writeUtf(packet.guildName, 128);
        buffer.writeUtf(packet.channelName, 128);
        buffer.writeUtf(packet.roleName, 128);
        buffer.writeByte(packet.preset.ordinal());
        buffer.writeCollection(packet.categories, (target, value) -> {
            target.writeByte(value.category.ordinal());
            target.writeByte(value.inGame.ordinal());
            target.writeByte(value.discord.ordinal());
            target.writeByte(value.mention.ordinal());
        });
        buffer.writeBoolean(packet.includeCoordinates);
        buffer.writeVarInt(packet.digestMinutes);
        buffer.writeVarInt(packet.mentionCooldownMinutes);
        buffer.writeVarInt(packet.pendingCount);
        buffer.writeVarInt(packet.retryingCount);
        buffer.writeVarLong(packet.droppedCount);
        buffer.writeVarLong(packet.expiredCount);
        buffer.writeLong(packet.lastSuccessAtMillis);
        buffer.writeLong(packet.lastFailureAtMillis);
        buffer.writeUtf(packet.lastFailureReason, 128);
        buffer.writeCollection(packet.history, (target, value) -> {
            target.writeLong(value.atMillis);
            target.writeUtf(value.title, 128);
            target.writeUtf(value.state, 32);
            target.writeUtf(value.detail, 256);
        });
        buffer.writeLong(packet.revision);
        buffer.writeUtf(packet.inviteUrl, 256);
    }

    private static S2C_OpenNationNotificationsPacket read(RegistryFriendlyByteBuf buffer) {
        int schema = buffer.readVarInt();
        boolean manage = buffer.readBoolean();
        DiscordLinkAccess.BotState botState = enumAt(DiscordLinkAccess.BotState.values(), buffer.readUnsignedByte(),
                DiscordLinkAccess.BotState.UNAVAILABLE);
        boolean account = buffer.readBoolean(), nation = buffer.readBoolean();
        String guild = buffer.readUtf(128), channel = buffer.readUtf(128), role = buffer.readUtf(128);
        NotificationPreset preset = enumAt(NotificationPreset.values(), buffer.readUnsignedByte(), NotificationPreset.CUSTOM);
        List<CategorySetting> categories = buffer.readCollection(ArrayList::new, source -> new CategorySetting(
                enumAt(NotificationCategory.values(), source.readUnsignedByte(), NotificationCategory.DEFENSE),
                enumAt(NotificationPreference.InGameMode.values(), source.readUnsignedByte(),
                        NotificationPreference.InGameMode.IMMEDIATE),
                enumAt(NotificationPreference.DiscordMode.values(), source.readUnsignedByte(),
                        NotificationPreference.DiscordMode.OFF),
                enumAt(NotificationPreference.MentionPolicy.values(), source.readUnsignedByte(),
                        NotificationPreference.MentionPolicy.NONE)));
        boolean coordinates = buffer.readBoolean();
        int digest = buffer.readVarInt(), cooldown = buffer.readVarInt();
        int pending = buffer.readVarInt(), retrying = buffer.readVarInt();
        long dropped = buffer.readVarLong(), expired = buffer.readVarLong();
        long lastSuccess = buffer.readLong(), lastFailure = buffer.readLong();
        String reason = buffer.readUtf(128);
        List<HistoryEntry> history = buffer.readCollection(ArrayList::new, source -> new HistoryEntry(
                source.readLong(), source.readUtf(128), source.readUtf(32), source.readUtf(256)));
        long revision = buffer.readLong();
        String invite = buffer.readUtf(256);
        return new S2C_OpenNationNotificationsPacket(schema, manage, botState, account, nation,
                guild, channel, role, preset, categories, coordinates, digest, cooldown, pending, retrying,
                dropped, expired, lastSuccess, lastFailure, reason, history, revision, invite);
    }

    private static <T> T enumAt(T[] values, int index, T fallback) {
        return index >= 0 && index < values.length ? values[index] : fallback;
    }

    private static String safe(String value) {
        if (value == null) return "";
        return value.substring(0, Math.min(128, value.length()));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandler.handleOpenNationNotifications(this));
    }

    public record CategorySetting(NotificationCategory category, NotificationPreference.InGameMode inGame,
                                  NotificationPreference.DiscordMode discord,
                                  NotificationPreference.MentionPolicy mention) { }

    /** {@code title} and {@code detail} are translation keys, or literal text for server-rendered event titles. */
    public record HistoryEntry(long atMillis, String title, String state, String detail) { }
}
