package com.ruskserver.moveearth_addtional.region;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Decides which region gets which strategic resource, and how much of the
 * common ones, with no Minecraft in it so it can be tested.
 *
 * <p>The regions the terrain generator produces are not interchangeable. On the
 * production tile the largest is 3.19 times the area of the smallest, and the
 * smallest is also a coastal ribbon filling less than a third of its bounding
 * box — reachable from the sea along its whole length and with no depth to
 * retreat into. Handing out exclusive resources without looking at that would
 * make the value of a region depend on how much land it happened to get rather
 * than on what is under it.
 *
 * <p>Two different problems, so two different tools. Area is answered with
 * density: a small region gets a higher multiplier on the common ores, so
 * holding it is still worth something. Shape is answered with placement: the
 * scarcest exclusive resource does not go to the region that is hardest to
 * hold, because a resource nobody can defend is not scarce, it is just
 * contested. Trying to fix shape with density instead would make the most
 * exposed region the richest, which is the opposite of what it needs.
 */
public final class RegionProfileAssigner {

    /**
     * A region as the terrain measurements describe it.
     *
     * @param areaCells   cells the region covers
     * @param compactness cells over the square of the bounding box's longer
     *                    side; 1 for a solid square, low for anything long and
     *                    thin. On the production tile the provinces sit at 0.42
     *                    to 0.68, the coastal ribbon at 0.20 and a narrow strip
     *                    at 0.33
     * @param hasWorldSpawn whether the world spawn falls inside it
     */
    public record Region(int id, int continent, int areaCells, double compactness,
                         boolean hasWorldSpawn, Set<Integer> neighbours) {

        /** For callers with no adjacency to hand, such as a lone region. */
        public Region(int id, int continent, int areaCells, double compactness,
                      boolean hasWorldSpawn) {
            this(id, continent, areaCells, compactness, hasWorldSpawn, Set.of());
        }
    }

    /**
     * An exclusive resource waiting for somewhere to live.
     *
     * @param scarcity lower is scarcer, and scarcer resources are placed first
     *                 and kept away from the weakest regions
     * @param depositsPerCell how many deposits of it a cell of land yields, as
     *                        measured from the pack. Zero means unknown, and
     *                        then the size floor does not apply
     */
    public record Profile(String id, int scarcity, double depositsPerCell) {

        /** For callers with no density measurement, such as a test. */
        public Profile(String id, int scarcity) {
            this(id, scarcity, 0.0);
        }

        /** How many of this resource a region of the given size would hold. */
        public double expectedIn(Region region) {
            return region.areaCells() * depositsPerCell;
        }
    }

    /**
     * The fewest deposits a region must expect before it can be the exclusive
     * home of a resource.
     *
     * <p>One, because below that the region defining itself by a resource may
     * contain none of it. Measured on the production map, uranium came to 0.49
     * expected deposits in the smallest region: half the time, the uranium
     * region has no uranium. A resource nobody can find is worse than one
     * nobody has, because the map says it is there.
     */
    private static final double MINIMUM_EXPECTED_DEPOSITS = 1.0;

    /**
     * What one region ended up with.
     *
     * @param profileId the exclusive resource, or null for a region that has none
     * @param baseDensity the area correction on the common ores
     * @param specialty a common ore this region has more of than its neighbours
     * @param shortage a common ore it has less of, so it has something to want
     */
    public record Assignment(int regionId, String profileId, double baseDensity,
                             String specialty, String shortage) {

        /** The final multiplier for one material in this region. */
        public double multiplierFor(String material) {
            double density = baseDensity;
            if (material.equals(specialty)) {
                density *= SPECIALTY_BONUS;
            } else if (material.equals(shortage)) {
                density *= SHORTAGE_PENALTY;
            }
            return clampDensity(density);
        }
    }

    /** How much more of its specialty a region has. */
    private static final double SPECIALTY_BONUS = 1.6;

    /** How much less of the one it is short of. */
    private static final double SHORTAGE_PENALTY = 0.6;

    /**
     * How far to correct for area, as an exponent on the area ratio.
     *
     * <p>Half, not one. Full correction would equalise the total ore in every
     * region, which sounds fair and plays badly: the smallest region would hold
     * three times the ore per chunk of the largest, so the quickest way to get
     * rich would be to take the small one. A square root leaves a real
     * advantage in holding more land while stopping the small regions from
     * being worthless.
     */
    private static final double AREA_CORRECTION = 0.5;

    /** The band the tier design allows a common-ore multiplier to move in. */
    private static final double MIN_DENSITY = 0.4;
    private static final double MAX_DENSITY = 2.5;

