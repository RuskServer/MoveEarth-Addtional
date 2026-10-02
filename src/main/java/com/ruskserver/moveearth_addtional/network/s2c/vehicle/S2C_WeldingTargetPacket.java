package com.ruskserver.moveearth_addtional.network.s2c.vehicle;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.siege.CoreSabotagePrompt;
import com.ruskserver.moveearth_addtional.s2.territory.ConfiguringReservationPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * What the server resolved for the block the player is aiming at; the client uses it only for that block.
 * {@code reservationMinutes} is the open time left on the aimer's configuring reservation there: 0 once it
 * has lapsed, {@link ConfiguringReservationPolicy#NO_RESERVATION} where none applies.
 */
public record S2C_WeldingTargetPacket(BlockPos target, boolean reinforceable, CoreSabotagePrompt prompt,
                                      int reservationMinutes)
        implements CustomPacketPayload {
    public static final S2C_WeldingTargetPacket NONE = new S2C_WeldingTargetPacket(null, false, CoreSabotagePrompt.NONE,
            ConfiguringReservationPolicy.NO_RESERVATION);
    public static final Type<S2C_WeldingTargetPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "welding_target"));
    public static final StreamCodec<FriendlyByteBuf, S2C_WeldingTargetPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeBoolean(packet.target != null);
                if (packet.target != null) buffer.writeBlockPos(packet.target);
                buffer.writeBoolean(packet.reinforceable);
                buffer.writeEnum(packet.prompt.kind());
                buffer.writeVarInt(packet.prompt.progressTicks());
                buffer.writeVarInt(packet.prompt.progressTotal());
                buffer.writeVarInt(packet.prompt.seconds());
                buffer.writeVarInt(packet.prompt.tnt());
                buffer.writeVarInt(packet.reservationMinutes);
            },
            buffer -> {
                BlockPos target = buffer.readBoolean() ? buffer.readBlockPos() : null;
                boolean reinforceable = buffer.readBoolean();
                CoreSabotagePrompt prompt = new CoreSabotagePrompt(
                        buffer.readEnum(CoreSabotagePrompt.Kind.class), buffer.readVarInt(), buffer.readVarInt(),
                        buffer.readVarInt(), buffer.readVarInt());
                return new S2C_WeldingTargetPacket(target, reinforceable, prompt, buffer.readVarInt());
            });

    public S2C_WeldingTargetPacket {
        if (prompt == null) prompt = CoreSabotagePrompt.NONE;
        if (reservationMinutes < ConfiguringReservationPolicy.NO_RESERVATION) {
            reservationMinutes = ConfiguringReservationPolicy.NO_RESERVATION;
        }
        if (target == null) {
            reinforceable = false;
            prompt = CoreSabotagePrompt.NONE;
            reservationMinutes = ConfiguringReservationPolicy.NO_RESERVATION;
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleWeldingTarget(this));
    }
}
