package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.data.PlayerWhitelistSavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record C2S_UpdateWhitelistPacket(String playerName, boolean isAdd) implements CustomPacketPayload {

    private static final int MAX_NAME_LENGTH = 16;
    private static final int MAX_WHITELIST_SIZE = 100;

    public static final CustomPacketPayload.Type<C2S_UpdateWhitelistPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "update_whitelist"));

    public static final StreamCodec<FriendlyByteBuf, C2S_UpdateWhitelistPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeUtf(packet.playerName());
                buf.writeBoolean(packet.isAdd());
            },
            buf -> new C2S_UpdateWhitelistPacket(
                    buf.readUtf(MAX_NAME_LENGTH),
                    buf.readBoolean()
            )
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ServerLevel level = player.serverLevel();
                PlayerWhitelistSavedData data = PlayerWhitelistSavedData.get(level);

                if (playerName == null || playerName.isBlank() || playerName.length() > MAX_NAME_LENGTH
                        || !playerName.matches("[A-Za-z0-9_]+")) {
                    return;
                }

                if (isAdd && level.getServer().getPlayerList().getPlayers().stream()
                        .noneMatch(onlinePlayer -> onlinePlayer.getScoreboardName().equals(playerName))) {
                    return;
                }

                if (isAdd) {
                    if (data.getMemberNamesForDisplay(player.getUUID()).size() >= MAX_WHITELIST_SIZE
                            && !data.getMemberNamesForDisplay(player.getUUID()).contains(playerName)) {
                        return;
                    }
                    ServerPlayer targetPlayer = level.getServer().getPlayerList().getPlayerByName(playerName);
                    if (targetPlayer != null) {
                        data.addToWhitelist(player.getUUID(), targetPlayer.getUUID(), targetPlayer.getScoreboardName());
                    } else {
                        data.addByNameFallback(player.getUUID(), playerName, null);
                    }
                } else {
                    data.removeFromWhitelistByName(player.getUUID(), playerName);
                }

                // 更新された最新のホワイトリスト（表示名）とオンラインプレイヤー一覧を返信する
                List<String> whitelist = data.getMemberNamesForDisplay(player.getUUID());

                List<String> onlinePlayers = new ArrayList<>();
                for (ServerPlayer onlinePlayer : level.getServer().getPlayerList().getPlayers()) {
                    onlinePlayers.add(onlinePlayer.getScoreboardName());
                }

                PacketDistributor.sendToPlayer(player, new S2C_SyncWhitelistPacket(whitelist, onlinePlayers));
            }
        });
    }
}
