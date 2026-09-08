package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.pvp.PvpLoadoutDefinition;
import com.ruskserver.moveearth_addtional.pvp.PvpLoadoutSavedData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 管理者クライアントからサーバーへロードアウトを保存・更新するパケット。
 */
public record C2S_SaveLoadoutPacket(PvpLoadoutDefinition definition) implements CustomPacketPayload {

    public static final Type<C2S_SaveLoadoutPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_save_loadout"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2S_SaveLoadoutPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> p.definition.write(buf),
            buf -> new C2S_SaveLoadoutPacket(PvpLoadoutDefinition.read(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (!player.hasPermissions(2)) {
                    player.sendSystemMessage(Component.literal("§cロードアウトの編集権限がありません。"));
                    return;
                }

                PvpLoadoutSavedData data = PvpLoadoutSavedData.get(player.server);
                data.addOrUpdate(definition);
                player.sendSystemMessage(Component.literal("§aロードアウト [" + definition.displayName() + "] を保存しました。"));

                // 全員に同期
                PacketDistributor.sendToAllPlayers(new S2C_SyncLoadoutsPacket(data.getAll()));
            }
        });
    }
}
