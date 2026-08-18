package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.pvp.PvpRewardData;
import com.ruskserver.moveearth_addtional.pvp.PvpMatchManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record C2S_ExchangeWeaponCratePacket() implements CustomPacketPayload {
    public static final Type<C2S_ExchangeWeaponCratePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_crate_exchange"));
    public static final StreamCodec<FriendlyByteBuf, C2S_ExchangeWeaponCratePacket> STREAM_CODEC = StreamCodec.unit(new C2S_ExchangeWeaponCratePacket());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (PvpMatchManager.INSTANCE.isActive(player)) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("試合中は武器箱を交換できません。"));
                return;
            }
            PvpRewardData rewards = PvpRewardData.get(player.server);
            if (!rewards.exchangeCrate(player))
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("100ポイントと空きスロットが必要です。"));
            else player.sendSystemMessage(net.minecraft.network.chat.Component.literal("武器箱を交換しました。"));
        });
    }
}
