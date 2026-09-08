package com.ruskserver.moveearth_addtional.pvp;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record PvpMapDefinition(
        String id,
        String displayName,
        String description,
        BlockPos redSpawn,
        BlockPos blueSpawn,
        List<BlockPos> extraRedSpawns,
        List<BlockPos> extraBlueSpawns,
        BlockPos hillMin,
        BlockPos hillMax,
        int cardColor,
        boolean enabled
) {
    public static final int DEFAULT_COLOR = 0xFF5DCBFF;

    public PvpMapDefinition {
        id = Objects.requireNonNullElse(id, "default").trim().toLowerCase();
        displayName = Objects.requireNonNullElse(displayName, "標準アリーナ");
        description = Objects.requireNonNullElse(description, "");
        redSpawn = redSpawn == null ? BlockPos.ZERO : redSpawn.immutable();
        blueSpawn = blueSpawn == null ? BlockPos.ZERO : blueSpawn.immutable();
        extraRedSpawns = extraRedSpawns == null ? List.of() : List.copyOf(extraRedSpawns);
        extraBlueSpawns = extraBlueSpawns == null ? List.of() : List.copyOf(extraBlueSpawns);
        hillMin = hillMin == null ? BlockPos.ZERO : hillMin.immutable();
        hillMax = hillMax == null ? BlockPos.ZERO : hillMax.immutable();
    }

    public static PvpMapDefinition createDefault() {
        return new PvpMapDefinition(
                "default",
                "標準アリーナ",
                "中央の丘を巡る標準的な対称アリーナマップ",
                new BlockPos(-20, 80, 0),
                new BlockPos(20, 80, 0),
                List.of(),
                List.of(),
                new BlockPos(-4, 76, -4),
                new BlockPos(4, 90, 4),
                DEFAULT_COLOR,
                true
        );
    }

    public List<BlockPos> allRedSpawns() {
        if (extraRedSpawns.isEmpty()) return List.of(redSpawn);
        List<BlockPos> all = new ArrayList<>(1 + extraRedSpawns.size());
        all.add(redSpawn);
        all.addAll(extraRedSpawns);
        return all;
    }

    public List<BlockPos> allBlueSpawns() {
        if (extraBlueSpawns.isEmpty()) return List.of(blueSpawn);
        List<BlockPos> all = new ArrayList<>(1 + extraBlueSpawns.size());
        all.add(blueSpawn);
        all.addAll(extraBlueSpawns);
        return all;
    }

    public boolean isConfigured() {
        return !redSpawn.equals(BlockPos.ZERO)
                && !blueSpawn.equals(BlockPos.ZERO)
                && !hillMin.equals(BlockPos.ZERO)
                && !hillMax.equals(BlockPos.ZERO);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putString("DisplayName", displayName);
        tag.putString("Description", description);
        tag.putLong("RedSpawn", redSpawn.asLong());
        tag.putLong("BlueSpawn", blueSpawn.asLong());

        ListTag extraRed = new ListTag();
        for (BlockPos pos : extraRedSpawns) extraRed.add(LongTag.valueOf(pos.asLong()));
        tag.put("ExtraRedSpawns", extraRed);

        ListTag extraBlue = new ListTag();
        for (BlockPos pos : extraBlueSpawns) extraBlue.add(LongTag.valueOf(pos.asLong()));
        tag.put("ExtraBlueSpawns", extraBlue);

        tag.putLong("HillMin", hillMin.asLong());
        tag.putLong("HillMax", hillMax.asLong());
        tag.putInt("CardColor", cardColor);
        tag.putBoolean("Enabled", enabled);
        return tag;
    }

    public static PvpMapDefinition load(CompoundTag tag) {
        List<BlockPos> extraRed = new ArrayList<>();
        if (tag.contains("ExtraRedSpawns", Tag.TAG_LIST)) {
            ListTag list = tag.getList("ExtraRedSpawns", Tag.TAG_LONG);
            for (int i = 0; i < list.size(); i++) extraRed.add(BlockPos.of(((LongTag) list.get(i)).getAsLong()));
        }

        List<BlockPos> extraBlue = new ArrayList<>();
        if (tag.contains("ExtraBlueSpawns", Tag.TAG_LIST)) {
            ListTag list = tag.getList("ExtraBlueSpawns", Tag.TAG_LONG);
            for (int i = 0; i < list.size(); i++) extraBlue.add(BlockPos.of(((LongTag) list.get(i)).getAsLong()));
        }

        return new PvpMapDefinition(
                tag.getString("Id"),
                tag.getString("DisplayName"),
                tag.getString("Description"),
                tag.contains("RedSpawn") ? BlockPos.of(tag.getLong("RedSpawn")) : BlockPos.ZERO,
                tag.contains("BlueSpawn") ? BlockPos.of(tag.getLong("BlueSpawn")) : BlockPos.ZERO,
                extraRed,
                extraBlue,
                tag.contains("HillMin") ? BlockPos.of(tag.getLong("HillMin")) : BlockPos.ZERO,
                tag.contains("HillMax") ? BlockPos.of(tag.getLong("HillMax")) : BlockPos.ZERO,
                tag.contains("CardColor") ? tag.getInt("CardColor") : DEFAULT_COLOR,
                !tag.contains("Enabled") || tag.getBoolean("Enabled")
        );
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(id, 64);
        buffer.writeUtf(displayName, 64);
        buffer.writeUtf(description, 256);
        buffer.writeBlockPos(redSpawn);
        buffer.writeBlockPos(blueSpawn);

        buffer.writeVarInt(extraRedSpawns.size());
        for (BlockPos pos : extraRedSpawns) buffer.writeBlockPos(pos);

        buffer.writeVarInt(extraBlueSpawns.size());
        for (BlockPos pos : extraBlueSpawns) buffer.writeBlockPos(pos);

        buffer.writeBlockPos(hillMin);
        buffer.writeBlockPos(hillMax);
        buffer.writeInt(cardColor);
        buffer.writeBoolean(enabled);
    }

    public static PvpMapDefinition read(FriendlyByteBuf buffer) {
        String id = buffer.readUtf(64);
        String displayName = buffer.readUtf(64);
        String description = buffer.readUtf(256);
        BlockPos redSpawn = buffer.readBlockPos();
        BlockPos blueSpawn = buffer.readBlockPos();

        int redSize = buffer.readVarInt();
        List<BlockPos> extraRed = new ArrayList<>(redSize);
        for (int i = 0; i < redSize; i++) extraRed.add(buffer.readBlockPos());

        int blueSize = buffer.readVarInt();
        List<BlockPos> extraBlue = new ArrayList<>(blueSize);
        for (int i = 0; i < blueSize; i++) extraBlue.add(buffer.readBlockPos());

        BlockPos hillMin = buffer.readBlockPos();
        BlockPos hillMax = buffer.readBlockPos();
        int cardColor = buffer.readInt();
        boolean enabled = buffer.readBoolean();

        return new PvpMapDefinition(id, displayName, description, redSpawn, blueSpawn, extraRed, extraBlue, hillMin, hillMax, cardColor, enabled);
    }

    public PvpMapDefinition withDisplayName(String name) {
        return new PvpMapDefinition(id, name, description, redSpawn, blueSpawn, extraRedSpawns, extraBlueSpawns, hillMin, hillMax, cardColor, enabled);
    }

    public PvpMapDefinition withDescription(String desc) {
        return new PvpMapDefinition(id, displayName, desc, redSpawn, blueSpawn, extraRedSpawns, extraBlueSpawns, hillMin, hillMax, cardColor, enabled);
    }

    public PvpMapDefinition withRedSpawn(BlockPos pos) {
        return new PvpMapDefinition(id, displayName, description, pos, blueSpawn, extraRedSpawns, extraBlueSpawns, hillMin, hillMax, cardColor, enabled);
    }

    public PvpMapDefinition withBlueSpawn(BlockPos pos) {
        return new PvpMapDefinition(id, displayName, description, redSpawn, pos, extraRedSpawns, extraBlueSpawns, hillMin, hillMax, cardColor, enabled);
    }

    public PvpMapDefinition withAddedRedSpawn(BlockPos pos) {
        List<BlockPos> list = new ArrayList<>(extraRedSpawns);
        list.add(pos.immutable());
        return new PvpMapDefinition(id, displayName, description, redSpawn, blueSpawn, list, extraBlueSpawns, hillMin, hillMax, cardColor, enabled);
    }

    public PvpMapDefinition withAddedBlueSpawn(BlockPos pos) {
        List<BlockPos> list = new ArrayList<>(extraBlueSpawns);
        list.add(pos.immutable());
        return new PvpMapDefinition(id, displayName, description, redSpawn, blueSpawn, extraRedSpawns, list, hillMin, hillMax, cardColor, enabled);
    }

    public PvpMapDefinition withClearedExtraSpawns(boolean clearRed, boolean clearBlue) {
        return new PvpMapDefinition(
                id, displayName, description, redSpawn, blueSpawn,
                clearRed ? List.of() : extraRedSpawns,
                clearBlue ? List.of() : extraBlueSpawns,
                hillMin, hillMax, cardColor, enabled
        );
    }

    public PvpMapDefinition withHillMin(BlockPos pos) {
        return new PvpMapDefinition(id, displayName, description, redSpawn, blueSpawn, extraRedSpawns, extraBlueSpawns, pos, hillMax, cardColor, enabled);
    }

    public PvpMapDefinition withHillMax(BlockPos pos) {
        return new PvpMapDefinition(id, displayName, description, redSpawn, blueSpawn, extraRedSpawns, extraBlueSpawns, hillMin, pos, cardColor, enabled);
    }

    public PvpMapDefinition withEnabled(boolean state) {
        return new PvpMapDefinition(id, displayName, description, redSpawn, blueSpawn, extraRedSpawns, extraBlueSpawns, hillMin, hillMax, cardColor, state);
    }

    public PvpMapDefinition withCardColor(int color) {
        return new PvpMapDefinition(id, displayName, description, redSpawn, blueSpawn, extraRedSpawns, extraBlueSpawns, hillMin, hillMax, color, enabled);
    }
}
