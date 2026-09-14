package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.DiscordLinkCodeRegistry;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.discord.DiscordBotService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;
import java.util.UUID;

/** Confirms a short-lived Discord pairing code from the nation GUI. */
public record C2S_LinkNationDiscordPacket(int requestId, String code) implements CustomPacketPayload {
    public static final Type<C2S_LinkNationDiscordPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "link_nation_discord"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2S_LinkNationDiscordPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> {
                buffer.writeVarInt(packet.requestId);
                buffer.writeUtf(packet.code, 16);
            }, buffer -> new C2S_LinkNationDiscordPacket(buffer.readVarInt(), buffer.readUtf(16)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            NationSavedData nations = NationSavedData.get(player.server);
            UUID nationId = nations.nationIdFor(player.getUUID()).orElse(null);
            NationNotificationSavedData notifications = NationNotificationSavedData.get(player.server);
            DiscordBotService bot = DiscordBotService.instance();
            boolean success = false;
            String result;
            if (!DiscordLinkAttemptLimiter.allow(player, "nation")) {
                result = "invalid_code";
            } else if (nationId == null || !nations.can(player.getUUID(), S2Permission.MANAGE_NOTIFICATIONS)) {
                result = "no_permission";
            } else if (!bot.isReady()) {
                result = "bot_offline";
            } else {
                Optional<DiscordLinkCodeRegistry.PendingLink> pending = bot.consumeLinkCode(code);
                if (pending.isEmpty()) {
                    result = "invalid_code";
                } else if (!bot.canDeliverTo(pending.get().guildId(), pending.get().channelId())) {
                    result = "channel_unavailable";
                } else {
                    var account = notifications.linkAccount(player.getUUID(), pending.get().discordUserId());
                    if (account == NationNotificationSavedData.AccountLinkResult.DISCORD_IN_USE
                            || account == NationNotificationSavedData.AccountLinkResult.MINECRAFT_IN_USE
                            || account == NationNotificationSavedData.AccountLinkResult.INVALID) {
                        result = "account_conflict";
                    } else {
                        notifications.markLinked(nationId, pending.get().guildId(), pending.get().channelId());
                        success = true;
                        result = "link_saved";
                    }
                }
            }
            notifications.audit("nation_link", nationId, player.getUUID(),
                    notifications.discordForMinecraft(player.getUUID()).orElse(0L), success, result);
            PacketDistributor.sendToPlayer(player, new S2C_S2ActionResultPacket(requestId, success,
                    notifications.revision(), "screen.moveearth_addtional.notifications.result." + result));
        });
    }
}
