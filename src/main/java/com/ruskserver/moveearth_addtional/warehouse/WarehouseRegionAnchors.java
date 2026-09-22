package com.ruskserver.moveearth_addtional.warehouse;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.terrain.TerrainTile;
import com.ruskserver.moveearth_addtional.terrain.TerrainTileStore;
import net.minecraft.world.level.ChunkPos;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Fixed structure chunks for the released terrain tile, not a runtime terrain search. */
public final class WarehouseRegionAnchors {
    // Fingerprints of the tile inside build/terrain/moveearth_terrain.tar.gz.
    // Never silently apply these coordinates to another map with different regions or rivers.
    private static final Map<String, String> PRODUCTION_FILES = Map.of(
            "tile.json", "6152a2986cb406d3742d5c473128f79c6f583bd2616b0a5edbea53a98a115996",
            "region.bin", "6954e1791918d7d37bc2e4ae885d6ce370928158c65a1ffb0ac1203e6f80aedb",
            "height.bin", "246960c3b9af5c7191650b51cfa15505a0ce9a2317649c1b1bf87d67d536cfe6",
            "river_segments.json", "44638b02a5e867cbcb35a545c99a3584a45108c84f0b31e6fad5ca777adc8738"
    );

    private static volatile Map<Integer, List<Anchor>> anchors = Map.of();
    private static volatile Map<ChunkPos, Anchor> byChunk = Map.of();
    private static final Map<Integer, List<Anchor>> villageSafe = new ConcurrentHashMap<>();
    private static final Map<Integer, Optional<Anchor>> resolved = new ConcurrentHashMap<>();

    private WarehouseRegionAnchors() { }

    public static void prepare(TerrainTileStore store) {
        clear();
        if (store.tiles().size() != 1) {
            Moveearth_addtional.LOGGER.warn("Fixed warehouses disabled: expected one production terrain tile, found {}",
                    store.tiles().size());
            return;
        }
        TerrainTile tile = store.tiles().getFirst();
        try {
            if (!matchesProductionTile(tile.directory())) {
                Moveearth_addtional.LOGGER.warn("Fixed warehouses disabled: terrain tile at {} does not match "
                        + "build/terrain/moveearth_terrain.tar.gz", tile.directory());
                return;
            }
        } catch (IOException exception) {
            Moveearth_addtional.LOGGER.error("Fixed warehouses disabled: cannot verify terrain tile at {}",
                    tile.directory(), exception);
            return;
        }
        Map<Integer, List<Anchor>> regions = new HashMap<>();
        Map<ChunkPos, Anchor> chunks = new HashMap<>();
        for (WarehouseFixedSites.Site site : WarehouseFixedSites.SITES) {
            if (!sameRegion(tile, site)) {
                Moveearth_addtional.LOGGER.error("Fixed warehouse at {},{} does not belong entirely to region {}",
                        site.minX(), site.minZ(), site.region());
                continue;
            }
            Anchor anchor = new Anchor(site.region(), site.minX(), site.minZ());
            if (regions.putIfAbsent(site.region(), List.of(anchor)) != null
                    || chunks.putIfAbsent(anchor.chunk(), anchor) != null) {
                throw new IllegalStateException("Duplicate fixed warehouse region or chunk: " + site);
            }
        }
        anchors = Map.copyOf(regions);
        byChunk = Map.copyOf(chunks);
        Moveearth_addtional.LOGGER.info("Prepared {} fixed warehouse sites from the production terrain tile",
                regions.size());
    }

    private static boolean matchesProductionTile(Path directory) throws IOException {
        for (var expected : PRODUCTION_FILES.entrySet()) {
            if (!sha256(directory.resolve(expected.getKey())).equals(expected.getValue())) return false;
        }
        return true;
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream stream = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = stream.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java runtime does not provide SHA-256", exception);
        }
    }

    private static boolean sameRegion(TerrainTile tile, WarehouseFixedSites.Site site) {
        for (int dx : new int[] {0, WarehouseSitePolicy.WIDTH / 2, WarehouseSitePolicy.WIDTH - 1}) {
            for (int dz : new int[] {0, WarehouseSitePolicy.LENGTH / 2, WarehouseSitePolicy.LENGTH - 1}) {
                int x = site.minX() + dx;
                int z = site.minZ() + dz;
                if (!tile.covers(x, z) || tile.sampleNearest("region", x, z) != site.region()
                        || tile.sampleNearest("continent", x, z) <= 0) return false;
            }
        }
        return true;
    }

    public static void clear() {
        anchors = Map.of();
        byChunk = Map.of();
        villageSafe.clear();
        resolved.clear();
    }

    public static List<Anchor> forRegion(int region) { return anchors.getOrDefault(region, List.of()); }

    public static List<Anchor> all() {
        return anchors.values().stream().flatMap(List::stream)
                .sorted(java.util.Comparator.comparingInt(Anchor::region)).toList();
    }

    public static Anchor forChunk(ChunkPos chunk) { return byChunk.get(chunk); }

    public static String generationStatus(int region) {
        List<Anchor> nearVillage = villageSafe.get(region);
        if (nearVillage != null && nearVillage.isEmpty()) return "村に近いため除外";
        Optional<Anchor> actual = resolved.get(region);
        if (actual != null && actual.isEmpty()) return "実地の高さ条件で除外";
        return "チャンク未生成または登録待ち";
    }

    public static boolean villageSafe(Anchor anchor, Predicate<Anchor> allowed) {
        return villageSafe.computeIfAbsent(anchor.region(), region ->
                forRegion(region).stream().filter(allowed).toList()).contains(anchor);
    }

    public static Anchor resolve(int region, Predicate<Anchor> viable) {
        return resolved.computeIfAbsent(region, ignored ->
                villageSafe.getOrDefault(region, forRegion(region)).stream()
                        .filter(viable).findFirst()).orElse(null);
    }

    public record Anchor(int region, int minX, int minZ) {
        public ChunkPos chunk() { return new ChunkPos(minX >> 4, minZ >> 4); }
    }
}
