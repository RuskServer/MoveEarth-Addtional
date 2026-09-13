package com.ruskserver.moveearth_addtional.compat.warnautics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owns placed aerial-bomb racks until their spawned projectile has inherited attribution. */
public final class WarnauticsBombSavedData extends SavedData {
    private static final long MAX_AGE_TICKS = 20L * 60L * 60L * 24L * 7L;
    private static final int CLAIM_RADIUS = 8;
    private final Map<Long, Placement> placements = new HashMap<>();

    public void put(BlockPos pos, String weaponPath, UUID placerId, UUID nationId,
                    UUID subLevelId, long placedTick) {
        placements.put(pos.asLong(), new Placement(
                pos.immutable(), weaponPath, placerId, nationId, subLevelId, placedTick));
        setDirty();
    }

    public void remove(BlockPos pos) {
        if (placements.remove(pos.asLong()) != null) setDirty();
    }

    public static void move(ServerLevel sourceLevel, ServerLevel destinationLevel,
                            BlockPos sourcePos, BlockPos destinationPos) {
        WarnauticsBombSavedData source = get(sourceLevel);
        Placement placement = source.placements.remove(sourcePos.asLong());
        if (placement == null) return;
        source.setDirty();
        get(destinationLevel).put(destinationPos, placement.weaponPath(), placement.placerId(),
                placement.nationId(), WarnauticsSableBombCompat.subLevelId(destinationLevel, destinationPos),
                placement.placedTick());
    }

    public Placement claimNearest(ServerLevel level, Vec3 worldPosition, String weaponPath, long now) {
        boolean purged = placements.entrySet().removeIf(entry -> now - entry.getValue().placedTick() > MAX_AGE_TICKS);
        long radiusSquared = (long) CLAIM_RADIUS * CLAIM_RADIUS;
        Placement match = placements.values().stream()
                .filter(entry -> entry.weaponPath().equals(weaponPath))
                .filter(entry -> distanceSquared(level, entry, worldPosition) <= radiusSquared)
                .min(Comparator.comparingDouble(entry -> distanceSquared(level, entry, worldPosition)))
                .orElse(null);
        if (match != null && !WarnauticsWeaponEvents.isBombBlock(level.getBlockState(match.pos()), weaponPath)) {
            placements.remove(match.pos().asLong());
            purged = true;
        }
        if (purged) setDirty();
        return match;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Placement placement : placements.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", placement.pos().asLong());
            entry.putString("Weapon", placement.weaponPath());
            entry.putUUID("Placer", placement.placerId());
            if (placement.nationId() != null) entry.putUUID("Nation", placement.nationId());
            if (placement.subLevelId() != null) entry.putUUID("SubLevel", placement.subLevelId());
            entry.putLong("PlacedTick", placement.placedTick());
            list.add(entry);
        }
        tag.put("Placements", list);
        return tag;
    }

    public static WarnauticsBombSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        WarnauticsBombSavedData data = new WarnauticsBombSavedData();
        ListTag list = tag.getList("Placements", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Placer")) continue;
            BlockPos pos = BlockPos.of(entry.getLong("Pos"));
            String weapon = entry.getString("Weapon");
            data.placements.put(pos.asLong(), new Placement(
                    pos, weapon, entry.getUUID("Placer"),
                    entry.hasUUID("Nation") ? entry.getUUID("Nation") : null,
                    entry.hasUUID("SubLevel") ? entry.getUUID("SubLevel") : null,
                    entry.getLong("PlacedTick")));
        }
        return data;
    }

    public static WarnauticsBombSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WarnauticsBombSavedData::new, WarnauticsBombSavedData::load, null),
                "moveearth_warnautics_bombs");
    }

    private static double distanceSquared(ServerLevel level, Placement placement, Vec3 worldPosition) {
        Vec3 mapped = WarnauticsSableBombCompat.worldPosition(level, placement.subLevelId(), placement.pos());
        return mapped == null ? Double.POSITIVE_INFINITY : mapped.distanceToSqr(worldPosition);
    }

    public record Placement(BlockPos pos, String weaponPath, UUID placerId, UUID nationId,
                            UUID subLevelId, long placedTick) { }
}
