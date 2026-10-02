package com.ruskserver.moveearth_addtional.network.s2c.siege;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Asks the attacking player to confirm a first attack on a nation they have no declared hostility or
 * Siege with. The hit that triggered it was already refused; nothing happens until they confirm.
 */
public record S2C_WarConsentPromptPacket(UUID targetNationId, String targetNationName,
                                         int consentMinutes, int failedLockoutMinutes)
        implements CustomPacketPayload {
    public static final Type<S2C_WarConsentPromptPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "war_consent_prompt"));
    public static final StreamCodec<FriendlyByteBuf, S2C_WarConsentPromptPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeUUID(packet.targetNationId());
                buffer.writeUtf(packet.targetNationName(), 64);
                buffer.writeVarInt(packet.consentMinutes());
                buffer.writeVarInt(packet.failedLockoutMinutes());
            },
            buffer -> new S2C_WarConsentPromptPacket(buffer.readUUID(), buffer.readUtf(64),
                    buffer.readVarInt(), buffer.readVarInt()));

    public S2C_WarConsentPromptPacket {
        targetNationName = targetNationName == null ? "?"
                : targetNationName.length() > 64 ? targetNationName.substring(0, 64) : targetNationName;
        consentMinutes = Math.max(0, consentMinutes);
        failedLockoutMinutes = Math.max(0, failedLockoutMinutes);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.WarConsentClient.prompt(this));
    }
}
