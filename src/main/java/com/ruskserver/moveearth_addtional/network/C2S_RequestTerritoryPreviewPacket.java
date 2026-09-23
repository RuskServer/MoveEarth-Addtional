package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.block.entity.TerritoryCoreBlockEntity;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritoryPreviewArea;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public record C2S_RequestTerritoryPreviewPacket(int radius, boolean coreBound, BlockPos corePos)
        implements CustomPacketPayload {
    private static final BlockPos NO_CORE = BlockPos.ZERO;

    public C2S_RequestTerritoryPreviewPacket(int radius) {
        this(radius, false, NO_CORE);
    }

    public C2S_RequestTerritoryPreviewPacket(int radius, BlockPos corePos) {
        this(radius, true, corePos);
    }
    public static final Type<C2S_RequestTerritoryPreviewPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "territory_preview_request"));
    public static final StreamCodec<FriendlyByteBuf, C2S_RequestTerritoryPreviewPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeByte(packet.radius);
                buffer.writeBoolean(packet.coreBound);
                if (packet.coreBound) buffer.writeBlockPos(packet.corePos);
            },
            buffer -> {
                int radius = buffer.readByte();
                boolean coreBound = buffer.readBoolean();
                return new C2S_RequestTerritoryPreviewPacket(radius, coreBound,
                        coreBound ? buffer.readBlockPos() : NO_CORE);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            int safeRadius = Math.max(TerritoryPreviewArea.MIN_RADIUS,
                    Math.min(TerritoryPreviewArea.MAX_RADIUS, radius));
            net.minecraft.world.level.ChunkPos chunk = player.chunkPosition();
            if (coreBound) {
                NationSavedData data = NationSavedData.get(player.server);
                TerritoryCoreBlockEntity core = player.level().getBlockEntity(corePos)
                        instanceof TerritoryCoreBlockEntity found ? found : null;
                java.util.UUID playerNation = data.nationIdFor(player.getUUID()).orElse(null);
                if (core == null || playerNation == null || !playerNation.equals(core.nationId())
                        || !data.can(player.getUUID(), S2Permission.MANAGE_TERRITORY)
                        || player.blockPosition().distSqr(corePos) > 64.0D) return;
                chunk = new net.minecraft.world.level.ChunkPos(corePos);
            }
            TerritoryPreviewArea area = new TerritoryPreviewArea(chunk.x, chunk.z, safeRadius);
            com.ruskserver.moveearth_addtional.s2.technology.NationTechnologySavedData.get(player.server)
                    .recordAction(player, "territory_previewed");
            com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(player,
                    com.ruskserver.moveearth_addtional.advancement.ModCriteria.TERRITORY_VIEWED);
            PacketDistributor.sendToPlayer(player, new S2C_TerritoryPreviewPacket(
                    player.level().dimension().location(), area.centerChunkX(), area.centerChunkZ(),
                    area.radius(), area.chunkCount()));
        });
    }
}
