package com.ruskserver.moveearth_addtional.network.c2s.pvp;

import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.pvp.reward.PvpExchange;
import com.ruskserver.moveearth_addtional.pvp.reward.PvpRewardData;
import com.ruskserver.moveearth_addtional.pvp.PvpMatchManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Spends PvP points on one of {@link PvpExchange}'s rewards. */
public record C2S_PvpExchangePacket(int exchange) implements CustomPacketPayload {
    public static final Type<C2S_PvpExchangePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_exchange"));
    public static final StreamCodec<FriendlyByteBuf, C2S_PvpExchangePacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeVarInt(packet.exchange),
            buf -> new C2S_PvpExchangePacket(buf.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            PvpExchange chosen = PvpExchange.byOrdinal(exchange);
            if (chosen == null) return;
            if (PvpMatchManager.INSTANCE.isActive(player)) {
                player.sendSystemMessage(MoveEarthMessage.error("試合中はポイント交換できません。"));
                return;
            }
            PvpRewardData rewards = PvpRewardData.get(player.server);
            if (!rewards.exchange(player, chosen))
                player.sendSystemMessage(MoveEarthMessage.error(chosen.cost() + "ポイントが必要です。"));
            else player.sendSystemMessage(MoveEarthMessage.success(chosen.displayName() + "と交換しました。"));
        });
    }
}
