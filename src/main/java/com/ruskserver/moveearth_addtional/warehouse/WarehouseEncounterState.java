package com.ruskserver.moveearth_addtional.warehouse;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Durable encounter identity. Never infer a victory from an absent or unloaded mob. */
public final class WarehouseEncounterState extends SavedData {
    public enum Phase { DORMANT, ACTIVE, ALERTED, LOOT_OPEN, COOLDOWN, DEFEATED, FAILED }

    public static final long LOOT_TICKS = 10L * 60L * 20L;
    public static final long COOLDOWN_TICKS = 8L * 60L * 60L * 20L;
    public static final long FAILURE_TICKS = 5L * 60L * 20L;

    private final Map<Integer, Encounter> byRegion = new HashMap<>();
    private final Map<Integer, LootContainer> lootByRegion = new HashMap<>();

    public Encounter get(int region) {
        return byRegion.getOrDefault(region, new Encounter(Phase.DORMANT, 0, null, 0));
    }

    public boolean begin(int region, UUID boss) {
        Encounter current = get(region);
        if (region <= 0 || current.phase() != Phase.DORMANT || boss == null) return false;
        byRegion.put(region, new Encounter(Phase.ACTIVE, current.cycle() + 1, boss, 0));
        setDirty();
        return true;
    }

    public boolean alert(int region, UUID boss) {
        Encounter current = get(region);
        if (!WarehouseEncounterPolicy.matchesBoss(current.phase() == Phase.ACTIVE,
                current.boss(), boss)) return false;
        byRegion.put(region, new Encounter(Phase.ALERTED, current.cycle(), boss, 0));
        setDirty();
        return true;
    }

    public boolean defeat(int region, UUID boss, long now, java.util.List<ItemStack> rewards) {
        Encounter current = get(region);
        if (!WarehouseEncounterPolicy.matchesBoss(
                current.phase() == Phase.ACTIVE || current.phase() == Phase.ALERTED,
                current.boss(), boss)) return false;
        if (rewards == null || rewards.isEmpty()) return false;
        LootContainer loot = new LootContainer(region);
        for (int i = 0; i < Math.min(loot.getContainerSize(), rewards.size()); i++) {
            loot.setItem(i, rewards.get(i).copy());
        }
        lootByRegion.put(region, loot);
        byRegion.put(region, new Encounter(Phase.LOOT_OPEN, current.cycle(), null, now + LOOT_TICKS));
        setDirty();
        return true;
    }

    public boolean fail(int region, UUID boss, long now) {
        Encounter current = get(region);
        if (!WarehouseEncounterPolicy.matchesBoss(
                current.phase() == Phase.ACTIVE || current.phase() == Phase.ALERTED,
                current.boss(), boss)) return false;
        byRegion.put(region, new Encounter(Phase.COOLDOWN, current.cycle(), null, now + FAILURE_TICKS));
        setDirty();
        return true;
    }

    public void advance(int region, long now) {
        Encounter current = get(region);
        if (!WarehouseEncounterPolicy.deadlineReached(now, current.deadline())) return;
        if (current.phase() == Phase.LOOT_OPEN) {
            lootByRegion.remove(region);
            byRegion.put(region, new Encounter(Phase.COOLDOWN, current.cycle(), null,
                    now + COOLDOWN_TICKS));
            setDirty();
        } else if (current.phase() == Phase.COOLDOWN) {
            byRegion.put(region, new Encounter(Phase.DORMANT, current.cycle(), null, 0));
            setDirty();
        }
    }

    public SimpleContainer loot(int region) {
        return get(region).phase() == Phase.LOOT_OPEN ? lootByRegion.get(region) : null;
    }

    public void resetForTesting(int region) {
        Encounter current = get(region);
        lootByRegion.remove(region);
        byRegion.put(region, new Encounter(Phase.DORMANT, current.cycle(), null, 0));
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("SchemaVersion", 2);
        ListTag entries = new ListTag();
        byRegion.forEach((region, encounter) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Region", region);
            entry.putString("Phase", encounter.phase().name());
            entry.putInt("Cycle", encounter.cycle());
            entry.putLong("Deadline", encounter.deadline());
            if (encounter.boss() != null) entry.putUUID("Boss", encounter.boss());
            LootContainer loot = lootByRegion.get(region);
            if (encounter.phase() == Phase.LOOT_OPEN && loot != null) {
                entry.putBoolean("LootInitialized", true);
                NonNullList<ItemStack> items = NonNullList.withSize(loot.getContainerSize(), ItemStack.EMPTY);
                for (int i = 0; i < items.size(); i++) items.set(i, loot.getItem(i));
                ContainerHelper.saveAllItems(entry, items, registries);
            }
            entries.add(entry);
        });
        tag.put("Encounters", entries);
        return tag;
    }

    public static WarehouseEncounterState load(CompoundTag tag, HolderLookup.Provider registries) {
        WarehouseEncounterState data = new WarehouseEncounterState();
        for (Tag raw : tag.getList("Encounters", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            int region = entry.getInt("Region");
            if (region <= 0) continue;
            try {
                Phase phase = Phase.valueOf(entry.getString("Phase"));
                if (tag.getInt("SchemaVersion") < 2
                        && (phase == Phase.DEFEATED || phase == Phase.FAILED)) phase = Phase.DORMANT;
                UUID boss = entry.hasUUID("Boss") ? entry.getUUID("Boss") : null;
                if (phase == Phase.DORMANT) boss = null;
                if ((phase == Phase.ACTIVE || phase == Phase.ALERTED) != (boss != null)) continue;
                if (phase == Phase.LOOT_OPEN) {
                    if (!entry.getBoolean("LootInitialized")) continue;
                    NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
                    ContainerHelper.loadAllItems(entry, items, registries);
                    LootContainer loot = data.new LootContainer(region);
                    for (int i = 0; i < items.size(); i++) loot.setItem(i, items.get(i));
                    data.lootByRegion.put(region, loot);
                }
                data.byRegion.put(region, new Encounter(phase,
                        Math.max(0, entry.getInt("Cycle")), boss, Math.max(0, entry.getLong("Deadline"))));
            } catch (IllegalArgumentException ignored) {
                // Unknown future phase must not accidentally enable a reward path.
            }
        }
        return data;
    }

    public static WarehouseEncounterState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WarehouseEncounterState::new, WarehouseEncounterState::load, null),
                "moveearth_warehouse_encounters");
    }

    public record Encounter(Phase phase, int cycle, UUID boss, long deadline) { }

    private final class LootContainer extends SimpleContainer {
        private final int region;

        private LootContainer(int region) {
            super(9);
            this.region = region;
        }

        @Override public void setChanged() {
            super.setChanged();
            WarehouseEncounterState.this.setDirty();
        }

        @Override public boolean stillValid(Player player) {
            if (player.level().isClientSide) return true;
            if (get(region).phase() != Phase.LOOT_OPEN || player.getServer() == null) return false;
            return WarehouseSites.get(player.getServer()).all().stream().anyMatch(site ->
                    site.regionId() == region
                            && site.dimension().equals(player.level().dimension().location())
                            && WarehouseSitePolicy.insideStructure(site.min().getX(), site.min().getY(),
                            site.min().getZ(), player.blockPosition().getX(),
                            player.blockPosition().getY(), player.blockPosition().getZ()));
        }
    }
}
