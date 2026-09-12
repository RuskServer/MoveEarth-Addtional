package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.territory.TerritoryClosureScanner;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record S2C_TerritoryClosurePacket(ResourceLocation dimension, BlockPos corePos, int requestId,
                                         Status status, TerritorySavedData.CoreState coreState,
                                         int visited, List<BlockPos> escapePath,
                                         List<BlockPos> unreinforcedLeakBlocks)
        implements CustomPacketPayload {
    public static final Type<S2C_TerritoryClosurePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "territory_closure"));
    public static final StreamCodec<FriendlyByteBuf, S2C_TerritoryClosurePacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeResourceLocation(packet.dimension);
                buffer.writeBlockPos(packet.corePos);
                buffer.writeVarInt(packet.requestId);
                buffer.writeEnum(packet.status);
                buffer.writeEnum(packet.coreState);
                buffer.writeVarInt(packet.visited);
                int pathSize = Math.min(TerritoryClosureScanner.MAX_PATH, packet.escapePath.size());
                buffer.writeVarInt(pathSize);
                for (int index = 0; index < pathSize; index++) buffer.writeBlockPos(packet.escapePath.get(index));
                int leakSize = Math.min(TerritoryClosureScanner.MAX_PATH, packet.unreinforcedLeakBlocks.size());
                buffer.writeVarInt(leakSize);
                for (int index = 0; index < leakSize; index++) {
                    buffer.writeBlockPos(packet.unreinforcedLeakBlocks.get(index));
                }
            },
            buffer -> {
                ResourceLocation dimension = buffer.readResourceLocation();
                BlockPos corePos = buffer.readBlockPos();
                int requestId = buffer.readVarInt();
                Status status = buffer.readEnum(Status.class);
                TerritorySavedData.CoreState coreState = buffer.readEnum(TerritorySavedData.CoreState.class);
                int visited = buffer.readVarInt();
                int pathSize = buffer.readVarInt();
                if (pathSize < 0 || pathSize > TerritoryClosureScanner.MAX_PATH) {
                    throw new IllegalArgumentException("Invalid territory closure path size: " + pathSize);
                }
                List<BlockPos> path = new ArrayList<>(pathSize);
                for (int index = 0; index < pathSize; index++) path.add(buffer.readBlockPos());
                int leakSize = buffer.readVarInt();
                if (leakSize < 0 || leakSize > TerritoryClosureScanner.MAX_PATH) {
                    throw new IllegalArgumentException("Invalid territory closure leak size: " + leakSize);
                }
                List<BlockPos> leaks = new ArrayList<>(leakSize);
                for (int index = 0; index < leakSize; index++) leaks.add(buffer.readBlockPos());
                return new S2C_TerritoryClosurePacket(
                        dimension, corePos, requestId, status, coreState, visited, path, leaks);
            });

    public S2C_TerritoryClosurePacket {
        escapePath = escapePath == null ? List.of() : List.copyOf(escapePath);
        unreinforcedLeakBlocks = unreinforcedLeakBlocks == null
                ? List.of() : List.copyOf(unreinforcedLeakBlocks);
    }

    public static S2C_TerritoryClosurePacket scanned(ResourceLocation dimension, BlockPos corePos,
                                                      int requestId, TerritoryClosureScanner.Result result,
                                                      TerritorySavedData.CoreState coreState,
                                                      List<BlockPos> unreinforcedLeakBlocks) {
        return new S2C_TerritoryClosurePacket(dimension, corePos, requestId,
                Status.valueOf(result.status().name()), coreState, result.visited(), result.escapePath(),
                unreinforcedLeakBlocks);
    }

    public static S2C_TerritoryClosurePacket rejected(ResourceLocation dimension, BlockPos corePos,
                                                       int requestId, Status status) {
        return new S2C_TerritoryClosurePacket(dimension, corePos, requestId, status,
                TerritorySavedData.CoreState.CONFIGURING, 0, List.of(), List.of());
    }

    public String messageKey() {
        return "screen.moveearth_addtional.territory.closure." + status.name().toLowerCase(Locale.ROOT);
    }

    public boolean success() {
        return status == Status.SEALED;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() ->
                com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleTerritoryClosure(this));
    }

    public enum Status {
        SEALED, OPEN, TOO_SMALL, LIMIT_EXCEEDED, UNLOADED, DENIED, NOT_FOUND, TOO_FAR
    }
}
