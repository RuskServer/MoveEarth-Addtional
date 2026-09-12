package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
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

/** Verifies a one-to-one Minecraft UUID to Discord user link. */
public record C2S_LinkDiscordAccountPacket(int requestId, String code) implements CustomPacketPayload {
    public static final Type<C2S_LinkDiscordAccountPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "link_discord_account"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2S_LinkDiscordAccountPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> {
                buffer.writeVarInt(packet.requestId);
                buffer.writeUtf(packet.code, 16);
            }, buffer -> new C2S_LinkDiscordAccountPacket(buffer.readVarInt(), buffer.readUtf(16)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            NationNotificationSavedData data = NationNotificationSavedData.get(player.server);
            DiscordBotService bot = DiscordBotService.instance();
            boolean success = false;
            String result;
            long discordId = 0L;
            if (!bot.isReady()) {
                result = "bot_offline";
            } else {
                Optional<DiscordLinkCodeRegistry.PendingLink> pending = bot.consumeAccountLinkCode(code);
                if (pending.isEmpty()) {
                    result = "invalid_code";
                } else {
                    discordId = pending.get().discordUserId();
                    NationNotificationSavedData.AccountLinkResult linked = data.linkAccount(
                            player.getUUID(), discordId);
                    success = linked == NationNotificationSavedData.AccountLinkResult.LINKED
                            || linked == NationNotificationSavedData.AccountLinkResult.ALREADY_LINKED;
                    result = switch (linked) {
                        case LINKED, ALREADY_LINKED -> "account_linked";
                        case MINECRAFT_IN_USE -> "minecraft_in_use";
                        case DISCORD_IN_USE -> "discord_in_use";
                        case INVALID -> "invalid_code";
                    };
                }
            }
            data.audit("account_link", null, player.getUUID(), discordId, success, result);
            PacketDistributor.sendToPlayer(player, new S2C_S2ActionResultPacket(requestId, success,
                    data.revision(), "screen.moveearth_addtional.notifications.result." + result));
        });
    }
}
