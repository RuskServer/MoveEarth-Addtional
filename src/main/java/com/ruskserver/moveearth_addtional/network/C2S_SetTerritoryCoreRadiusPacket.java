package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.block.entity.TerritoryCoreBlockEntity;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritoryPreviewArea;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeSavedData;

public record C2S_SetTerritoryCoreRadiusPacket(int requestId, BlockPos pos, int radius)
        implements CustomPacketPayload {
    public static final Type<C2S_SetTerritoryCoreRadiusPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "set_territory_core_radius"));
    public static final StreamCodec<FriendlyByteBuf, C2S_SetTerritoryCoreRadiusPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeVarInt(packet.requestId);
                buffer.writeBlockPos(packet.pos);
                buffer.writeByte(packet.radius);
            },
            buffer -> new C2S_SetTerritoryCoreRadiusPacket(
                    buffer.readVarInt(), buffer.readBlockPos(), buffer.readUnsignedByte()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            NationSavedData data = NationSavedData.get(player.server);
            boolean validRadius = radius >= TerritoryPreviewArea.MIN_RADIUS
                    && radius <= TerritoryPreviewArea.MAX_RADIUS;
            boolean closeEnough = player.blockPosition().distSqr(pos) <= 64.0D;
            TerritoryCoreBlockEntity core = player.level().getBlockEntity(pos)
                    instanceof TerritoryCoreBlockEntity found ? found : null;
            java.util.UUID playerNation = data.nationIdFor(player.getUUID()).orElse(null);
            boolean allowed = core != null && playerNation != null && playerNation.equals(core.nationId())
                    && data.can(player.getUUID(), S2Permission.MANAGE_TERRITORY);
            boolean siegeLocked = core != null && core.coreId() != null
                    && SiegeSavedData.get(player.server).isCoreLocked(core.coreId());
            TerritorySavedData.UpdateResult update = null;
            if (validRadius && closeEnough && allowed && !siegeLocked) {
                TerritorySavedData territories = TerritorySavedData.get(player.server);
                if (territories.core(player.level().dimension().location(), pos).isEmpty()) {
                    TerritorySavedData.RegistrationResult repaired = territories.register(
                            playerNation, core.placedBy() == null ? player.getUUID() : core.placedBy(),
                            player.level().dimension().location(), pos, core.radius());
                    if (repaired.success()) core.bind(repaired.core());
                }
                update = territories.updateRadius(
                        playerNation, player.level().dimension().location(), pos, radius);
            }
            boolean success = update != null && update.success();
            if (success) core.bind(update.core());
            String key = success ? "message.moveearth_addtional.territory_core.saved"
                    : !validRadius ? "message.moveearth_addtional.territory_core.invalid_radius"
                    : !closeEnough ? "message.moveearth_addtional.territory_core.too_far"
                    : !allowed ? "message.moveearth_addtional.territory_core.no_permission"
                    : siegeLocked ? "message.moveearth_addtional.territory_core.siege_locked"
                    : update != null && update.status() == TerritorySavedData.Status.FOREIGN_TERRITORY_CONFLICT
                    ? "message.moveearth_addtional.territory_core.foreign_conflict"
                    : "message.moveearth_addtional.territory_core.not_registered";
            PacketDistributor.sendToPlayer(player, new S2C_S2ActionResultPacket(
                    requestId, success, data.revision(), key));
        });
    }
}
