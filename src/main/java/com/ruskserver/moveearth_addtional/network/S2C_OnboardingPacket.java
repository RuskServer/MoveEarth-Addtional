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

public record S2C_OnboardingPacket(long revision, UUID appliedNationId, String appliedNationName,
                                   long requestedAt, boolean searching, List<NationEntry> nations, String messageKey,
                                   boolean success) implements CustomPacketPayload {
    private static final UUID NONE = new UUID(0L, 0L);
    private static final int MAX_NATIONS = 512;
    public static final Type<S2C_OnboardingPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "onboarding"));
    public static final StreamCodec<FriendlyByteBuf, S2C_OnboardingPacket> STREAM_CODEC = StreamCodec.of(
            S2C_OnboardingPacket::encode, S2C_OnboardingPacket::decode);

    private static void encode(FriendlyByteBuf buffer, S2C_OnboardingPacket packet) {
        buffer.writeLong(packet.revision);
        buffer.writeUUID(packet.appliedNationId == null ? NONE : packet.appliedNationId);
        buffer.writeUtf(packet.appliedNationName, 64);
        buffer.writeLong(packet.requestedAt);
        buffer.writeBoolean(packet.searching);
        int count = Math.min(MAX_NATIONS, packet.nations.size());
        buffer.writeVarInt(count);
        for (int index = 0; index < count; index++) {
            NationEntry nation = packet.nations.get(index);
            buffer.writeUUID(nation.id);
            buffer.writeUtf(nation.name, 64);
            buffer.writeUtf(nation.tag, 16);
            buffer.writeVarInt(nation.members);
            buffer.writeVarInt(nation.activeCores);
        }
        buffer.writeUtf(packet.messageKey, 128);
        buffer.writeBoolean(packet.success);
    }

    private static S2C_OnboardingPacket decode(FriendlyByteBuf buffer) {
        long revision = buffer.readLong();
        UUID applied = buffer.readUUID();
        if (NONE.equals(applied)) applied = null;
        String appliedName = buffer.readUtf(64);
        long requestedAt = buffer.readLong();
        boolean searching = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_NATIONS) throw new IllegalArgumentException("Invalid nation count: " + count);
        List<NationEntry> nations = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            nations.add(new NationEntry(buffer.readUUID(), buffer.readUtf(64), buffer.readUtf(16),
                    buffer.readVarInt(), buffer.readVarInt()));
        }
        return new S2C_OnboardingPacket(revision, applied, appliedName, requestedAt, searching, List.copyOf(nations),
                buffer.readUtf(128), buffer.readBoolean());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandler.handleOnboarding(this));
    }

    public record NationEntry(UUID id, String name, String tag, int members, int activeCores) { }
}
