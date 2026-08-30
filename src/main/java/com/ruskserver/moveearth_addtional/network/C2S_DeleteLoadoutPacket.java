package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.pvp.PvpLoadoutSavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 管理者クライアントからサーバーへロードアウトを削除するパケット。
 */
public record C2S_DeleteLoadoutPacket(String loadoutId) implements CustomPacketPayload {

    public static final Type<C2S_DeleteLoadoutPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_delete_loadout"));

    public static final StreamCodec<FriendlyByteBuf, C2S_DeleteLoadoutPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeUtf(p.loadoutId),
            buf -> new C2S_DeleteLoadoutPacket(buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (!player.hasPermissions(2)) {
                    player.sendSystemMessage(Component.literal("§cロードアウトの削除権限がありません。"));
                    return;
                }

                PvpLoadoutSavedData data = PvpLoadoutSavedData.get(player.server);
                if (data.delete(loadoutId)) {
                    player.sendSystemMessage(Component.literal("§aロードアウト [" + loadoutId + "] を削除しました。"));
                    PacketDistributor.sendToAllPlayers(new S2C_SyncLoadoutsPacket(data.getAll()));
                } else {
                    player.sendSystemMessage(Component.literal("§cロードアウトの削除に失敗しました（最低1つのロードアウトが必要です）。"));
                }
            }
        });
    }
}
