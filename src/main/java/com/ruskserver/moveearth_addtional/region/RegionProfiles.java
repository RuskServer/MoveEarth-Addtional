package com.ruskserver.moveearth_addtional.region;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.RegionResourceConfig;
import com.ruskserver.moveearth_addtional.region.RegionProfileAssigner.Assignment;
import com.ruskserver.moveearth_addtional.region.RegionProfileAssigner.Profile;
import com.ruskserver.moveearth_addtional.region.RegionProfileAssigner.Region;
import com.ruskserver.moveearth_addtional.terrain.TerrainTile;
import com.ruskserver.moveearth_addtional.terrain.TerrainTileStore;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.server.MinecraftServer;

/**
 * Which resources each region has, held for the worldgen threads to read.
 *
 * <p>Built once, before any chunk exists, and never written to again. Chunk
 * generation runs on worker threads and asks this question for every ore
 * feature and every vein placement, so the answer has to be a plain read of an
 * immutable map published through one volatile field. Nothing here builds
 * anything lazily; a worker thread that arrived first must never be the one to
 * compute the region map.
 *
 * <p>Before it is built, and for any material it does not recognise, every
 * query answers yes. A region system that is not ready must let the world
 * generate normally, because the alternative is a world quietly missing its
 * ores with nothing in the log to say why.
 */
public final class RegionProfiles {

    private static volatile Snapshot active = null;

    /**
     * The material set the allocation was made from.
     *
     * <p>Kept so that a later, fuller view of what the pack contains can be
     * compared against it. The allocation happens before the first chunk and
     * cannot be redone afterwards without changing a world that already exists,
     * so the only useful response to a mismatch is to say so loudly.
     */
    private static volatile Set<String> assignedFrom = Set.of();

    /** The finished allocation. Immutable once published. */
    private record Snapshot(Map<Integer, Assignment> byRegion, Set<String> exclusive,
                            List<String> common) { }

    private RegionProfiles() { }

    /** True once the allocation exists and queries mean something. */
    public static boolean ready() {
        return active != null;
    }

    /**
     * Whether a material may generate in a region.
     *
     * <p>Exclusive materials generate only where they were assigned. Everything
     * else generates everywhere, in an amount {@link #densityFor} decides.
     */
    public static boolean allows(int region, String material) {
        Snapshot snapshot = active;
        if (snapshot == null || material == null || !snapshot.exclusive().contains(material)) {
            return true;
        }
        if (region == RegionGrid.NONE) {
            // Open ocean far from any coast belongs to nobody, so gating there
            // would only hide ore under water that no region can claim.
            return true;
        }
        Assignment assignment = snapshot.byRegion().get(region);
        return assignment != null && holds(assignment.profileId(), material);
    }

    /** The density multiplier for a material in a region. */
    public static double densityFor(int region, String material) {
        Snapshot snapshot = active;
        if (snapshot == null || region == RegionGrid.NONE) {
            return 1.0;
        }
        Assignment assignment = snapshot.byRegion().get(region);
        return assignment == null ? 1.0 : assignment.multiplierFor(material);
    }

    /** The allocation, for commands and diagnostics. */
    public static List<Assignment> assignments() {
        Snapshot snapshot = active;
        return snapshot == null ? List.of() : List.copyOf(snapshot.byRegion().values());
    }

    /**
     * Surveys the loaded tile and allocates resources to its regions.
     *
     * <p>Called once while the server is starting, after the terrain tiles are
     * in place and before the first chunk is generated.
     */
    public static void build(MinecraftServer server, Set<String> availableMaterials) {
        TerrainTileStore store = TerrainTileStore.active();
        if (store == null || store.tiles().isEmpty()) {
            active = null;
            Moveearth_addtional.LOGGER.warn(
                    "No terrain tiles, so no region map: every resource will generate everywhere.");
            return;
        }
        // Only materials something actually produces. Create: Rock & Stone
        // registers a deposit for every metal it knows of, including ones whose
        // mod is absent, and those deposits generate while yielding nothing.
        // Assigning such a material would hand a region an exclusive resource
        // that does not exist anywhere and cannot be traded for -- a region with
        // nothing, reported as a region with something.
        List<String> configured = RegionResourceConfig.exclusiveMaterials();
        List<String> exclusive = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String material : configured) {
            if (availableMaterials.isEmpty() || availableMaterials.contains(material)) {
                exclusive.add(material);
            } else {
                missing.add(material);
            }
        }
        if (!missing.isEmpty()) {
            Moveearth_addtional.LOGGER.warn(
                    "These materials are configured as exclusive but nothing in this pack "
                            + "produces them, so they are ignored: {}", missing);
        }
        if (exclusive.isEmpty()) {
            Moveearth_addtional.LOGGER.warn(
                    "No exclusive material exists in this pack; regions will differ only "
                            + "in how much of the common ores they hold.");
        }
        List<String> common = RegionResourceConfig.commonMaterials();
        assignedFrom = Set.copyOf(availableMaterials);