    /** Below this, a region is a ribbon rather than a territory. */
    private static final double RIBBON_COMPACTNESS = 0.40;

    /** Resources no region was large enough to hold, from the last assignment. */
    private static volatile List<String> unplaceable = List.of();

    /** Resources the last assignment could not place anywhere, for reporting. */
    public static List<String> unplaceable() {
        return unplaceable;
    }

    private RegionProfileAssigner() { }

    /**
     * Assigns every strategic profile to a region and works out each region's
     * multiplier for the common ores.
     *
     * <p>Deterministic: same regions and same profiles give the same answer on
     * every server and every restart, with no random source at all. The order
     * comes from the measurements, which is both reproducible and explicable —
     * an operator asking why a region got what it got can be shown the numbers.
     */
    public static List<Assignment> assign(List<Region> regions, List<Profile> profiles) {
        return assign(regions, profiles, List.of());
    }

    /**
     * As above, and additionally gives every region a common ore it is rich in
     * and one it is short of.
     *
     * <p>Exclusive resources work at the scale of a whole region, and a region
     * holds dozens of territories, so the nation next door almost always has
     * the same ones. That makes the exclusive tier a reason for blocs to trade
     * and no reason at all for neighbours to. The common ores fix that, because
     * they are everywhere and only the amount differs: nobody is ever blocked,
     * and two adjacent regions still have something to offer each other.
     *
     * <p>Which is why the specialties are coloured against the adjacency graph
     * rather than handed out in id order. Region ids come from the order basins
     * happened to be merged and say nothing about who borders whom; assigning
     * along them would leave neighbouring regions with the same specialty and
     * put the trading partner several regions away, which is the situation this
     * is meant to avoid.
     */
    public static List<Assignment> assign(List<Region> regions, List<Profile> profiles,
                                          List<String> commonMaterials) {
        if (regions.isEmpty()) {
            return List.of();
        }
        double median = medianArea(regions);
        Map<Integer, Double> density = new TreeMap<>();
        for (Region region : regions) {
            density.put(region.id(), baseDensity(region, median));
        }

        // Scarcest first, so the tightest constraints are satisfied while there
        // is still a choice of region left.
        List<Profile> ordered = new ArrayList<>(profiles);
        ordered.sort(Comparator.comparingInt(Profile::scarcity).thenComparing(Profile::id));

        Map<Integer, String> placed = new TreeMap<>();
        Set<Integer> continentsUsed = new HashSet<>();
        List<String> skipped = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            Profile profile = ordered.get(index);
            boolean scarcest = index == 0;
            boolean mostAbundant = index == ordered.size() - 1 && ordered.size() > 1;
            Region host = pick(regions, placed, continentsUsed, scarcest, mostAbundant, profile);
            if (host == null) {
                // More profiles than regions: start a second pass rather than
                // leaving a resource that exists nowhere in the world.
                continentsUsed.clear();
                host = pick(regions, Map.of(), continentsUsed, scarcest, mostAbundant, profile);
            }
            if (host == null) {
                // Every region is too small to hold it. Better nowhere than in a
                // region that would claim it and not have it.
                skipped.add(profile.id());
                continue;
            }
            placed.merge(host.id(), profile.id(), (a, b) -> a + "," + b);
            continentsUsed.add(host.continent());
        }

        unplaceable = List.copyOf(skipped);
        Map<Integer, String> specialty = colourByAdjacency(regions, commonMaterials);

