package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.BankReference;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import com.ruskserver.moveearth_addtional.s2.territory.UpkeepPenalty;

public record S2C_NationTreasuryPacket(boolean canManage, long upkeep, boolean enabled,
                                       long nextDueAt, int failedPayments, long overdueSince,
                                       UpkeepPenalty penalty, int selectedAccount,
                                       List<BankReference> accounts, List<String> accountNames)
        implements CustomPacketPayload {
    public static final Type<S2C_NationTreasuryPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "nation_treasury"));
    public static final StreamCodec<FriendlyByteBuf, S2C_NationTreasuryPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeBoolean(packet.canManage);
                buffer.writeVarLong(Math.max(0L, packet.upkeep));
                buffer.writeBoolean(packet.enabled);
                buffer.writeLong(packet.nextDueAt);
                buffer.writeVarInt(packet.failedPayments);
                buffer.writeLong(packet.overdueSince);
                buffer.writeEnum(packet.penalty);
                buffer.writeVarInt(packet.selectedAccount + 1);
                buffer.writeCollection(packet.accounts, (buf, reference) -> reference.encode(buf));
                buffer.writeCollection(packet.accountNames, (buf, name) -> buf.writeUtf(name, 80));
            },
            buffer -> new S2C_NationTreasuryPacket(buffer.readBoolean(), buffer.readVarLong(),
                    buffer.readBoolean(), buffer.readLong(), buffer.readVarInt(), buffer.readLong(),
                    buffer.readEnum(UpkeepPenalty.class), buffer.readVarInt() - 1,
                    buffer.readCollection(ArrayList::new, BankReference::decode),
                    buffer.readCollection(ArrayList::new, buf -> buf.readUtf(80))));

    public S2C_NationTreasuryPacket {
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
        accountNames = accountNames == null ? List.of() : List.copyOf(accountNames);
        if (penalty == null) penalty = UpkeepPenalty.CURRENT;
        overdueSince = Math.max(0L, overdueSince);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleNationTreasury(this));
    }
}
