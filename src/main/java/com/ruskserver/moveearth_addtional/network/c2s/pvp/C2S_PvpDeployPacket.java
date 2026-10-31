package com.ruskserver.moveearth_addtional.network.c2s.pvp;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.pvp.PvpMatchManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * The deploy screen's choice of point and loadout; {@code now} when the player pressed deploy, otherwise only
 * remembered for the automatic deploy.
 */
public record C2S_PvpDeployPacket(long pointId, String loadoutId, boolean now) implements CustomPacketPayload {
    public static final Type<C2S_PvpDeployPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_deploy_choice"));
    public static final StreamCodec<FriendlyByteBuf, C2S_PvpDeployPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeLong(p.pointId);
                buf.writeUtf(p.loadoutId, 64);
                buf.writeBoolean(p.now);
            },
            buf -> new C2S_PvpDeployPacket(buf.readLong(), buf.readUtf(64), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PvpMatchManager.INSTANCE.requestDeploy(player, pointId, loadoutId, now);
            }
        });
    }
}
