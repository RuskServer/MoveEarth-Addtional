package com.ruskserver.moveearth_addtional.s2.nation;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Each nation's tax rate on its citizens' Jobs income ({@link NationTaxPolicy}). A nation never set has 0 %. */
public final class NationTaxSavedData extends SavedData {
    public record Rate(int percent, long lastRaisedAt) {
        static final Rate NONE = new Rate(0, 0L);
    }

    private final Map<UUID, Rate> rates = new LinkedHashMap<>();

    public Rate rate(UUID nationId) {
        return nationId == null ? Rate.NONE : rates.getOrDefault(nationId, Rate.NONE);
    }

    public int percent(UUID nationId) {
        return rate(nationId).percent();
    }

    public void set(UUID nationId, int percent, long now) {
        if (nationId == null) return;
        Rate before = rate(nationId);
        int clamped = NationTaxPolicy.clamp(percent);
        rates.put(nationId, new Rate(clamped, clamped > before.percent() ? now : before.lastRaisedAt()));
        setDirty();
    }

    public void removeNation(UUID nationId) {
        if (nationId != null && rates.remove(nationId) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        rates.forEach((nation, rate) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Nation", nation);
            entry.putInt("Percent", rate.percent());
            entry.putLong("LastRaisedAt", rate.lastRaisedAt());
            list.add(entry);
        });
        tag.put("Rates", list);
        return tag;
    }

    public static NationTaxSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        NationTaxSavedData data = new NationTaxSavedData();
        ListTag list = tag.getList("Rates", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Nation")) continue;
            data.rates.put(entry.getUUID("Nation"), new Rate(NationTaxPolicy.clamp(entry.getInt("Percent")),
                    Math.max(0L, entry.getLong("LastRaisedAt"))));
        }
        return data;
    }

    public static NationTaxSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(NationTaxSavedData::new, NationTaxSavedData::load, null),
                "moveearth_nation_tax");
    }
}
