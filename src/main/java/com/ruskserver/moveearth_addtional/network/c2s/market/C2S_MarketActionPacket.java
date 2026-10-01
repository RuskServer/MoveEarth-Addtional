package com.ruskserver.moveearth_addtional.network.c2s.market;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.economy.MarketScreenSync;
import com.ruskserver.moveearth_addtional.economy.MarketSearch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Every market screen request. {@code query} and {@code itemKeys} carry the screen's current search
 * (see {@link com.ruskserver.moveearth_addtional.economy.MarketSearch}); the server keeps the latest one
 * per player and filters every snapshot it sends by it.
 */
public record C2S_MarketActionPacket(String action, UUID target, int quantity, long unitPrice,
                                     String query, List<String> itemKeys)
        implements CustomPacketPayload {
    public static final Type<C2S_MarketActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "market_action"));
    public static final StreamCodec<FriendlyByteBuf, C2S_MarketActionPacket> STREAM_CODEC = StreamCodec.of(
            C2S_MarketActionPacket::encode, C2S_MarketActionPacket::decode);

    public C2S_MarketActionPacket {
        query = MarketSearch.normalize(query);
        List<String> keys = new ArrayList<>();
        if (itemKeys != null) {
            for (String key : itemKeys) {
                if (keys.size() >= MarketSearch.MAX_ITEM_KEYS) break;
                if (key != null && !key.isBlank() && key.length() <= MarketSearch.MAX_ITEM_KEY_LENGTH) keys.add(key);
            }
        }
        itemKeys = List.copyOf(keys);
    }

    private static void encode(FriendlyByteBuf buf, C2S_MarketActionPacket packet) {
        buf.writeUtf(packet.action, 16);
        buf.writeUUID(packet.target);
        buf.writeVarInt(packet.quantity);
        buf.writeVarLong(packet.unitPrice);
        buf.writeUtf(packet.query, MarketSearch.MAX_TEXT_LENGTH);
        buf.writeVarInt(packet.itemKeys.size());
        for (String key : packet.itemKeys) buf.writeUtf(key, MarketSearch.MAX_ITEM_KEY_LENGTH);
    }

    private static C2S_MarketActionPacket decode(FriendlyByteBuf buf) {
        String action = buf.readUtf(16);
        UUID target = buf.readUUID();
        int quantity = buf.readVarInt();
        long unitPrice = buf.readVarLong();
        String query = buf.readUtf(MarketSearch.MAX_TEXT_LENGTH);
        int count = buf.readVarInt();
        if (count < 0 || count > MarketSearch.MAX_ITEM_KEYS) throw new IllegalArgumentException("Market search too large");
        List<String> keys = new ArrayList<>(count);
        for (int index = 0; index < count; index++) keys.add(buf.readUtf(MarketSearch.MAX_ITEM_KEY_LENGTH));
        return new C2S_MarketActionPacket(action, target, quantity, unitPrice, query, keys);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) MarketScreenSync.handle(player, this);
        });
    }
}
