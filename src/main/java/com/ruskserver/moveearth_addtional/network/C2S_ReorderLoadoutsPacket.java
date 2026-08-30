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

import java.util.ArrayList;
import java.util.List;

/**
 * 管理者クライアントからサーバーへロードアウトの表示順序変更を送信するパケット。
 */
public record C2S_ReorderLoadoutsPacket(List<String> orderedIds) implements CustomPacketPayload {

    public static final Type<C2S_ReorderLoadoutsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_reorder_loadouts"));

    public static final StreamCodec<FriendlyByteBuf, C2S_ReorderLoadoutsPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.orderedIds.size());
                for (String id : p.orderedIds) {
                    buf.writeUtf(id);
                }
            },
            buf -> {
                int count = buf.readVarInt();
                List<String> list = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    list.add(buf.readUtf());
                }
                return new C2S_ReorderLoadoutsPacket(list);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (!player.hasPermissions(2)) {
                    player.sendSystemMessage(Component.literal("§cロードアウトの順序変更権限がありません。"));
                    return;
                }

                PvpLoadoutSavedData data = PvpLoadoutSavedData.get(player.server);
                data.reorder(orderedIds);
                PacketDistributor.sendToAllPlayers(new S2C_SyncLoadoutsPacket(data.getAll()));
            }
        });
    }
}
