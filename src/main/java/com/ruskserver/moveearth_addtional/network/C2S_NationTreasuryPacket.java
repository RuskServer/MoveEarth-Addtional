package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.territory.NationUpkeepService;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.BankReference;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record C2S_NationTreasuryPacket(Action action, BankReference account) implements CustomPacketPayload {
    public static final Type<C2S_NationTreasuryPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "nation_treasury_action"));
    public static final StreamCodec<FriendlyByteBuf, C2S_NationTreasuryPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeByte(packet.action.ordinal());
                buffer.writeBoolean(packet.account != null);
                if (packet.account != null) packet.account.encode(buffer);
            }, buffer -> new C2S_NationTreasuryPacket(Action.fromNetworkId(buffer.readUnsignedByte()),
                    buffer.readBoolean() ? BankReference.decode(buffer) : null));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            switch (action) {
                case OPEN -> NationUpkeepService.sendScreen(player);
                case SAVE_ENABLE -> {
                    NationUpkeepService.configure(player, account, true);
                    NationUpkeepService.sendScreen(player);
                }
                case DISABLE -> {
                    NationUpkeepService.configure(player, account, false);
                    NationUpkeepService.sendScreen(player);
                }
                case PAY_NOW -> {
                    NationUpkeepService.payNow(player);
                    NationUpkeepService.sendScreen(player);
                }
            }
        });
    }

    public enum Action {
        OPEN, SAVE_ENABLE, DISABLE, PAY_NOW;
        private static Action fromNetworkId(int id) {
            return id >= 0 && id < values().length ? values()[id] : OPEN;
        }
    }
}
