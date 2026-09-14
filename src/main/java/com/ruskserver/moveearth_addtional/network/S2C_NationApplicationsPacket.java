package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record S2C_NationApplicationsPacket(long revision, List<Entry> applications,
                                           String messageKey, boolean success) implements CustomPacketPayload {
    private static final int MAX_APPLICATIONS = 512;
    public static final Type<S2C_NationApplicationsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "nation_applications"));
    public static final StreamCodec<FriendlyByteBuf, S2C_NationApplicationsPacket> STREAM_CODEC = StreamCodec.of(
            S2C_NationApplicationsPacket::encode, S2C_NationApplicationsPacket::decode);

    private static void encode(FriendlyByteBuf buffer, S2C_NationApplicationsPacket packet) {
        buffer.writeLong(packet.revision);
        int count = Math.min(MAX_APPLICATIONS, packet.applications.size());
        buffer.writeVarInt(count);
        for (int index = 0; index < count; index++) {
            Entry entry = packet.applications.get(index);
            buffer.writeUUID(entry.playerId);
            buffer.writeUtf(entry.playerName, 16);
            buffer.writeLong(entry.requestedAt);
            buffer.writeBoolean(entry.online);
        }
        buffer.writeUtf(packet.messageKey, 128);
        buffer.writeBoolean(packet.success);
    }

    private static S2C_NationApplicationsPacket decode(FriendlyByteBuf buffer) {
        long revision = buffer.readLong();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_APPLICATIONS) {
            throw new IllegalArgumentException("Invalid application count: " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new Entry(buffer.readUUID(), buffer.readUtf(16), buffer.readLong(), buffer.readBoolean()));
        }
        return new S2C_NationApplicationsPacket(revision, List.copyOf(entries),
                buffer.readUtf(128), buffer.readBoolean());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandler.handleNationApplications(this));
    }

    public record Entry(UUID playerId, String playerName, long requestedAt, boolean online) { }
}
