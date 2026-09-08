package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2C_SyncOxygenPacket(
        float oxygenPercent,
        float filterPercent,
        boolean hasGasMask,
        boolean isDangerZone,
        boolean isExtremeZone,
        float consumptionRate,
        boolean isSprinting,
        boolean isMining,
        boolean isCombat
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<S2C_SyncOxygenPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "sync_oxygen"));

    public static final StreamCodec<FriendlyByteBuf, S2C_SyncOxygenPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.write(buf),
            S2C_SyncOxygenPacket::new
    );

    public S2C_SyncOxygenPacket(FriendlyByteBuf buf) {
        this(
                buf.readFloat(),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean()
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeFloat(this.oxygenPercent);
        buf.writeFloat(this.filterPercent);
        buf.writeBoolean(this.hasGasMask);
        buf.writeBoolean(this.isDangerZone);
        buf.writeBoolean(this.isExtremeZone);
        buf.writeFloat(this.consumptionRate);
        buf.writeBoolean(this.isSprinting);
        buf.writeBoolean(this.isMining);
        buf.writeBoolean(this.isCombat);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleOxygenSync(this);
        });
    }
}
