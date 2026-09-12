package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.nation.NationNameplateRelation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record S2C_NationNameplatesPacket(List<Entry> entries) implements CustomPacketPayload {
    private static final int MAX_ENTRIES = 512;
    public static final Type<S2C_NationNameplatesPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "nation_nameplates"));
    public static final StreamCodec<FriendlyByteBuf, S2C_NationNameplatesPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                int size = Math.min(packet.entries.size(), MAX_ENTRIES);
                buffer.writeVarInt(size);
                for (int index = 0; index < size; index++) {
                    Entry entry = packet.entries.get(index);
                    buffer.writeUUID(entry.playerId);
                    buffer.writeUtf(entry.prefix, 80);
                    buffer.writeByte(entry.relation.ordinal());
                }
            },
            buffer -> {
                int size = buffer.readVarInt();
                if (size < 0 || size > MAX_ENTRIES) throw new IllegalArgumentException("Invalid nameplate count: " + size);
                List<Entry> entries = new ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    entries.add(new Entry(buffer.readUUID(), buffer.readUtf(80),
                            NationNameplateRelation.fromNetworkId(buffer.readUnsignedByte())));
                }
                return new S2C_NationNameplatesPacket(entries);
            });

    public S2C_NationNameplatesPacket {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() ->
                com.ruskserver.moveearth_addtional.client.NationNameplateClientState.update(this));
    }

    public record Entry(UUID playerId, String prefix, NationNameplateRelation relation) {
        public Entry {
            if (playerId == null) playerId = new UUID(0L, 0L);
            prefix = prefix == null ? "" : prefix;
            if (relation == null) relation = NationNameplateRelation.NEUTRAL;
        }
    }
}
