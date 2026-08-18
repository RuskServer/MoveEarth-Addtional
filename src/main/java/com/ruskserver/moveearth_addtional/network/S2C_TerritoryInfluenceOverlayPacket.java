package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2C_TerritoryInfluenceOverlayPacket(
        int originX,
        int originZ,
        int step,
        int width,
        int[] cells,
        short[] heights
) implements CustomPacketPayload {
    private static final int MAX_CELLS = 4_225;
    public static final Type<S2C_TerritoryInfluenceOverlayPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "territory_influence_overlay"));
    public static final StreamCodec<FriendlyByteBuf, S2C_TerritoryInfluenceOverlayPacket> STREAM_CODEC =
            StreamCodec.of(S2C_TerritoryInfluenceOverlayPacket::encode,
                    S2C_TerritoryInfluenceOverlayPacket::decode);

    public S2C_TerritoryInfluenceOverlayPacket {
        if (step <= 0 || width <= 0 || cells.length != width * width
                || heights.length != cells.length || cells.length > MAX_CELLS) {
            throw new IllegalArgumentException("invalid territory overlay grid");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() ->
                com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleTerritoryInfluenceOverlay(this));
    }

    private static void encode(FriendlyByteBuf buffer, S2C_TerritoryInfluenceOverlayPacket packet) {
        buffer.writeInt(packet.originX);
        buffer.writeInt(packet.originZ);
        buffer.writeVarInt(packet.step);
        buffer.writeVarInt(packet.width);
        for (int index = 0; index < packet.cells.length; index++) {
            buffer.writeInt(packet.cells[index]);
            buffer.writeShort(packet.heights[index]);
        }
    }

    private static S2C_TerritoryInfluenceOverlayPacket decode(FriendlyByteBuf buffer) {
        int originX = buffer.readInt();
        int originZ = buffer.readInt();
        int step = buffer.readVarInt();
        int width = buffer.readVarInt();
        if (step <= 0 || width <= 0 || width > 65 || width * width > MAX_CELLS) {
            throw new IllegalArgumentException("invalid territory overlay dimensions");
        }
        int[] cells = new int[width * width];
        short[] heights = new short[cells.length];
        for (int index = 0; index < cells.length; index++) {
            cells[index] = buffer.readInt();
            heights[index] = buffer.readShort();
        }
        return new S2C_TerritoryInfluenceOverlayPacket(originX, originZ, step, width, cells, heights);
    }
}
