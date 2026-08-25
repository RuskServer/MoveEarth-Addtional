package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record S2C_JobShopPacket(int points, double recurringXp, int recurringPointsInWindow,
                               int recurringSecondsRemaining, boolean canAdmin, List<ProductEntry> products)
        implements CustomPacketPayload {
    private static final int MAX_PRODUCTS = 128;
    public static final Type<S2C_JobShopPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "job_shop"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2C_JobShopPacket> STREAM_CODEC = StreamCodec.of(
            S2C_JobShopPacket::encode, S2C_JobShopPacket::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, S2C_JobShopPacket packet) {
        buffer.writeVarInt(packet.points);
        buffer.writeDouble(packet.recurringXp);
        buffer.writeVarInt(packet.recurringPointsInWindow);
        buffer.writeVarInt(packet.recurringSecondsRemaining);
        buffer.writeBoolean(packet.canAdmin);
        buffer.writeVarInt(packet.products.size());
        for (ProductEntry product : packet.products) {
            buffer.writeUUID(product.id);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, product.template);
            buffer.writeVarInt(product.price);
            buffer.writeVarInt(product.purchaseLimit);
            buffer.writeVarInt(product.purchased);
            buffer.writeBoolean(product.enabled);
        }
    }

    private static S2C_JobShopPacket decode(RegistryFriendlyByteBuf buffer) {
        int points = buffer.readVarInt();
        double recurringXp = buffer.readDouble();
        int recurringPointsInWindow = buffer.readVarInt();
        int recurringSecondsRemaining = buffer.readVarInt();
        boolean canAdmin = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_PRODUCTS) {
            throw new IllegalArgumentException("Invalid job shop product count: " + count);
        }
        List<ProductEntry> products = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            products.add(new ProductEntry(buffer.readUUID(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean()));
        }
        return new S2C_JobShopPacket(points, recurringXp, recurringPointsInWindow,
                recurringSecondsRemaining, canAdmin, List.copyOf(products));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() ->
                com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleJobShop(this));
    }

    public record ProductEntry(java.util.UUID id, ItemStack template, int price, int purchaseLimit,
                               int purchased, boolean enabled) {
        public int remainingPurchases() {
            return purchaseLimit <= 0 ? -1 : Math.max(0, purchaseLimit - purchased);
        }
    }
}
