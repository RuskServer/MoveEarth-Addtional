package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.client.ClientPacketHandler;
import com.ruskserver.moveearth_addtional.pvp.PvpLoadoutDefinition;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * サーバーからクライアントへ最新のロードアウト一覧を同期するパケット。
 */
public record S2C_SyncLoadoutsPacket(List<PvpLoadoutDefinition> loadouts) implements CustomPacketPayload {

    public static final Type<S2C_SyncLoadoutsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_sync_loadouts"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2C_SyncLoadoutsPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.loadouts.size());
                for (PvpLoadoutDefinition def : p.loadouts) {
                    def.write(buf);
                }
            },
            buf -> {
                int count = buf.readVarInt();
                List<PvpLoadoutDefinition> list = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    list.add(PvpLoadoutDefinition.read(buf));
                }
                return new S2C_SyncLoadoutsPacket(list);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandler.handleSyncLoadouts(this));
    }
}