        List<Assignment> out = new ArrayList<>();
        for (Region region : regions) {
            String rich = specialty.get(region.id());
            out.add(new Assignment(region.id(), placed.get(region.id()),
                    density.get(region.id()), rich, shortageFor(rich, commonMaterials)));
        }
        return List.copyOf(out);
    }

    /**
     * Gives each region a specialty differing from all of its neighbours.
     *
     * <p>Greedy graph colouring, taking the most connected regions first so the
     * hardest constraints are met while every colour is still free. Regions are
     * walked in a fixed order and colours picked in a fixed order, so the result
     * is the same on every server.
     */
    private static Map<Integer, String> colourByAdjacency(List<Region> regions,
                                                          List<String> materials) {
        Map<Integer, String> chosen = new TreeMap<>();
        if (materials.isEmpty()) {
            return chosen;
        }
        Map<String, Integer> used = new java.util.HashMap<>();
        List<Region> order = new ArrayList<>(regions);
        order.sort(Comparator.comparingInt((Region r) -> -r.neighbours().size())
                .thenComparingInt(Region::id));
        for (Region region : order) {
            Set<String> taken = new HashSet<>();
            for (Integer neighbour : region.neighbours()) {
                String neighbourSpecialty = chosen.get(neighbour);
                if (neighbourSpecialty != null) {
                    taken.add(neighbourSpecialty);
                }
            }
            // Among the colours that do not clash, take the one used least so
            // far. Simply taking the first leaves whole materials unused: the
            // production map's adjacency graph needs only two colours, so a
            // first-fit colouring gave copper to nobody and made it the shortage
            // in five regions out of eight -- of a metal Create needs by the
            // stack. Balancing usage is what puts every common ore somewhere.
            String pick = materials.stream()
                    .filter(material -> !taken.contains(material))
                    .min(Comparator.comparingInt((String material) -> used.getOrDefault(material, 0))
                            .thenComparing(materials::indexOf))
                    // More neighbours than materials: a repeat is unavoidable,
                    // and a duplicate specialty beats no specialty.
                    .orElse(materials.get(region.id() % materials.size()));
            chosen.put(region.id(), pick);
            used.merge(pick, 1, Integer::sum);
        }
        return chosen;
    }

    /** The material a region is short of: the next one after its specialty. */
    private static String shortageFor(String specialty, List<String> materials) {
        if (specialty == null || materials.size() < 2) {
            return null;
        }
        int index = materials.indexOf(specialty);
        return materials.get((index + 1) % materials.size());
    }

    private static double clampDensity(double density) {
        return Math.round(Math.min(MAX_DENSITY, Math.max(MIN_DENSITY, density)) * 100.0) / 100.0;
    }

    /**
     * The next region to hand a profile to.
     *
     * <p>Prefers a region with nothing yet, then one on a continent that has
     * nothing yet. Among those the two ends of the scarcity order pull in
     * opposite directions, which is the point: the scarcest resource goes to a
     * region that can hold it, skipping ribbons and the spawn region, while the
     * most abundant one goes to the weakest region left. A ribbon will change
     * hands; what it holds should be worth taking and survivable to lose, and
     * bulk is exactly that. Everything in between simply takes the largest
     * region available.
     */
    private static Region pick(List<Region> regions, Map<Integer, String> placed,
                               Set<Integer> continentsUsed, boolean scarcest,
                               boolean mostAbundant, Profile profile) {
        Comparator<Region> byNeed = mostAbundant
                ? Comparator.comparingDouble(RegionProfileAssigner::holdability)
                : Comparator.comparingDouble(region -> -holdability(region));
        return regions.stream()
                .filter(region -> !placed.containsKey(region.id()))
                .filter(region -> !scarcest || suitableForTheScarcest(region))
                .filter(region -> canHold(region, profile))
                .min(Comparator
                        .comparing((Region region) -> continentsUsed.contains(region.continent()))
                        .thenComparing(byNeed)
                        .thenComparingInt(Region::id))
                .orElse(null);
    }

    /**
     * Whether a region is big enough for this resource to actually appear in it.
     *
     * <p>Skipped when the density is unknown, because refusing every region on
     * a missing measurement would leave the world with no exclusive resources
     * at all -- the wrong way to fail.
     */
    static boolean canHold(Region region, Profile profile) {
        return profile.depositsPerCell() <= 0
                || profile.expectedIn(region) >= MINIMUM_EXPECTED_DEPOSITS;
    }

    /**
     * How well a region can be held: area weighted by how solid its shape is.
     *
     * <p>Area alone would call the coastal ribbon merely small, when its real
     * problem is that every part of it is near open water. Multiplying by the
     * fill of its bounding box separates a compact province from a strip of the
     * same size.
     */
    static double holdability(Region region) {
        return region.areaCells() * Math.max(0.0, region.compactness());
    }

    /**
     * Whether a region can hold the scarcest resource.
     *
     * <p>A ribbon can be reached from the sea along its whole length and has no
     * depth to fall back into, and the spawn region would hand whoever starts
     * there an opening nobody else can answer.
     */
    private static boolean suitableForTheScarcest(Region region) {
        return region.compactness() >= RIBBON_COMPACTNESS && !region.hasWorldSpawn();
    }

    /** The common-ore multiplier for a region, from its area against the median. */
    static double baseDensity(Region region, double medianAreaCells) {
        if (region.areaCells() <= 0 || medianAreaCells <= 0) {
            return 1.0;
        }
        return clampDensity(Math.pow(medianAreaCells / region.areaCells(), AREA_CORRECTION));
    }

    private static double medianArea(List<Region> regions) {
        int[] areas = regions.stream().mapToInt(Region::areaCells).sorted().toArray();
        int middle = areas.length / 2;
        return areas.length % 2 == 1
                ? areas[middle]
                : (areas[middle - 1] + areas[middle]) / 2.0;
    }
}
