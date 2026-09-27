package com.ruskserver.moveearth_addtional.network.c2s.nation;

import com.ruskserver.moveearth_addtional.network.s2c.nation.S2C_OpenNationNotificationsPacket;

import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.DiscordLinkAccess;
import com.ruskserver.moveearth_addtional.s2.notification.DiscordLinkGateway;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NotificationCategory;
import com.ruskserver.moveearth_addtional.s2.notification.NotificationPresentation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class NationNotificationSnapshotFactory {
    private NationNotificationSnapshotFactory() { }

    static S2C_OpenNationNotificationsPacket create(ServerPlayer player) {
        NationSavedData nations = NationSavedData.get(player.server);
        UUID nationId = nations.nationIdFor(player.getUUID()).orElse(null);
        NationNotificationSavedData data = NationNotificationSavedData.get(player.server);
        NationNotificationSavedData.Settings settings = nationId == null
                ? new NationNotificationSavedData.Settings(true, false, false, false) : data.settings(nationId);
        NationNotificationSavedData.Link link = nationId == null
                ? NationNotificationSavedData.Link.unlinked() : data.link(nationId);
        DiscordLinkAccess discord = DiscordLinkGateway.access();
        var health = nationId == null
                ? new NationNotificationSavedData.DeliveryHealth(0, 0, 0, 0, 0, 0, "")
                : data.deliveryHealth(nationId);
        List<S2C_OpenNationNotificationsPacket.CategorySetting> categories =
                java.util.Arrays.stream(NotificationCategory.values()).map(category -> {
                    var preference = nationId == null
                            ? new com.ruskserver.moveearth_addtional.s2.notification.NotificationPreference(
                            com.ruskserver.moveearth_addtional.s2.notification.NotificationPreference.InGameMode.IMMEDIATE,
                            com.ruskserver.moveearth_addtional.s2.notification.NotificationPreference.DiscordMode.OFF,
                            com.ruskserver.moveearth_addtional.s2.notification.NotificationPreference.MentionPolicy.NONE)
                            : data.preference(nationId, category);
                    return new S2C_OpenNationNotificationsPacket.CategorySetting(category,
                            preference.inGame(), preference.discord(), preference.mention());
                }).toList();
        java.util.ArrayList<S2C_OpenNationNotificationsPacket.HistoryEntry> historyBuilder = new java.util.ArrayList<>();
        if (nationId != null) data.deliveries(nationId, 8).forEach(delivery -> historyBuilder.add(
                new S2C_OpenNationNotificationsPacket.HistoryEntry(delivery.createdAtMillis(),
                        NotificationPresentation.eventTitle(delivery.type()),
                        delivery.attempts() > 0 ? "retrying" : "pending",
                        delivery.attempts() > 0 ? REASON + "retry" : REASON + "pending")));
        if (nationId != null && historyBuilder.size() < 8) historyBuilder.addAll(data.recentAudit(nationId, 0L,
                        8 - historyBuilder.size()).stream()
                .filter(entry -> entry.action().startsWith("delivery") || entry.action().startsWith("test"))
                .map(entry -> new S2C_OpenNationNotificationsPacket.HistoryEntry(entry.atMillis(),
                        historyTitle(entry.action()), entry.success() ? "delivered" : "failed",
                        safeReason(entry.detail()))).toList());
        List<S2C_OpenNationNotificationsPacket.HistoryEntry> history = List.copyOf(historyBuilder);
        boolean canManage = nationId != null && nations.can(player.getUUID(), S2Permission.MANAGE_NOTIFICATIONS);
        return new S2C_OpenNationNotificationsPacket(S2C_OpenNationNotificationsPacket.CURRENT_SCHEMA,
                canManage, discord.state(), data.discordForMinecraft(player.getUUID()).isPresent(),
                link.linked(), discord.guildName(link.guildId()),
                discord.channelName(link.guildId(), link.channelId()),
                discord.roleName(link.guildId(), link.mentionRoleId()),
                nationId == null ? com.ruskserver.moveearth_addtional.s2.notification.NotificationPreset.CUSTOM
                        : data.preset(nationId), categories, settings.includeCoordinates(),
                settings.digestMinutes(), settings.mentionCooldownMinutes(), health.pending(), health.retrying(),
                health.dropped(), health.expired(), health.lastSuccessAtMillis(), health.lastFailureAtMillis(),
                safeReason(health.lastFailureReason()), history, data.revision(),
                canManage ? discord.inviteUrl() : "");
    }

    private static final String REASON = "screen.moveearth_addtional.notifications.reason.";

    private static String historyTitle(String action) {
        return "screen.moveearth_addtional.notifications.history." + switch (action) {
            case "test_delivery" -> "test";
            case "delivery_expired" -> "expired";
            default -> "delivery";
        };
    }

    /** Translation key (or a server-rendered event title) for a stored failure code; never the raw code. */
    private static String safeReason(String reason) {
        if (reason == null || reason.isBlank()) return "";
        if (reason.startsWith("retry_limit:")) return REASON + "retry_limit";
        if (reason.startsWith("retry:")) return REASON + "retry";
        try {
            return NotificationPresentation.eventTitle(NationNotificationSavedData.EventType.valueOf(reason));
        } catch (IllegalArgumentException ignored) { }
        return REASON + switch (reason.toLowerCase(Locale.ROOT)) {
            case "channel_unavailable" -> "channel_unavailable";
            case "temporary_api_failure", "retry" -> "retry";
            case "retry_limit" -> "retry_limit";
            default -> "unknown";
        };
    }
}
