package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.pvp.PvpReplayFrame;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record S2C_KillcamReplayPacket(
        UUID killerId,
        String killerName,
        UUID victimId,
        String victimName,
        List<PvpReplayFrame> killerFrames,
        List<PvpReplayFrame> victimFrames,
        float killerHealth,
        float killerMaxHealth,
        String weaponName,
        List<String> attachments,
        float distance,
        boolean isHeadshot,
        int killerStreak,
        int totalRespawnTicks
) implements CustomPacketPayload {
    public static final Type<S2C_KillcamReplayPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_killcam_replay"));
    public static final StreamCodec<FriendlyByteBuf, S2C_KillcamReplayPacket> STREAM_CODEC = StreamCodec.of(
            S2C_KillcamReplayPacket::encode, S2C_KillcamReplayPacket::decode);

    private static void encode(FriendlyByteBuf buffer, S2C_KillcamReplayPacket packet) {
        buffer.writeUUID(packet.killerId);
        buffer.writeUtf(packet.killerName, 64);
        buffer.writeUUID(packet.victimId);
        buffer.writeUtf(packet.victimName, 64);

        buffer.writeVarInt(packet.killerFrames.size());
        for (PvpReplayFrame frame : packet.killerFrames) frame.write(buffer);

        buffer.writeVarInt(packet.victimFrames.size());
        for (PvpReplayFrame frame : packet.victimFrames) frame.write(buffer);

        buffer.writeFloat(packet.killerHealth);
        buffer.writeFloat(packet.killerMaxHealth);
        buffer.writeUtf(packet.weaponName, 64);

        buffer.writeVarInt(packet.attachments.size());
        for (String att : packet.attachments) buffer.writeUtf(att, 64);

        buffer.writeFloat(packet.distance);
        buffer.writeBoolean(packet.isHeadshot);
        buffer.writeVarInt(packet.killerStreak);
        buffer.writeVarInt(packet.totalRespawnTicks);
    }

    private static S2C_KillcamReplayPacket decode(FriendlyByteBuf buffer) {
        UUID killerId = buffer.readUUID();
        String killerName = buffer.readUtf(64);
        UUID victimId = buffer.readUUID();
        String victimName = buffer.readUtf(64);

        int kSize = buffer.readVarInt();
        List<PvpReplayFrame> killerFrames = new ArrayList<>(kSize);
        for (int i = 0; i < kSize; i++) killerFrames.add(PvpReplayFrame.read(buffer));

        int vSize = buffer.readVarInt();
        List<PvpReplayFrame> victimFrames = new ArrayList<>(vSize);
        for (int i = 0; i < vSize; i++) victimFrames.add(PvpReplayFrame.read(buffer));

        float killerHealth = buffer.readFloat();
        float killerMaxHealth = buffer.readFloat();
        String weaponName = buffer.readUtf(64);

        int attSize = buffer.readVarInt();
        List<String> attachments = new ArrayList<>(attSize);
        for (int i = 0; i < attSize; i++) attachments.add(buffer.readUtf(64));

        float distance = buffer.readFloat();
        boolean isHeadshot = buffer.readBoolean();
        int killerStreak = buffer.readVarInt();
        int totalRespawnTicks = buffer.readVarInt();

        return new S2C_KillcamReplayPacket(
                killerId, killerName, victimId, victimName,
                List.copyOf(killerFrames), List.copyOf(victimFrames),
                killerHealth, killerMaxHealth, weaponName, List.copyOf(attachments),
                distance, isHeadshot, killerStreak, totalRespawnTicks
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleKillcamReplay(this));
    }
}
