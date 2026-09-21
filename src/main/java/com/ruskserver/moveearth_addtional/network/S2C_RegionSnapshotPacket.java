package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.client.ClientPacketHandler;
import com.ruskserver.moveearth_addtional.region.RegionSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** What the region under one player holds, in answer to their hub asking. */
public record S2C_RegionSnapshotPacket(RegionSnapshot snapshot) implements CustomPacketPayload {

    /** Convention material names are short; this is far more than any needs. */
    private static final int MAX_NAME = 64;

    /** More exclusives than any pack is likely to declare. */
    private static final int MAX_MATERIALS = 32;

    public static final Type<S2C_RegionSnapshotPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "region_snapshot"));

    public static final StreamCodec<FriendlyByteBuf, S2C_RegionSnapshotPacket> STREAM_CODEC =
            StreamCodec.of(S2C_RegionSnapshotPacket::encode, S2C_RegionSnapshotPacket::decode);

    private static void encode(FriendlyByteBuf buffer, S2C_RegionSnapshotPacket packet) {
        RegionSnapshot snapshot = packet.snapshot();
        buffer.writeVarInt(snapshot.id());
        writeMaterials(buffer, snapshot.exclusives());
        writeMaterials(buffer, snapshot.elsewhere());
        buffer.writeUtf(snapshot.specialty(), MAX_NAME);
        buffer.writeUtf(snapshot.shortage(), MAX_NAME);
        buffer.writeDouble(snapshot.traceShare());
    }

    private static S2C_RegionSnapshotPacket decode(FriendlyByteBuf buffer) {
        return new S2C_RegionSnapshotPacket(new RegionSnapshot(
                buffer.readVarInt(), readMaterials(buffer), readMaterials(buffer),
                buffer.readUtf(MAX_NAME), buffer.readUtf(MAX_NAME), buffer.readDouble()));
    }

    private static void writeMaterials(FriendlyByteBuf buffer, List<String> materials) {
        List<String> capped = materials.stream().limit(MAX_MATERIALS).toList();
        buffer.writeVarInt(capped.size());
        for (String material : capped) {
            buffer.writeUtf(material, MAX_NAME);
        }
    }

    private static List<String> readMaterials(FriendlyByteBuf buffer) {
        // Read from the network, so the count is clamped before it sizes
        // anything. A wrong length must cost a short list, not an allocation
        // the size of whatever number arrived.
        int count = Math.min(MAX_MATERIALS, Math.max(0, buffer.readVarInt()));
        List<String> materials = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            materials.add(buffer.readUtf(MAX_NAME));
        }
        return materials;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandler.handleRegionSnapshot(snapshot));
    }
}
