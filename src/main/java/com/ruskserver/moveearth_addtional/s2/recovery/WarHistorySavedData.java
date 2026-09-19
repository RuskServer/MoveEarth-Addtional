package com.ruskserver.moveearth_addtional.s2.recovery;

import com.ruskserver.moveearth_addtional.config.RecoveryDispatchConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Bounded, non-spatial history used by the recovery UI and Discord summaries. */
public final class WarHistorySavedData extends SavedData {
    private final List<Entry> entries = new ArrayList<>();

    public Entry append(long openTick, Type type, Visibility visibility, UUID primaryNation,
                        UUID secondaryNation, UUID sourceId, List<String> arguments) {
        Entry entry = new Entry(UUID.randomUUID(), Math.max(0L, openTick), type, visibility,
                primaryNation, secondaryNation, sourceId,
                arguments == null ? List.of() : List.copyOf(arguments));
        entries.add(entry);
        int retention = RecoveryDispatchConfig.historyRetention();
        if (entries.size() > retention) entries.subList(0, entries.size() - retention).clear();
        setDirty();
        return entry;
    }

    public List<Entry> visibleTo(UUID nationId, boolean admin) {
        return entries.stream().filter(entry -> admin || entry.visibility() == Visibility.PUBLIC
                || nationId != null && (nationId.equals(entry.primaryNation())
                || nationId.equals(entry.secondaryNation()))).toList().reversed();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("SchemaVersion", 1);
        ListTag list = new ListTag();
        for (Entry value : entries) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", value.id());
            entry.putLong("OpenTick", value.openTick());
            entry.putString("Type", value.type().name());
            entry.putString("Visibility", value.visibility().name());
            if (value.primaryNation() != null) entry.putUUID("PrimaryNation", value.primaryNation());
            if (value.secondaryNation() != null) entry.putUUID("SecondaryNation", value.secondaryNation());
            if (value.sourceId() != null) entry.putUUID("Source", value.sourceId());
            ListTag args = new ListTag();
            for (String argument : value.arguments()) {
                CompoundTag valueTag = new CompoundTag();
                valueTag.putString("Value", argument == null ? "" : argument);
                args.add(valueTag);
            }
            entry.put("Arguments", args);
            list.add(entry);
        }
        tag.put("Entries", list);
        return tag;
    }

    public static WarHistorySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        WarHistorySavedData data = new WarHistorySavedData();
        ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            if (!entry.hasUUID("Id")) continue;
            Type type;
            Visibility visibility;
            try { type = Type.valueOf(entry.getString("Type")); }
            catch (IllegalArgumentException ignored) { type = Type.SYSTEM; }
            try { visibility = Visibility.valueOf(entry.getString("Visibility")); }
            catch (IllegalArgumentException ignored) { visibility = Visibility.NATION; }
            List<String> arguments = new ArrayList<>();
            ListTag args = entry.getList("Arguments", Tag.TAG_COMPOUND);
            for (int arg = 0; arg < args.size(); arg++) arguments.add(args.getCompound(arg).getString("Value"));
            data.entries.add(new Entry(entry.getUUID("Id"), Math.max(0L, entry.getLong("OpenTick")),
                    type, visibility, entry.hasUUID("PrimaryNation") ? entry.getUUID("PrimaryNation") : null,
                    entry.hasUUID("SecondaryNation") ? entry.getUUID("SecondaryNation") : null,
                    entry.hasUUID("Source") ? entry.getUUID("Source") : null, List.copyOf(arguments)));
        }
        return data;
    }

    public static WarHistorySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WarHistorySavedData::new, WarHistorySavedData::load, null),
                "moveearth_war_history");
    }

    public enum Type {
        SYSTEM, RECOVERY_STARTED, RECOVERY_OBJECTIVE, RECOVERY_COMPLETED, RECOVERY_EXPIRED,
        SIEGE_STARTED, CORE_FALLEN, COUNTEROFFENSIVE_SUCCEEDED, PEACE_ESTABLISHED,
        DISPATCH_CREATED, DISPATCH_ACTIVATED, DISPATCH_COMPLETED, DISPATCH_CANCELLED,
        RIVAL_SET, RIVAL_CLEARED, VEHICLE_DESTROYED, STORAGE_WRECKED, PRISONER_TRANSPORTED,
        PRISONER_RESCUED, STORAGE_RECOVERED
    }
    public enum Visibility { PUBLIC, NATION }
    public record Entry(UUID id, long openTick, Type type, Visibility visibility,
                        UUID primaryNation, UUID secondaryNation, UUID sourceId,
                        List<String> arguments) { }
}
