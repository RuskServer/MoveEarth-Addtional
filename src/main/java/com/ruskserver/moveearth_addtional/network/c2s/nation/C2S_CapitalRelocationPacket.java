package com.ruskserver.moveearth_addtional.network.c2s.nation;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.s2c.nation.S2C_CapitalRelocationPacket;
import com.ruskserver.moveearth_addtional.s2.territory.CapitalRelocationService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import java.util.UUID;

public record C2S_CapitalRelocationPacket(int requestId, long revision, boolean commit, UUID capitalId, UUID targetId)
        implements CustomPacketPayload {
    public static final Type<C2S_CapitalRelocationPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            Moveearth_addtional.MODID, "capital_relocation_request"));
    public static final StreamCodec<FriendlyByteBuf, C2S_CapitalRelocationPacket> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeVarInt(p.requestId); b.writeLong(p.revision); b.writeBoolean(p.commit);
                b.writeUUID(p.capitalId); b.writeUUID(p.targetId); },
            b -> new C2S_CapitalRelocationPacket(b.readVarInt(), b.readLong(), b.readBoolean(), b.readUUID(), b.readUUID()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !NetworkRegistry.hasChannel(player.connection, S2C_CapitalRelocationPacket.TYPE.id())) return;
            var result = commit ? CapitalRelocationService.relocate(player, revision, capitalId, targetId) : null;
            PacketDistributor.sendToPlayer(player, new S2C_CapitalRelocationPacket(
                    CapitalRelocationService.view(player, requestId, result)));
        });
    }
}
