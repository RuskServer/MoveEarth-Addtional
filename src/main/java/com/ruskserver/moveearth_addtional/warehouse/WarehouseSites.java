package com.ruskserver.moveearth_addtional.warehouse;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-authoritative, durable registry. A region can have at most one warehouse. */
public final class WarehouseSites extends SavedData {
    private final Map<Integer, Site> byRegion = new LinkedHashMap<>();
    // A paste is journaled before any world edit. An interrupted/failed paste is never retried
    // automatically, and its footprint remains protected until an operator repairs it.
    private final Map<Integer, Site> placements = new LinkedHashMap<>();

    public List<Site> all() { return List.copyOf(byRegion.values()); }

    public List<Site> pendingPlacements() { return List.copyOf(placements.values()); }

    public boolean hasRegion(int regionId) { return byRegion.containsKey(regionId); }

    public boolean hasPlacement(int regionId) { return placements.containsKey(regionId); }

    public Site pendingPlacement(int regionId) { return placements.get(regionId); }

    public boolean beginPlacement(Site site) {
        if (site.regionId() <= 0 || byRegion.containsKey(site.regionId())
                || placements.containsKey(site.regionId())) return false;
        if (java.util.stream.Stream.concat(byRegion.values().stream(), placements.values().stream())
                .anyMatch(existing -> existing.dimension().equals(site.dimension())
                        && WarehouseSitePolicy.overlaps(existing.min().getX(), existing.min().getZ(),
                        site.min().getX(), site.min().getZ()))) return false;
        placements.put(site.regionId(), site);
        setDirty();
        return true;
    }

    public boolean add(Site site) {
        if (site.regionId() <= 0 || byRegion.containsKey(site.regionId())) return false;
        Site pending = placements.get(site.regionId());
        if (pending != null && (!pending.dimension().equals(site.dimension())
                || !pending.min().equals(site.min()))) return false;
        if (placements.values().stream().anyMatch(existing -> existing.regionId() != site.regionId()
                && existing.dimension().equals(site.dimension())
                && WarehouseSitePolicy.overlaps(existing.min().getX(), existing.min().getZ(),
                site.min().getX(), site.min().getZ()))) return false;
        if (byRegion.values().stream().anyMatch(existing -> existing.dimension().equals(site.dimension())
                && WarehouseSitePolicy.overlaps(existing.min().getX(), existing.min().getZ(),
                site.min().getX(), site.min().getZ()))) return false;
        byRegion.put(site.regionId(), site);
        placements.remove(site.regionId());
        setDirty();
        return true;
    }

    public boolean protects(ResourceLocation dimension, BlockPos pos) {
        return java.util.stream.Stream.concat(byRegion.values().stream(), placements.values().stream())
                .anyMatch(site -> site.dimension().equals(dimension)
                && WarehouseSitePolicy.within(site.min().getX(), site.min().getZ(), pos.getX(), pos.getZ(),
                WarehouseSitePolicy.BUILD_MARGIN));
    }

    public boolean insideStructure(ResourceLocation dimension, BlockPos pos) {
        return byRegion.values().stream().anyMatch(site -> site.dimension().equals(dimension)
                && WarehouseSitePolicy.insideStructure(site.min().getX(), site.min().getY(), site.min().getZ(),
                pos.getX(), pos.getY(), pos.getZ()));
    }

    public boolean conflictsClaim(ResourceLocation dimension,
                                  com.ruskserver.moveearth_addtional.s2.territory.TerritoryPreviewArea area) {
        return java.util.stream.Stream.concat(byRegion.values().stream(), placements.values().stream())
                .anyMatch(site -> site.dimension().equals(dimension)
                && WarehouseSitePolicy.intersectsClaim(site.min().getX(), site.min().getZ(),
                area.minBlockX(), area.maxBlockXExclusive(),
                area.minBlockZ(), area.maxBlockZExclusive()));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("SchemaVersion", 1);
        ListTag entries = new ListTag();
        for (Site site : byRegion.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Region", site.regionId());
            entry.putString("Dimension", site.dimension().toString());
            entry.putInt("X", site.min().getX());
            entry.putInt("Y", site.min().getY());
            entry.putInt("Z", site.min().getZ());
            entries.add(entry);
        }
        tag.put("Sites", entries);
        ListTag pending = new ListTag();
        for (Site site : placements.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Region", site.regionId());
            entry.putString("Dimension", site.dimension().toString());
            entry.putInt("X", site.min().getX());
            entry.putInt("Y", site.min().getY());
            entry.putInt("Z", site.min().getZ());
            pending.add(entry);
        }
        tag.put("Placements", pending);
        return tag;
    }

    public static WarehouseSites load(CompoundTag tag, HolderLookup.Provider registries) {
        WarehouseSites data = new WarehouseSites();
        for (Tag raw : tag.getList("Sites", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            int region = entry.getInt("Region");
            if (dimension == null || region <= 0 || data.byRegion.containsKey(region)) continue;
            data.byRegion.put(region, new Site(region, dimension,
                    new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z"))));
        }
        for (Tag raw : tag.getList("Placements", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            int region = entry.getInt("Region");
            if (dimension == null || region <= 0 || data.byRegion.containsKey(region)) continue;
            data.placements.putIfAbsent(region, new Site(region, dimension,
                    new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z"))));
        }
        return data;
    }

    public static WarehouseSites get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WarehouseSites::new, WarehouseSites::load, null),
                "moveearth_warehouse_sites");
    }

    public record Site(int regionId, ResourceLocation dimension, BlockPos min) { }
}
