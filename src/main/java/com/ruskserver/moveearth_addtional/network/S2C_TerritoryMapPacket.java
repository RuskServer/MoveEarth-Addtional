package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Compact public territory snapshot for client-side map overlays. */
public record S2C_TerritoryMapPacket(List<NationEntry> nations, List<CoreEntry> cores)
        implements CustomPacketPayload {
    private static final int MAX_NATIONS = 512;
    private static final int MAX_CORES = 4096;

    public static final Type<S2C_TerritoryMapPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "territory_map"));
    public static final StreamCodec<FriendlyByteBuf, S2C_TerritoryMapPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeVarInt(packet.nations.size());
                for (NationEntry nation : packet.nations) {
                    buffer.writeUUID(nation.nationId());
                    buffer.writeUtf(nation.name(), 64);
                    buffer.writeUtf(nation.tag(), 12);
                    buffer.writeByte(nation.relation().ordinal());
                }
                buffer.writeVarInt(packet.cores.size());
                for (CoreEntry core : packet.cores) {
                    buffer.writeUUID(core.nationId());
                    buffer.writeResourceLocation(core.dimension());
                    buffer.writeInt(core.centerChunkX());
                    buffer.writeInt(core.centerChunkZ());
                    buffer.writeByte(core.radius());
                    buffer.writeByte(core.state().ordinal());
                    buffer.writeBoolean(core.capital());
                }
            },
            buffer -> {
                int nationCount = checkedSize(buffer.readVarInt(), MAX_NATIONS, "nation");
                List<NationEntry> nations = new ArrayList<>(nationCount);
                for (int index = 0; index < nationCount; index++) {
                    nations.add(new NationEntry(buffer.readUUID(), buffer.readUtf(64), buffer.readUtf(12),
                            Relation.fromNetworkId(buffer.readUnsignedByte())));
                }
                int coreCount = checkedSize(buffer.readVarInt(), MAX_CORES, "territory core");
                List<CoreEntry> cores = new ArrayList<>(coreCount);
                for (int index = 0; index < coreCount; index++) {
                    cores.add(new CoreEntry(buffer.readUUID(), buffer.readResourceLocation(), buffer.readInt(),
                            buffer.readInt(), buffer.readUnsignedByte(),
                            CoreState.fromNetworkId(buffer.readUnsignedByte()), buffer.readBoolean()));
                }
                return new S2C_TerritoryMapPacket(nations, cores);
            });

    public S2C_TerritoryMapPacket {
        nations = nations == null ? List.of() : List.copyOf(nations);
        cores = cores == null ? List.of() : List.copyOf(cores);
        if (nations.size() > MAX_NATIONS || cores.size() > MAX_CORES) {
            throw new IllegalArgumentException("Territory map snapshot exceeds protocol limits");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() ->
                com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleTerritoryMap(this));
    }

    private static int checkedSize(int size, int maximum, String label) {
        if (size < 0 || size > maximum) throw new IllegalArgumentException("Invalid " + label + " count: " + size);
        return size;
    }

    public record NationEntry(UUID nationId, String name, String tag, Relation relation) {
        public NationEntry {
            if (nationId == null) nationId = new UUID(0L, 0L);
            name = name == null ? "" : name;
            tag = tag == null ? "" : tag;
            if (relation == null) relation = Relation.FOREIGN;
        }
    }

    public record CoreEntry(UUID nationId, ResourceLocation dimension, int centerChunkX, int centerChunkZ,
                            int radius, CoreState state, boolean capital) {
        public CoreEntry {
            if (nationId == null) nationId = new UUID(0L, 0L);
            if (dimension == null) dimension = ResourceLocation.withDefaultNamespace("overworld");
            radius = Math.max(0, Math.min(127, radius));
            if (state == null) state = CoreState.ACTIVE;
        }
    }

    public enum Relation {
        OWN, ALLIED, HOSTILE, FOREIGN;

        static Relation fromNetworkId(int id) {
            return id >= 0 && id < values().length ? values()[id] : FOREIGN;
        }
    }

    public enum CoreState {
        ACTIVE, EXPOSED, FALLEN;

        static CoreState fromNetworkId(int id) {
            return id >= 0 && id < values().length ? values()[id] : ACTIVE;
        }
    }
}
