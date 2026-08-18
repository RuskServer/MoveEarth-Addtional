package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.BankReference;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record S2C_SyncDetectorPaymentPacket(
        BlockPos pos,
        boolean isActive,
        long nextPaymentTime,
        long placedTime,
        BankReference currentReference,
        List<BankReference> availableAccounts,
        List<String> availableAccountNames
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2C_SyncDetectorPaymentPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "sync_detector_payment"));

    public static final StreamCodec<FriendlyByteBuf, S2C_SyncDetectorPaymentPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.pos());
                buf.writeBoolean(packet.isActive());
                buf.writeLong(packet.nextPaymentTime());
                buf.writeLong(packet.placedTime());
                
                // currentReference (Nullable)
                buf.writeBoolean(packet.currentReference() != null);
                if (packet.currentReference() != null) {
                    packet.currentReference().encode(buf);
                }
                
                // availableAccounts
                buf.writeCollection(packet.availableAccounts(), (b, ref) -> {
                    b.writeBoolean(ref != null);
                    if (ref != null) {
                        ref.encode(b);
                    }
                });

                // BankReference#get() cannot resolve server-owned account data on a
                // dedicated client, so display names are resolved on the server.
                buf.writeCollection(packet.availableAccountNames(), FriendlyByteBuf::writeUtf);
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                boolean isActive = buf.readBoolean();
                long nextPaymentTime = buf.readLong();
                long placedTime = buf.readLong();
                
                BankReference currentReference = null;
                if (buf.readBoolean()) {
                    currentReference = BankReference.decode(buf);
                }
                
                List<BankReference> availableAccounts = buf.readCollection(ArrayList::new, b -> {
                    if (b.readBoolean()) {
                        return BankReference.decode(b);
                    }
                    return null;
                });

                List<String> availableAccountNames = buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf);

                return new S2C_SyncDetectorPaymentPacket(pos, isActive, nextPaymentTime, placedTime,
                        currentReference, availableAccounts, availableAccountNames);
            }
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleSyncDetectorPayment(this);
        });
    }
}
