package com.ruskserver.moveearth_addtional.network.s2c.other;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.detector.DetectorNamePolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Everything the player detector's screen shows: sent to open it ({@code open}), and again after a change to
 * refresh it with the outcome in {@code message}.
 *
 * @param owner        "Name [TAG]" of the owning nation, or the owner's name
 * @param nationOwned  whether a nation owns it, so its upkeep comes from the treasury
 * @param canManage    whether this player may rename it and pay to restart it
 * @param balance      the paying account's balance: the treasury, or the owner's own
 * @param upkeepCost   what one upkeep cycle costs, as the server is set
 * @param upkeepCycleHours how long one paid cycle lasts
 */
public record S2C_DetectorStatePacket(
        BlockPos pos,
        boolean open,
        String name,
        String owner,
        boolean nationOwned,
        boolean canManage,
        boolean active,
        long nextPaymentTime,
        long placedTime,
        long balance,
        boolean success,
        String message,
        long upkeepCost,
        int upkeepCycleHours
) implements CustomPacketPayload {
    private static final int MAX_OWNER_LENGTH = 64;
    private static final int MAX_MESSAGE_LENGTH = 160;

    public static final Type<S2C_DetectorStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "detector_state"));

    public static final StreamCodec<FriendlyByteBuf, S2C_DetectorStatePacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.pos);
                buf.writeBoolean(packet.open);
                buf.writeUtf(packet.name, DetectorNamePolicy.MAX_LENGTH);
                buf.writeUtf(packet.owner, MAX_OWNER_LENGTH);
                buf.writeBoolean(packet.nationOwned);
                buf.writeBoolean(packet.canManage);
                buf.writeBoolean(packet.active);
                buf.writeLong(packet.nextPaymentTime);
                buf.writeLong(packet.placedTime);
                buf.writeLong(packet.balance);
                buf.writeBoolean(packet.success);
                buf.writeUtf(packet.message, MAX_MESSAGE_LENGTH);
                buf.writeVarLong(Math.max(0L, packet.upkeepCost));
                buf.writeVarInt(Math.max(1, packet.upkeepCycleHours));
            },
            buf -> new S2C_DetectorStatePacket(buf.readBlockPos(), buf.readBoolean(),
                    buf.readUtf(DetectorNamePolicy.MAX_LENGTH), buf.readUtf(MAX_OWNER_LENGTH), buf.readBoolean(),
                    buf.readBoolean(), buf.readBoolean(), buf.readLong(), buf.readLong(), buf.readLong(),
                    buf.readBoolean(), buf.readUtf(MAX_MESSAGE_LENGTH), buf.readVarLong(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleDetectorState(this));
    }
}
