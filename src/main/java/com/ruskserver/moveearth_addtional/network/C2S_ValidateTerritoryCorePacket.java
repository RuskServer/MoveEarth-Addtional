package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.block.entity.TerritoryCoreBlockEntity;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritoryClosureRecheckManager;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public record C2S_ValidateTerritoryCorePacket(int requestId, BlockPos pos) implements CustomPacketPayload {
    public static final Type<C2S_ValidateTerritoryCorePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "validate_territory_core"));
    public static final StreamCodec<FriendlyByteBuf, C2S_ValidateTerritoryCorePacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeVarInt(packet.requestId);
                buffer.writeBlockPos(packet.pos);
            },
            buffer -> new C2S_ValidateTerritoryCorePacket(buffer.readVarInt(), buffer.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            ResourceLocation dimension = level.dimension().location();
            if (player.blockPosition().distSqr(pos) > 64.0D) {
                send(player, S2C_TerritoryClosurePacket.rejected(
                        dimension, pos, requestId, S2C_TerritoryClosurePacket.Status.TOO_FAR));
                return;
            }
            TerritoryCoreBlockEntity core = level.getBlockEntity(pos)
                    instanceof TerritoryCoreBlockEntity found ? found : null;
            TerritorySavedData territories = TerritorySavedData.get(player.server);
            TerritorySavedData.CoreRecord record = territories.core(dimension, pos).orElse(null);
            if (core == null || record == null) {
                send(player, S2C_TerritoryClosurePacket.rejected(
                        dimension, pos, requestId, S2C_TerritoryClosurePacket.Status.NOT_FOUND));
                return;
            }
            NationSavedData nations = NationSavedData.get(player.server);
            UUID nationId = nations.nationIdFor(player.getUUID()).orElse(null);
            if (nationId == null || !nationId.equals(record.nationId()) || !nationId.equals(core.nationId())
                    || !nations.can(player.getUUID(), S2Permission.MANAGE_TERRITORY)) {
                send(player, S2C_TerritoryClosurePacket.rejected(
                        dimension, pos, requestId, S2C_TerritoryClosurePacket.Status.DENIED));
                return;
            }
            TerritoryClosureRecheckManager.requestValidation(player, pos, requestId);
        });
    }

    private static void send(ServerPlayer player, S2C_TerritoryClosurePacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }
}
