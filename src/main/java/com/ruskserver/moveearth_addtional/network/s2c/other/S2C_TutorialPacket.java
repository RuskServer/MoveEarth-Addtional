package com.ruskserver.moveearth_addtional.network.s2c.other;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The player's current tutorial goal for the HUD under the balance, or
 * {@link #hidden()} once it is finished, skipped or not yet started.
 *
 * @param step   zero-based index of the current step
 * @param textId translation id of the step's wording
 * @param icon   item id drawn beside the goal, or empty
 */
public record S2C_TutorialPacket(boolean visible, int step, int total, String textId, String icon)
        implements CustomPacketPayload {
    public static final Type<S2C_TutorialPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "tutorial"));
    public static final StreamCodec<FriendlyByteBuf, S2C_TutorialPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeBoolean(packet.visible);
                buffer.writeVarInt(packet.step);
                buffer.writeVarInt(packet.total);
                buffer.writeUtf(packet.textId, 64);
                buffer.writeUtf(packet.icon, 128);
            },
            buffer -> new S2C_TutorialPacket(buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readUtf(64), buffer.readUtf(128)));

    public static S2C_TutorialPacket hidden() {
        return new S2C_TutorialPacket(false, 0, 0, "", "");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.TutorialHud.update(this));
    }
}
