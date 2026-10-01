package com.ruskserver.moveearth_addtional.network.s2c.nation;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.nation.NationNameplateRelation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Nameplate prefixes for players the viewer can see. {@code replace} is a full list (the client drops
 * what it had first); otherwise the packet is a delta: {@code entries} are added or replaced and
 * {@code removed} players are forgotten. The server sends a full list once per login and deltas after.
 */
public record S2C_NationNameplatesPacket(boolean replace, List<Entry> entries, List<UUID> removed)
        implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 512;
    public static final Type<S2C_NationNameplatesPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "nation_nameplates"));
    public static final StreamCodec<FriendlyByteBuf, S2C_NationNameplatesPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeBoolean(packet.replace);
                int size = Math.min(packet.entries.size(), MAX_ENTRIES);
                buffer.writeVarInt(size);
                for (int index = 0; index < size; index++) {
                    Entry entry = packet.entries.get(index);
                    buffer.writeUUID(entry.playerId);
                    buffer.writeUtf(entry.prefix, 80);
                    buffer.writeByte(entry.relation.ordinal());
                }
                int removedSize = Math.min(packet.removed.size(), MAX_ENTRIES);
                buffer.writeVarInt(removedSize);
                for (int index = 0; index < removedSize; index++) buffer.writeUUID(packet.removed.get(index));
            },
            buffer -> {
                boolean replace = buffer.readBoolean();
                int size = buffer.readVarInt();
                if (size < 0 || size > MAX_ENTRIES) throw new IllegalArgumentException("Invalid nameplate count: " + size);
                List<Entry> entries = new ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    entries.add(new Entry(buffer.readUUID(), buffer.readUtf(80),
                            NationNameplateRelation.fromNetworkId(buffer.readUnsignedByte())));
                }
                int removedSize = buffer.readVarInt();
                if (removedSize < 0 || removedSize > MAX_ENTRIES) {
                    throw new IllegalArgumentException("Invalid nameplate removal count: " + removedSize);
                }
                List<UUID> removed = new ArrayList<>(removedSize);
                for (int index = 0; index < removedSize; index++) removed.add(buffer.readUUID());
                return new S2C_NationNameplatesPacket(replace, entries, removed);
            });

    public S2C_NationNameplatesPacket {
        entries = entries == null ? List.of() : List.copyOf(entries);
        removed = removed == null ? List.of() : List.copyOf(removed);
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
