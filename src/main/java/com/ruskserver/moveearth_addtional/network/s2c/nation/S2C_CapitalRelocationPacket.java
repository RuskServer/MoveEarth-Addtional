package com.ruskserver.moveearth_addtional.network.s2c.nation;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.territory.CapitalRelocationView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2C_CapitalRelocationPacket(CapitalRelocationView view) implements CustomPacketPayload {
    public static final Type<S2C_CapitalRelocationPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            Moveearth_addtional.MODID, "capital_relocation_view"));
    public static final StreamCodec<FriendlyByteBuf, S2C_CapitalRelocationPacket> STREAM_CODEC = StreamCodec.of((b, p) -> {
        var v = p.view;
        b.writeVarInt(v.requestId()); b.writeLong(v.revision()); b.writeUUID(v.capitalId());
        b.writeLong(v.remainingTicks()); b.writeLong(v.cooldownTicks()); b.writeBoolean(v.allowed()); b.writeUtf(v.result(), 128);
        b.writeVarInt(v.cores().size());
        for (var c : v.cores()) {
            b.writeUUID(c.id()); b.writeUtf(c.dimension(), 256); b.writeInt(c.x()); b.writeInt(c.y());
            b.writeInt(c.z()); b.writeVarInt(c.radius()); b.writeBoolean(c.capital()); b.writeUtf(c.reason(), 128);
        }
    }, b -> {
        int requestId = b.readVarInt(); long revision = b.readLong(); var capitalId = b.readUUID();
        long remaining = b.readLong(); long cooldown = b.readLong(); boolean allowed = b.readBoolean(); String result = b.readUtf(128);
        int size = b.readVarInt();
        if (size < 0 || size > CapitalRelocationView.MAX_CORES) throw new IllegalArgumentException("Invalid capital candidate count");
        var cores = new java.util.ArrayList<CapitalRelocationView.Core>(size);
        for (int i = 0; i < size; i++) cores.add(new CapitalRelocationView.Core(b.readUUID(), b.readUtf(256),
                b.readInt(), b.readInt(), b.readInt(), b.readVarInt(), b.readBoolean(), b.readUtf(128)));
        return new S2C_CapitalRelocationPacket(new CapitalRelocationView(requestId, revision, capitalId, remaining, cooldown, allowed, result, cores));
    });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.NationCapitalScreen.receive(view));
    }
}
