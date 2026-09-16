package com.ruskserver.moveearth_addtional.compat.warnautics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persists C4 support and attribution because Warnautics removes the block before its blast event. */
public final class WarnauticsC4SavedData extends SavedData {
    private final Map<Long, Charge> charges = new HashMap<>();

    public void put(BlockPos chargePos, BlockPos supportPos, UUID placerId, UUID nationId,
                    UUID contractId, UUID siegeId, long placedTick) {
        charges.put(chargePos.asLong(), new Charge(
                chargePos.immutable(), supportPos.immutable(), placerId, nationId, contractId, siegeId, placedTick));
        setDirty();
    }

    public Charge consume(BlockPos chargePos) {
        Charge removed = charges.remove(chargePos.asLong());
        if (removed != null) setDirty();
        return removed;
    }

    public void remove(BlockPos chargePos) {
        if (charges.remove(chargePos.asLong()) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Charge charge : charges.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", charge.pos().asLong());
            entry.putLong("Support", charge.support().asLong());
            entry.putUUID("Placer", charge.placerId());
            if (charge.nationId() != null) entry.putUUID("Nation", charge.nationId());
            if (charge.contractId() != null) entry.putUUID("Contract", charge.contractId());
            if (charge.siegeId() != null) entry.putUUID("Siege", charge.siegeId());
            entry.putLong("PlacedTick", charge.placedTick());
            list.add(entry);
        }
        tag.put("Charges", list);
        return tag;
    }

    public static WarnauticsC4SavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        WarnauticsC4SavedData data = new WarnauticsC4SavedData();
        ListTag list = tag.getList("Charges", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Placer")) continue;
            BlockPos pos = BlockPos.of(entry.getLong("Pos"));
            data.charges.put(pos.asLong(), new Charge(
                    pos, BlockPos.of(entry.getLong("Support")), entry.getUUID("Placer"),
                    entry.hasUUID("Nation") ? entry.getUUID("Nation") : null,
                    entry.hasUUID("Contract") ? entry.getUUID("Contract") : null,
                    entry.hasUUID("Siege") ? entry.getUUID("Siege") : null, entry.getLong("PlacedTick")));
        }
        return data;
    }

    public static WarnauticsC4SavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WarnauticsC4SavedData::new, WarnauticsC4SavedData::load, null),
                "moveearth_warnautics_c4");
    }

    public record Charge(BlockPos pos, BlockPos support, UUID placerId, UUID nationId,
                         UUID contractId, UUID siegeId, long placedTick) { }
}
