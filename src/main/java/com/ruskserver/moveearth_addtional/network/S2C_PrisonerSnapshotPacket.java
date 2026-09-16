package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record S2C_PrisonerSnapshotPacket(boolean openScreen, int state, String counterpart,
                                         String holdingNation, long remainingTicks,
                                         ResourceLocation jailDimension, BlockPos jailPos,
                                         BlockPos intakePos, IntakeView intake,
                                         List<EntryView> entries, List<CandidateView> candidates)
        implements CustomPacketPayload {
    private static final int MAX_ROWS = 256;
    public static final Type<S2C_PrisonerSnapshotPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "prisoner_snapshot"));
    public static final StreamCodec<FriendlyByteBuf, S2C_PrisonerSnapshotPacket> STREAM_CODEC = StreamCodec.of(
            S2C_PrisonerSnapshotPacket::write, S2C_PrisonerSnapshotPacket::read);

    public S2C_PrisonerSnapshotPacket {
        counterpart = counterpart == null ? "" : counterpart;
        holdingNation = holdingNation == null ? "" : holdingNation;
        remainingTicks = Math.max(0L, remainingTicks);
        intake = intake == null ? IntakeView.NONE : intake;
        entries = entries == null ? List.of() : List.copyOf(entries);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    private static void write(FriendlyByteBuf buffer, S2C_PrisonerSnapshotPacket packet) {
        buffer.writeBoolean(packet.openScreen);
        buffer.writeByte(packet.state);
        buffer.writeUtf(packet.counterpart, 64);
        buffer.writeUtf(packet.holdingNation, 64);
        buffer.writeVarLong(packet.remainingTicks);
        writeOptionalResource(buffer, packet.jailDimension);
        writeOptionalPos(buffer, packet.jailPos);
        writeOptionalPos(buffer, packet.intakePos);
        packet.intake.write(buffer);
        int entryCount = Math.min(MAX_ROWS, packet.entries.size());
        buffer.writeVarInt(entryCount);
        for (int i = 0; i < entryCount; i++) packet.entries.get(i).write(buffer);
        int candidateCount = Math.min(MAX_ROWS, packet.candidates.size());
        buffer.writeVarInt(candidateCount);
        for (int i = 0; i < candidateCount; i++) packet.candidates.get(i).write(buffer);
    }

    private static S2C_PrisonerSnapshotPacket read(FriendlyByteBuf buffer) {
        boolean open = buffer.readBoolean();
        int state = buffer.readUnsignedByte();
        String counterpart = buffer.readUtf(64);
        String holdingNation = buffer.readUtf(64);
        long remaining = buffer.readVarLong();
        ResourceLocation dimension = readOptionalResource(buffer);
        BlockPos jail = readOptionalPos(buffer);
        BlockPos intakePos = readOptionalPos(buffer);
        IntakeView intake = IntakeView.read(buffer);
        int entryCount = Math.min(MAX_ROWS, buffer.readVarInt());
        List<EntryView> entries = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) entries.add(EntryView.read(buffer));
        int candidateCount = Math.min(MAX_ROWS, buffer.readVarInt());
        List<CandidateView> candidates = new ArrayList<>(candidateCount);
        for (int i = 0; i < candidateCount; i++) candidates.add(CandidateView.read(buffer));
        return new S2C_PrisonerSnapshotPacket(open, state, counterpart, holdingNation, remaining,
                dimension, jail, intakePos, intake, entries, candidates);
    }

    private static void writeOptionalPos(FriendlyByteBuf buffer, BlockPos pos) {
        buffer.writeBoolean(pos != null);
        if (pos != null) buffer.writeBlockPos(pos);
    }

    private static BlockPos readOptionalPos(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readBlockPos() : null;
    }

    private static void writeOptionalResource(FriendlyByteBuf buffer, ResourceLocation value) {
        buffer.writeBoolean(value != null);
        if (value != null) buffer.writeResourceLocation(value);
    }

    private static ResourceLocation readOptionalResource(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readResourceLocation() : null;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler
                .handlePrisonerSnapshot(this));
    }

    public record EntryView(UUID playerId, String playerName, int state, boolean heldByViewer,
                            String opponentName, long remainingTicks, boolean canRelease) {
        private void write(FriendlyByteBuf buffer) {
            buffer.writeUUID(playerId);
            buffer.writeUtf(playerName, 32);
            buffer.writeByte(state);
            buffer.writeBoolean(heldByViewer);
            buffer.writeUtf(opponentName, 64);
            buffer.writeVarLong(Math.max(0L, remainingTicks));
            buffer.writeBoolean(canRelease);
        }

        private static EntryView read(FriendlyByteBuf buffer) {
            return new EntryView(buffer.readUUID(), buffer.readUtf(32), buffer.readUnsignedByte(),
                    buffer.readBoolean(), buffer.readUtf(64), buffer.readVarLong(), buffer.readBoolean());
        }
    }

    public record CandidateView(UUID playerId, String playerName) {
        private void write(FriendlyByteBuf buffer) {
            buffer.writeUUID(playerId);
            buffer.writeUtf(playerName, 32);
        }

        private static CandidateView read(FriendlyByteBuf buffer) {
            return new CandidateView(buffer.readUUID(), buffer.readUtf(32));
        }
    }

    public record IntakeView(boolean present, String ownerName, boolean activeTerritory,
                             boolean safeSpace, boolean captivePresent, boolean sameDimension,
                             int distance, boolean canImprison) {
        private static final IntakeView NONE = new IntakeView(false, "", false,
                false, false, false, 0, false);

        private void write(FriendlyByteBuf buffer) {
            buffer.writeBoolean(present);
            buffer.writeUtf(ownerName == null ? "" : ownerName, 64);
            buffer.writeBoolean(activeTerritory);
            buffer.writeBoolean(safeSpace);
            buffer.writeBoolean(captivePresent);
            buffer.writeBoolean(sameDimension);
            buffer.writeVarInt(Math.max(0, distance));
            buffer.writeBoolean(canImprison);
        }

        private static IntakeView read(FriendlyByteBuf buffer) {
            return new IntakeView(buffer.readBoolean(), buffer.readUtf(64), buffer.readBoolean(),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readVarInt(), buffer.readBoolean());
        }
    }
}