        List<Region> regions = new ArrayList<>();
        for (TerrainTile tile : store.tiles()) {
            if (!tile.has("region") || !tile.has("continent")) {
                continue;
            }
            // From the tile's own spawn anchor, not from the level. This runs
            // before any level exists, so the world's recorded spawn is not yet
            // set and asking for it would quietly disable the rule that keeps
            // the scarcest resource away from wherever players start.
            TerrainTile.SpawnAnchor spawn = store.primarySpawnAnchor();
            regions.addAll(RegionSurvey.survey(
                    RegionResolver.cellsOf(tile, "region"),
                    RegionResolver.cellsOf(tile, "continent"),
                    spawn == null ? Integer.MIN_VALUE : tile.cellX(spawn.x()),
                    spawn == null ? Integer.MIN_VALUE : tile.cellZ(spawn.z())));
        }
        if (regions.isEmpty()) {
            active = null;
            Moveearth_addtional.LOGGER.warn("The terrain tiles carry no regions; nothing is gated.");
            return;
        }

        // Scarcity follows the configured order: the first listed is treated as
        // the rarest, which is what keeps the order in the config meaningful
        // rather than decorative.
        List<Profile> profiles = new ArrayList<>();
        for (int index = 0; index < exclusive.size(); index++) {
            profiles.add(new Profile(exclusive.get(index), index));
        }

        Map<Integer, Assignment> byRegion = new TreeMap<>();
        for (Assignment assignment : RegionProfileAssigner.assign(regions, profiles, common)) {
            byRegion.put(assignment.regionId(), assignment);
        }
        active = new Snapshot(Map.copyOf(byRegion), Set.copyOf(new LinkedHashSet<>(exclusive)),
                List.copyOf(common));

        Moveearth_addtional.LOGGER.info("Region resources: {} region(s), {} exclusive material(s)",
                byRegion.size(), exclusive.size());
        for (Assignment assignment : byRegion.values()) {
            Moveearth_addtional.LOGGER.info("  region {}: {} (rich in {}, short of {})",
                    assignment.regionId(),
                    assignment.profileId() == null ? "no exclusive resource" : assignment.profileId(),
                    assignment.specialty(), assignment.shortage());
        }
    }

    /**
     * Warns if the pack turns out to hold materials the allocation never saw.
     *
     * <p>Called once the server is fully up, when every registry and tag is
     * certainly bound. Agreement means the earlier view was complete; a
     * difference means regions were allocated from a partial picture and the
     * world needs regenerating, which is worth an operator's attention rather
     * than a silent wrong answer.
     */
    public static void verifyAgainst(Set<String> materials) {
        if (active == null || materials.isEmpty()) {
            return;
        }
        Set<String> unseen = new java.util.TreeSet<>(materials);
        unseen.removeAll(assignedFrom);
        if (!unseen.isEmpty()) {
            Moveearth_addtional.LOGGER.error(
                    "Regions were allocated before these materials were known: {}. The "
                            + "allocation is already fixed for this world; regenerate it if "
                            + "they were meant to be exclusive.", unseen);
        }
    }

    /** Drops the allocation. Used when the tiles themselves go away. */
    public static void clear() {
        active = null;
    }

    private static boolean holds(String profileId, String material) {
        if (profileId == null) {
            return false;
        }
        for (String held : profileId.split(",")) {
            if (held.equals(material)) {
                return true;
            }
        }
        return false;
    }

}
