package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.jobs.JobShopService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record C2S_JobShopActionPacket(String action, String productId, int price, int purchaseLimit)
        implements CustomPacketPayload {
    public static final Type<C2S_JobShopActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "job_shop_action"));
    public static final StreamCodec<FriendlyByteBuf, C2S_JobShopActionPacket> STREAM_CODEC = StreamCodec.of(
            C2S_JobShopActionPacket::encode, C2S_JobShopActionPacket::decode);

    private static void encode(FriendlyByteBuf buffer, C2S_JobShopActionPacket packet) {
        buffer.writeUtf(packet.action, 16);
        buffer.writeUtf(packet.productId, 36);
        buffer.writeInt(packet.price);
        buffer.writeInt(packet.purchaseLimit);
    }

    private static C2S_JobShopActionPacket decode(FriendlyByteBuf buffer) {
        return new C2S_JobShopActionPacket(buffer.readUtf(16), buffer.readUtf(36),
                buffer.readInt(), buffer.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                JobShopService.handle(player, this);
            }
        });
    }
}
