package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.client.ClientPacketHandler;
import com.ruskserver.moveearth_addtional.s2.technology.TechnologySnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record S2C_TechnologySnapshotPacket(TechnologySnapshot snapshot, boolean openScreen) implements CustomPacketPayload {
    private static final int MAX_NODES = 256;
    private static final int MAX_ENTRIES = 32;
    public static final Type<S2C_TechnologySnapshotPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "technology_snapshot"));
    public static final StreamCodec<FriendlyByteBuf, S2C_TechnologySnapshotPacket> STREAM_CODEC = StreamCodec.of(
            S2C_TechnologySnapshotPacket::encode, S2C_TechnologySnapshotPacket::decode);

    private static void encode(FriendlyByteBuf buffer, S2C_TechnologySnapshotPacket packet) {
        TechnologySnapshot value = packet.snapshot;
        buffer.writeBoolean(packet.openScreen);
        buffer.writeLong(value.revision()); buffer.writeBoolean(value.member()); buffer.writeUtf(value.nationName(), 80);
        buffer.writeVarInt(Math.min(MAX_NODES, value.nodes().size()));
        for (TechnologySnapshot.Node node : value.nodes().stream().limit(MAX_NODES).toList()) {
            buffer.writeResourceLocation(node.id()); buffer.writeByte(node.scope().ordinal()); buffer.writeUtf(node.category(), 48);
            buffer.writeUtf(node.chapter(), 48); buffer.writeUtf(node.titleKey(), 160); buffer.writeUtf(node.descriptionKey(), 160);
            buffer.writeResourceLocation(node.icon());
            buffer.writeVarInt(node.x()); buffer.writeVarInt(node.y()); buffer.writeByte(node.state().ordinal()); buffer.writeBoolean(node.tracked());
            buffer.writeVarInt(Math.min(MAX_ENTRIES, node.objectives().size()));
            for (var objective : node.objectives().stream().limit(MAX_ENTRIES).toList()) {
                buffer.writeUtf(objective.descriptionKey(), 160); buffer.writeVarLong(objective.current()); buffer.writeVarLong(objective.required());
            }
            buffer.writeByte(node.prerequisiteMode().ordinal());
            buffer.writeVarInt(Math.min(MAX_ENTRIES, node.prerequisites().size()));
            for (var id : node.prerequisites().stream().limit(MAX_ENTRIES).toList()) buffer.writeResourceLocation(id);
            writeStrings(buffer, node.unlocks());
            buffer.writeVarInt(Math.min(MAX_ENTRIES, node.jeiItems().size()));
            for (var id : node.jeiItems().stream().limit(MAX_ENTRIES).toList()) buffer.writeResourceLocation(id);
        }
    }

    private static S2C_TechnologySnapshotPacket decode(FriendlyByteBuf buffer) {
        boolean openScreen = buffer.readBoolean();
        long revision = buffer.readLong(); boolean member = buffer.readBoolean(); String nation = buffer.readUtf(80);
        int count = bounded(buffer.readVarInt(), MAX_NODES, "node"); List<TechnologySnapshot.Node> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ResourceLocation id = buffer.readResourceLocation();
            int scopeId = buffer.readUnsignedByte();
            if (scopeId >= com.ruskserver.moveearth_addtional.s2.technology.TechnologyDefinition.Scope.values().length) throw new IllegalArgumentException("Invalid technology scope");
            String category = buffer.readUtf(48); String chapter = buffer.readUtf(48);
            String title = buffer.readUtf(160); String description = buffer.readUtf(160);
            ResourceLocation icon = buffer.readResourceLocation();
            int x = buffer.readVarInt(); int y = buffer.readVarInt();
            int stateId = buffer.readUnsignedByte();
            if (stateId >= TechnologySnapshot.State.values().length) throw new IllegalArgumentException("Invalid technology state");
            boolean tracked = buffer.readBoolean();
            int objectiveCount = bounded(buffer.readVarInt(), MAX_ENTRIES, "objective");
            List<TechnologySnapshot.Objective> objectives = new ArrayList<>(objectiveCount);
            for (int j = 0; j < objectiveCount; j++) objectives.add(new TechnologySnapshot.Objective(
                    buffer.readUtf(160), buffer.readVarLong(), buffer.readVarLong()));
            int prerequisiteModeId = buffer.readUnsignedByte();
            if (prerequisiteModeId >= com.ruskserver.moveearth_addtional.s2.technology.TechnologyDefinition.PrerequisiteMode.values().length) throw new IllegalArgumentException("Invalid prerequisite mode");
            int prerequisiteCount = bounded(buffer.readVarInt(), MAX_ENTRIES, "prerequisite");
            List<ResourceLocation> prerequisites = new ArrayList<>(prerequisiteCount);
            for (int j = 0; j < prerequisiteCount; j++) prerequisites.add(buffer.readResourceLocation());
            List<String> unlocks = readStrings(buffer);
            int itemCount = bounded(buffer.readVarInt(), MAX_ENTRIES, "JEI item"); List<ResourceLocation> items = new ArrayList<>(itemCount);
            for (int j = 0; j < itemCount; j++) items.add(buffer.readResourceLocation());
            nodes.add(new TechnologySnapshot.Node(id, com.ruskserver.moveearth_addtional.s2.technology.TechnologyDefinition.Scope.values()[scopeId], category, chapter, title, description, icon, x, y,
                    TechnologySnapshot.State.values()[stateId], tracked, List.copyOf(objectives), com.ruskserver.moveearth_addtional.s2.technology.TechnologyDefinition.PrerequisiteMode.values()[prerequisiteModeId],
                    List.copyOf(prerequisites), unlocks, List.copyOf(items)));
        }
        return new S2C_TechnologySnapshotPacket(new TechnologySnapshot(revision, member, nation, List.copyOf(nodes)), openScreen);
    }

    private static void writeStrings(FriendlyByteBuf buffer, List<String> strings) {
        buffer.writeVarInt(Math.min(MAX_ENTRIES, strings.size()));
        strings.stream().limit(MAX_ENTRIES).forEach(value -> buffer.writeUtf(value, 160));
    }
    private static List<String> readStrings(FriendlyByteBuf buffer) {
        int count = bounded(buffer.readVarInt(), MAX_ENTRIES, "string"); List<String> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) values.add(buffer.readUtf(160)); return List.copyOf(values);
    }
    private static int bounded(int value, int maximum, String name) {
        if (value < 0 || value > maximum) throw new IllegalArgumentException("Invalid " + name + " count: " + value); return value;
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandler.handleTechnologySnapshot(this));
    }
}
