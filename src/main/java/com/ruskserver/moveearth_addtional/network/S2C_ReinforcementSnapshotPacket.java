package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record S2C_ReinforcementSnapshotPacket(ResourceLocation dimension, boolean allowed, List<Entry> entries)
        implements CustomPacketPayload {
    private static final int MAX_ENTRIES = 8192;
    public static final Type<S2C_ReinforcementSnapshotPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "reinforcement_snapshot"));
    public static final StreamCodec<FriendlyByteBuf, S2C_ReinforcementSnapshotPacket> STREAM_CODEC = StreamCodec.of(
            S2C_ReinforcementSnapshotPacket::encode, S2C_ReinforcementSnapshotPacket::decode);

    public S2C_ReinforcementSnapshotPacket {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    private static void encode(FriendlyByteBuf buffer, S2C_ReinforcementSnapshotPacket packet) {
        buffer.writeResourceLocation(packet.dimension);
        buffer.writeBoolean(packet.allowed);
        int count = Math.min(MAX_ENTRIES, packet.entries.size());
        buffer.writeVarInt(count);
        for (int index = 0; index < count; index++) {
            Entry entry = packet.entries.get(index);
            buffer.writeBlockPos(entry.pos);
            buffer.writeByte(entry.material.ordinal());
            buffer.writeVarInt(entry.durability);
            buffer.writeBoolean(entry.enabled);
            buffer.writeVarInt(entry.activationTicksRemaining);
            buffer.writeBoolean(entry.constructionInProgress);
        }
    }

    private static S2C_ReinforcementSnapshotPacket decode(FriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        boolean allowed = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid reinforcement entry count: " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            BlockPos pos = buffer.readBlockPos();
            int materialId = buffer.readUnsignedByte();
            ReinforcementMaterial[] materials = ReinforcementMaterial.values();
            ReinforcementMaterial material = materialId < materials.length
                    ? materials[materialId] : ReinforcementMaterial.COBBLESTONE;
            entries.add(new Entry(pos, material, buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt(),
                    buffer.readBoolean()));
        }
        return new S2C_ReinforcementSnapshotPacket(dimension, allowed, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() ->
                com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleReinforcementSnapshot(this));
    }

    public record Entry(BlockPos pos, ReinforcementMaterial material, int durability, boolean enabled,
                        int activationTicksRemaining, boolean constructionInProgress) {
    }
}
