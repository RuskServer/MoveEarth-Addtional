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

/** One player's view of the regions around them. */
public record S2C_RegionSnapshotPacket(RegionSnapshot snapshot) implements CustomPacketPayload {

    /** A region borders at most a handful of others; this is room to spare. */
    private static final int MAX_REGIONS = 64;

    /** Convention material names are short; this is far more than any needs. */
    private static final int MAX_NAME = 64;

    private static final int MAX_EXCLUSIVES = 16;

    public static final Type<S2C_RegionSnapshotPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "region_snapshot"));

    public static final StreamCodec<FriendlyByteBuf, S2C_RegionSnapshotPacket> STREAM_CODEC =
            StreamCodec.of(S2C_RegionSnapshotPacket::encode, S2C_RegionSnapshotPacket::decode);

    private static void encode(FriendlyByteBuf buffer, S2C_RegionSnapshotPacket packet) {
        RegionSnapshot snapshot = packet.snapshot();
        buffer.writeVarInt(snapshot.currentRegion());
        List<RegionSnapshot.Entry> entries = snapshot.regions().stream().limit(MAX_REGIONS).toList();
        buffer.writeVarInt(entries.size());
        for (RegionSnapshot.Entry entry : entries) {
            buffer.writeVarInt(entry.id());
            buffer.writeBoolean(entry.current());
            buffer.writeBoolean(entry.known());
            List<String> exclusives = entry.exclusives().stream().limit(MAX_EXCLUSIVES).toList();
            buffer.writeVarInt(exclusives.size());
            for (String material : exclusives) {
                buffer.writeUtf(material, MAX_NAME);
            }
            buffer.writeUtf(entry.specialty(), MAX_NAME);
            buffer.writeUtf(entry.shortage(), MAX_NAME);
            buffer.writeDouble(entry.baseDensity());
        }
    }

    private static S2C_RegionSnapshotPacket decode(FriendlyByteBuf buffer) {
        int current = buffer.readVarInt();
        // Read from the network, so every count is clamped before it is used to
        // size anything. A hostile or simply wrong length must cost a short
        // list, not an allocation the size of the number that arrived.
        int count = Math.min(MAX_REGIONS, Math.max(0, buffer.readVarInt()));
        List<RegionSnapshot.Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int id = buffer.readVarInt();
            boolean isCurrent = buffer.readBoolean();
            boolean known = buffer.readBoolean();
            int exclusiveCount = Math.min(MAX_EXCLUSIVES, Math.max(0, buffer.readVarInt()));
            List<String> exclusives = new ArrayList<>(exclusiveCount);
            for (int slot = 0; slot < exclusiveCount; slot++) {
                exclusives.add(buffer.readUtf(MAX_NAME));
            }
            entries.add(new RegionSnapshot.Entry(id, isCurrent, known, exclusives,
                    buffer.readUtf(MAX_NAME), buffer.readUtf(MAX_NAME), buffer.readDouble()));
        }
        return new S2C_RegionSnapshotPacket(new RegionSnapshot(current, entries));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandler.openRegionScreen(snapshot));
    }
}
