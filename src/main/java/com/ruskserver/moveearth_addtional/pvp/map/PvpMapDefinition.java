package com.ruskserver.moveearth_addtional.pvp.map;

import com.ruskserver.moveearth_addtional.pvp.PvpTeam;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        boolean enabled,
        BlockPos boundsCorner1,
        BlockPos boundsCorner2,
        Map<Long, String> pointNames
) {
    public static final int DEFAULT_COLOR = 0xFF5DCBFF;
    public static final int MAX_POINT_NAME = 24;

    /** A place a team can deploy to; {@code id} is its position packed as a long. */
    public record DeployPoint(long id, PvpTeam team, BlockPos pos, String name) { }

    public PvpMapDefinition(String id, String displayName, String description, BlockPos redSpawn, BlockPos blueSpawn,
                            List<BlockPos> extraRedSpawns, List<BlockPos> extraBlueSpawns, BlockPos hillMin,
                            BlockPos hillMax, int cardColor, boolean enabled) {
        this(id, displayName, description, redSpawn, blueSpawn, extraRedSpawns, extraBlueSpawns, hillMin, hillMax,
                cardColor, enabled, null, null, Map.of());
    }

    public PvpMapDefinition {
        boundsCorner1 = boundsCorner1 == null ? null : boundsCorner1.immutable();
        boundsCorner2 = boundsCorner2 == null ? null : boundsCorner2.immutable();
        pointNames = pointNames == null ? Map.of() : Map.copyOf(pointNames);
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
        return setupIssues().isEmpty();
    }

    /** Everything still missing or wrong before the map can be played, for admins; empty when ready. */
    public List<String> setupIssues() {
        List<String> issues = new ArrayList<>();
        if (redSpawn.equals(BlockPos.ZERO)) issues.add("REDの本陣が未設定（setredspawn）");
        if (blueSpawn.equals(BlockPos.ZERO)) issues.add("BLUEの本陣が未設定（setbluespawn）");
        if (hillMin.equals(BlockPos.ZERO) || hillMax.equals(BlockPos.ZERO)) issues.add("丘が未設定（sethill1 / sethill2）");
        issues.addAll(problems());
        return issues;
    }

    /** Whether an admin set the battle area; without one it is {@link PvpMapGeometry#derived derived}. */
    public boolean hasOwnBounds() {
        return boundsCorner1 != null && boundsCorner2 != null;
    }

    /** The battle area: the one set for the map, or one around its spawns and hill. */
    public PvpMapGeometry.Box bounds() {
        if (hasOwnBounds()) {
            return PvpMapGeometry.own(boundsCorner1.getX(), boundsCorner1.getY(), boundsCorner1.getZ(),
                    boundsCorner2.getX(), boundsCorner2.getY(), boundsCorner2.getZ());
        }
        List<PvpMapGeometry.Point> points = new ArrayList<>();
        for (BlockPos pos : allRedSpawns()) points.add(point(pos));
        for (BlockPos pos : allBlueSpawns()) points.add(point(pos));
        points.add(point(hillMin));
        points.add(point(hillMax));
        return PvpMapGeometry.derived(points);
    }

    /** What keeps the map from being played, for admins; empty when nothing does. */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (!hasOwnBounds()) return problems;
        PvpMapGeometry.Box box = bounds();
        if (box.tooLarge()) problems.add("戦闘エリアが広すぎます（各辺" + PvpMapGeometry.MAX_SPAN + "ブロックまで）");
        // Points not set yet sit at the origin; they are a missing setting, not one outside the area.
        for (DeployPoint point : deployPoints()) {
            if (point.pos().equals(BlockPos.ZERO)) continue;
            if (!box.containsXZ(point.pos().getX(), point.pos().getZ())) {
                problems.add("出撃地点「" + point.name() + "」が戦闘エリアの外です");
            }
        }
        PvpMapGeometry.Box hill = PvpMapGeometry.Box.of(hillMin.getX(), hillMin.getY(), hillMin.getZ(),
                hillMax.getX(), hillMax.getY(), hillMax.getZ());
        boolean hillSet = !hillMin.equals(BlockPos.ZERO) && !hillMax.equals(BlockPos.ZERO);
        if (hillSet && !box.containsXZ(hill)) problems.add("丘が戦闘エリアからはみ出しています");
        return problems;
    }

    /** Every deploy point of both teams, main spawns first, each with its name. */
    public List<DeployPoint> deployPoints() {
        List<DeployPoint> points = new ArrayList<>();
        addPoints(points, PvpTeam.RED, allRedSpawns());
        addPoints(points, PvpTeam.BLUE, allBlueSpawns());
        return points;
    }

    public List<DeployPoint> deployPoints(PvpTeam team) {
        return deployPoints().stream().filter(point -> point.team() == team).toList();
    }

    private void addPoints(List<DeployPoint> points, PvpTeam team, List<BlockPos> positions) {
        String side = team == PvpTeam.RED ? "赤" : "青";
        for (int i = 0; i < positions.size(); i++) {
            BlockPos pos = positions.get(i);
            String fallback = i == 0 ? side + " 本陣" : side + " 出撃地点" + (i + 1);
            points.add(new DeployPoint(pos.asLong(), team, pos, pointNames.getOrDefault(pos.asLong(), fallback)));
        }
    }

    private static PvpMapGeometry.Point point(BlockPos pos) {
        return new PvpMapGeometry.Point(pos.getX(), pos.getY(), pos.getZ());
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
        if (boundsCorner1 != null) tag.putLong("Bounds1", boundsCorner1.asLong());
        if (boundsCorner2 != null) tag.putLong("Bounds2", boundsCorner2.asLong());
        ListTag names = new ListTag();
        pointNames.forEach((pos, name) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", pos);
            entry.putString("Name", name);
            names.add(entry);
        });
        tag.put("PointNames", names);
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
                !tag.contains("Enabled") || tag.getBoolean("Enabled"),
                tag.contains("Bounds1") ? BlockPos.of(tag.getLong("Bounds1")) : null,
                tag.contains("Bounds2") ? BlockPos.of(tag.getLong("Bounds2")) : null,
                loadNames(tag.getList("PointNames", Tag.TAG_COMPOUND))
        );
    }

    private static Map<Long, String> loadNames(ListTag list) {
        Map<Long, String> names = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            names.put(entry.getLong("Pos"), entry.getString("Name"));
        }
        return names;
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
        buffer.writeBoolean(hasOwnBounds());
        if (hasOwnBounds()) {
            buffer.writeBlockPos(boundsCorner1);
            buffer.writeBlockPos(boundsCorner2);
        }
        buffer.writeVarInt(pointNames.size());
        pointNames.forEach((pos, name) -> {
            buffer.writeLong(pos);
            buffer.writeUtf(name, MAX_POINT_NAME * 4);
        });
    }

    public static PvpMapDefinition read(FriendlyByteBuf buffer) {
        String id = buffer.readUtf(64);
        String displayName = buffer.readUtf(64);
        String description = buffer.readUtf(256);
        BlockPos redSpawn = buffer.readBlockPos();
        BlockPos blueSpawn = buffer.readBlockPos();

        int redSize = buffer.readVarInt();
        if (redSize < 0 || redSize > 256) throw new IllegalArgumentException("Invalid red spawn count: " + redSize);
        List<BlockPos> extraRed = new ArrayList<>(redSize);
        for (int i = 0; i < redSize; i++) extraRed.add(buffer.readBlockPos());

        int blueSize = buffer.readVarInt();
        if (blueSize < 0 || blueSize > 256) throw new IllegalArgumentException("Invalid blue spawn count: " + blueSize);
        List<BlockPos> extraBlue = new ArrayList<>(blueSize);
        for (int i = 0; i < blueSize; i++) extraBlue.add(buffer.readBlockPos());

        BlockPos hillMin = buffer.readBlockPos();
        BlockPos hillMax = buffer.readBlockPos();
        int cardColor = buffer.readInt();
        boolean enabled = buffer.readBoolean();
        BlockPos bounds1 = null;
        BlockPos bounds2 = null;
        if (buffer.readBoolean()) {
            bounds1 = buffer.readBlockPos();
            bounds2 = buffer.readBlockPos();
        }
        int nameCount = buffer.readVarInt();
        if (nameCount < 0 || nameCount > 512) throw new IllegalArgumentException("Invalid point name count: " + nameCount);
        Map<Long, String> names = new HashMap<>();
        for (int i = 0; i < nameCount; i++) names.put(buffer.readLong(), buffer.readUtf(MAX_POINT_NAME * 4));

        return new PvpMapDefinition(id, displayName, description, redSpawn, blueSpawn, extraRed, extraBlue, hillMin,
                hillMax, cardColor, enabled, bounds1, bounds2, names);
    }

    public PvpMapDefinition withDisplayName(String name) {
        return new PvpMapDefinition(id, name, description, redSpawn, blueSpawn, extraRedSpawns, extraBlueSpawns, hillMin, hillMax, cardColor, enabled, boundsCorner1, boundsCorner2, pointNames);
    }

    public PvpMapDefinition withDescription(String desc) {
        return new PvpMapDefinition(id, displayName, desc, redSpawn, blueSpawn, extraRedSpawns, extraBlueSpawns, hillMin, hillMax, cardColor, enabled, boundsCorner1, boundsCorner2, pointNames);
    }

    public PvpMapDefinition withRedSpawn(BlockPos pos) {
        return new PvpMapDefinition(id, displayName, description, pos, blueSpawn, extraRedSpawns, extraBlueSpawns, hillMin, hillMax, cardColor, enabled, boundsCorner1, boundsCorner2, pointNames);
    }

    public PvpMapDefinition withBlueSpawn(BlockPos pos) {
        return new PvpMapDefinition(id, displayName, description, redSpawn, pos, extraRedSpawns, extraBlueSpawns, hillMin, hillMax, cardColor, enabled, boundsCorner1, boundsCorner2, pointNames);
    }

    public PvpMapDefinition withAddedRedSpawn(BlockPos pos) {
        List<BlockPos> list = new ArrayList<>(extraRedSpawns);
        list.add(pos.immutable());
        return new PvpMapDefinition(id, displayName, description, redSpawn, blueSpawn, list, extraBlueSpawns, hillMin, hillMax, cardColor, enabled, boundsCorner1, boundsCorner2, pointNames);
    }

    public PvpMapDefinition withAddedBlueSpawn(BlockPos pos) {
        List<BlockPos> list = new ArrayList<>(extraBlueSpawns);
        list.add(pos.immutable());
        return new PvpMapDefinition(id, displayName, description, redSpawn, blueSpawn, extraRedSpawns, list, hillMin, hillMax, cardColor, enabled, boundsCorner1, boundsCorner2, pointNames);
    }

    public PvpMapDefinition withClearedExtraSpawns(boolean clearRed, boolean clearBlue) {
        return new PvpMapDefinition(
                id, displayName, description, redSpawn, blueSpawn,
                clearRed ? List.of() : extraRedSpawns,
                clearBlue ? List.of() : extraBlueSpawns,
                hillMin, hillMax, cardColor, enabled, boundsCorner1, boundsCorner2, pointNames
        );
    }

    public PvpMapDefinition withHillMin(BlockPos pos) {
        return new PvpMapDefinition(id, displayName, description, redSpawn, blueSpawn, extraRedSpawns, extraBlueSpawns, pos, hillMax, cardColor, enabled, boundsCorner1, boundsCorner2, pointNames);
    }

    public PvpMapDefinition withHillMax(BlockPos pos) {
        return new PvpMapDefinition(id, displayName, description, redSpawn, blueSpawn, extraRedSpawns, extraBlueSpawns, hillMin, pos, cardColor, enabled, boundsCorner1, boundsCorner2, pointNames);
    }

    public PvpMapDefinition withEnabled(boolean state) {
        return new PvpMapDefinition(id, displayName, description, redSpawn, blueSpawn, extraRedSpawns, extraBlueSpawns, hillMin, hillMax, cardColor, state, boundsCorner1, boundsCorner2, pointNames);
    }

    public PvpMapDefinition withCardColor(int color) {
        return new PvpMapDefinition(id, displayName, description, redSpawn, blueSpawn, extraRedSpawns, extraBlueSpawns, hillMin, hillMax, color, enabled, boundsCorner1, boundsCorner2, pointNames);
    }

    public PvpMapDefinition withBoundsCorner(boolean first, BlockPos pos) {
        return new PvpMapDefinition(id, displayName, description, redSpawn, blueSpawn, extraRedSpawns, extraBlueSpawns,
                hillMin, hillMax, cardColor, enabled, first ? pos : boundsCorner1, first ? boundsCorner2 : pos, pointNames);
    }

    public PvpMapDefinition withPointName(BlockPos pos, String name) {
        Map<Long, String> names = new HashMap<>(pointNames);
        if (name == null || name.isBlank()) names.remove(pos.asLong());
        else names.put(pos.asLong(), name.strip());
        return new PvpMapDefinition(id, displayName, description, redSpawn, blueSpawn, extraRedSpawns, extraBlueSpawns,
                hillMin, hillMax, cardColor, enabled, boundsCorner1, boundsCorner2, names);
    }

    /** The legacy single arena's spawns and hill, keeping everything else this map has. */
    public PvpMapDefinition withArena(BlockPos red, BlockPos blue, BlockPos hill1, BlockPos hill2) {
        return new PvpMapDefinition(id, displayName, description, red, blue, extraRedSpawns, extraBlueSpawns,
                hill1, hill2, cardColor, true, boundsCorner1, boundsCorner2, pointNames);
    }
}
