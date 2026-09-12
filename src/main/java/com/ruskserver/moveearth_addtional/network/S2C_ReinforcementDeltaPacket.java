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

/** Changed reinforcement positions only; periodic snapshots remain the recovery mechanism. */
public record S2C_ReinforcementDeltaPacket(ResourceLocation dimension, List<Entry> upserts,
                                            List<BlockPos> removals) implements CustomPacketPayload {
    private static final int MAX_CHANGES = 8_192;
    public static final Type<S2C_ReinforcementDeltaPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "reinforcement_delta"));
    public static final StreamCodec<FriendlyByteBuf, S2C_ReinforcementDeltaPacket> STREAM_CODEC =
            StreamCodec.of(S2C_ReinforcementDeltaPacket::encode, S2C_ReinforcementDeltaPacket::decode);

    public S2C_ReinforcementDeltaPacket {
        upserts = upserts == null ? List.of() : List.copyOf(upserts);
        removals = removals == null ? List.of() : List.copyOf(removals);
        if (upserts.size() + removals.size() > MAX_CHANGES) {
            throw new IllegalArgumentException("Too many reinforcement changes");
        }
    }

    private static void encode(FriendlyByteBuf buffer, S2C_ReinforcementDeltaPacket packet) {
        buffer.writeResourceLocation(packet.dimension);
        buffer.writeVarInt(packet.upserts.size());
        for (Entry entry : packet.upserts) {
            buffer.writeBlockPos(entry.pos);
            buffer.writeByte(entry.material.ordinal());
            buffer.writeVarInt(entry.durability);
            buffer.writeBoolean(entry.enabled);
            buffer.writeVarInt(entry.activationTicksRemaining);
            buffer.writeBoolean(entry.constructionInProgress);
            buffer.writeBoolean(entry.siegeDisabled);
        }
        buffer.writeVarInt(packet.removals.size());
        for (BlockPos pos : packet.removals) buffer.writeBlockPos(pos);
    }

    private static S2C_ReinforcementDeltaPacket decode(FriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        int upsertCount = checkedCount(buffer.readVarInt());
        List<Entry> upserts = new ArrayList<>(upsertCount);
        ReinforcementMaterial[] materials = ReinforcementMaterial.values();
        for (int index = 0; index < upsertCount; index++) {
            BlockPos pos = buffer.readBlockPos();
            int materialId = buffer.readUnsignedByte();
            ReinforcementMaterial material = materialId < materials.length
                    ? materials[materialId] : ReinforcementMaterial.COBBLESTONE;
            upserts.add(new Entry(pos, material, buffer.readVarInt(), buffer.readBoolean(),
                    buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean()));
        }
        int removalCount = checkedCount(buffer.readVarInt());
        if (upsertCount + removalCount > MAX_CHANGES) {
            throw new IllegalArgumentException("Too many reinforcement changes");
        }
        List<BlockPos> removals = new ArrayList<>(removalCount);
        for (int index = 0; index < removalCount; index++) removals.add(buffer.readBlockPos());
        return new S2C_ReinforcementDeltaPacket(dimension, upserts, removals);
    }

    private static int checkedCount(int count) {
        if (count < 0 || count > MAX_CHANGES) throw new IllegalArgumentException("Invalid change count: " + count);
        return count;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() ->
                com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleReinforcementDelta(this));
    }

    public record Entry(BlockPos pos, ReinforcementMaterial material, int durability, boolean enabled,
                        int activationTicksRemaining, boolean constructionInProgress, boolean siegeDisabled) { }
}
