package com.ruskserver.moveearth_addtional.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ruskserver.moveearth_addtional.region.RegionProfileAssigner.Assignment;
import com.ruskserver.moveearth_addtional.region.RegionProfileAssigner.Profile;
import com.ruskserver.moveearth_addtional.region.RegionProfileAssigner.Region;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegionProfileAssignerTest {

    /**
     * The production tile, measured. Region 1 is the coastal ribbon: smallest
     * by area and filling only a third of its bounding box.
     */
    private static List<Region> productionRegions() {
        return List.of(
                new Region(1, 1, 29412, 0.20, false, Set.of(3, 4)),
                new Region(2, 1, 64230, 0.68, false, Set.of(3)),
                new Region(3, 1, 67697, 0.44, false, Set.of(1, 2, 5)),
                new Region(4, 1, 51684, 0.50, false, Set.of(1, 5)),
                new Region(5, 1, 26001, 0.42, false, Set.of(3, 4)),
                new Region(6, 2, 34335, 0.55, false, Set.of(7)),
                new Region(7, 2, 17499, 0.33, false, Set.of(6, 8)),
                new Region(8, 2, 23709, 0.53, false, Set.of(7)));
    }

    /** The common tier from the plan. */
    private static final List<String> COMMON = List.of("coal", "iron", "copper");

    /** The strategic tier from the plan: gold, diamond, emerald. */
    private static List<Profile> strategicTier() {
        return List.of(new Profile("emerald", 1), new Profile("diamond", 2), new Profile("gold", 3));
    }

    private static Assignment of(List<Assignment> all, int regionId) {
        return all.stream().filter(a -> a.regionId() == regionId).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("a small region gets a higher common-ore multiplier than a large one")
    void smallRegionsAreCompensated() {
        List<Assignment> result = RegionProfileAssigner.assign(productionRegions(), strategicTier());
        double smallest = of(result, 1).baseDensity();
        double largest = of(result, 2).baseDensity();
        assertTrue(smallest > largest,
                "region 1 is far smaller than region 2 and should be denser");
    }

    @Test
    @DisplayName("the correction is partial, so holding more land still pays")
    void correctionDoesNotEqualiseTotals() {
        List<Assignment> result = RegionProfileAssigner.assign(productionRegions(), strategicTier());
        double smallTotal = 29412 * of(result, 1).baseDensity();
        double largeTotal = 64230 * of(result, 2).baseDensity();
        assertTrue(largeTotal > smallTotal,
                "full compensation would make the small region the best place to mine");
        // ...but the gap must be narrower than area alone would give.
        assertTrue(largeTotal / smallTotal < 64230.0 / 29412.0);
    }

    @Test
    @DisplayName("multipliers stay inside the band the tier design allows")
    void densityStaysInBand() {
        List<Region> extreme = List.of(
                new Region(1, 1, 100, 0.7, false),
                new Region(2, 1, 50_000, 0.7, false),
                new Region(3, 1, 10_000_000, 0.7, false));
        for (Assignment a : RegionProfileAssigner.assign(extreme, strategicTier())) {
            assertTrue(a.baseDensity() >= 0.4 && a.baseDensity() <= 2.5,
                    "density " + a.baseDensity() + " left the 0.4-2.5 band");
        }
    }

    @Test
    @DisplayName("the scarcest resource avoids the coastal ribbon")
    void scarcestSkipsTheRibbon() {
        List<Assignment> result = RegionProfileAssigner.assign(productionRegions(), strategicTier());
        assertFalse("emerald".equals(of(result, 1).profileId()),
                "region 1 has no depth to defend and must not hold the scarcest resource");
    }

    @Test
    @DisplayName("the most abundant resource goes to the region hardest to hold")
    void bulkGoesToTheRibbon() {
        List<Profile> withQuartz = List.of(
                new Profile("emerald", 1), new Profile("diamond", 2),
                new Profile("gold", 3), new Profile("quartz", 4));
        List<Region> regions = productionRegions();
        List<Assignment> result = RegionProfileAssigner.assign(regions, withQuartz);
        // Asserted as a property, not a region id: which region is weakest is a
        // measurement that moves when the terrain or the shape metric does, and
        // a test naming region 1 would only be testing the fixture.
        Region weakest = regions.stream()
                .min(Comparator.comparingDouble(r -> r.areaCells() * r.compactness()))
                .orElseThrow();
        assertEquals("quartz", of(result, weakest.id()).profileId(),
                "the weakest region will change hands, so it should hold the resource "
                        + "that is worth taking and survivable to lose");
        Region strongest = regions.stream()
                .max(Comparator.comparingDouble(r -> r.areaCells() * r.compactness()))
                .orElseThrow();
        assertEquals("emerald", of(result, strongest.id()).profileId(),
                "the scarcest belongs in the region best able to hold it");
    }

    @Test
    @DisplayName("shape counts, not just area, when choosing the weakest region")
    void shapeDecidesTheWeakest() {
        // Same area, different shapes: the ribbon should take the bulk resource
        // even though neither is smaller than the other.
        List<Region> regions = List.of(
                new Region(1, 1, 60_000, 0.30, false),
                new Region(2, 1, 60_000, 0.75, false),
                new Region(3, 2, 90_000, 0.75, false));
        List<Assignment> result = RegionProfileAssigner.assign(regions,
                List.of(new Profile("emerald", 1), new Profile("gold", 3)));
        assertEquals("gold", of(result, 1).profileId());
        assertFalse("emerald".equals(of(result, 1).profileId()));
    }

    @Test
    @DisplayName("the scarcest resource avoids the spawn region")
    void scarcestSkipsSpawn() {
        List<Region> regions = List.of(
                new Region(1, 1, 90_000, 0.7, true),
                new Region(2, 1, 80_000, 0.7, false),
                new Region(3, 2, 70_000, 0.7, false));
        List<Assignment> result = RegionProfileAssigner.assign(regions, strategicTier());
        assertFalse("emerald".equals(of(result, 1).profileId()));
    }

    @Test
    @DisplayName("every strategic resource exists somewhere in the world")
    void nothingIsLeftOut() {
        List<String> placed = RegionProfileAssigner.assign(productionRegions(), strategicTier())
                .stream().map(Assignment::profileId).filter(p -> p != null)
                .flatMap(p -> List.of(p.split(",")).stream()).collect(Collectors.toList());
        assertTrue(placed.containsAll(List.of("gold", "diamond", "emerald")),
                "a resource that exists nowhere cannot be traded for; got " + placed);
    }

    @Test
    @DisplayName("more resources than regions still places all of them")
    void moreProfilesThanRegions() {
        List<Region> two = List.of(
                new Region(1, 1, 60_000, 0.7, false),
                new Region(2, 2, 50_000, 0.7, false));
        List<Assignment> result = RegionProfileAssigner.assign(two, strategicTier());
        String all = result.stream().map(Assignment::profileId)
                .filter(p -> p != null).collect(Collectors.joining(","));
        for (String id : List.of("gold", "diamond", "emerald")) {
            assertTrue(all.contains(id), id + " was dropped; got " + all);
        }
    }

    @Test
    @DisplayName("resources spread across continents before doubling up on one")
    void continentsAreSpreadFirst() {
        List<Region> regions = List.of(
                new Region(1, 1, 90_000, 0.7, false),
                new Region(2, 1, 80_000, 0.7, false),
                new Region(3, 2, 70_000, 0.7, false));
        List<Assignment> result = RegionProfileAssigner.assign(regions, strategicTier());
        assertNotNull(of(result, 3).profileId(),
                "the lone region on continent 2 should be used before continent 1 gets a third");
    }

    @Test
    @DisplayName("neighbouring regions never share a specialty")
    void neighboursDifferSoTheyCanTrade() {
        List<Region> regions = productionRegions();
        List<Assignment> result = RegionProfileAssigner.assign(regions, strategicTier(), COMMON);
        for (Region region : regions) {
            String mine = of(result, region.id()).specialty();
            for (int neighbour : region.neighbours()) {
                assertNotEquals(mine, of(result, neighbour).specialty(),
                        "regions " + region.id() + " and " + neighbour + " border each other "
                                + "and would have nothing to offer one another");
            }
        }
    }

    @Test
    @DisplayName("a region is richer in its specialty and poorer in its shortage")
    void specialtyAndShortageMoveTheMultiplier() {
        Assignment a = of(RegionProfileAssigner.assign(productionRegions(), strategicTier(), COMMON), 2);
        assertTrue(a.multiplierFor(a.specialty()) > a.baseDensity());
        assertTrue(a.multiplierFor(a.shortage()) < a.baseDensity());
        assertEquals(a.baseDensity(), a.multiplierFor("some_other_ore"), 1e-9);
    }

    @Test
    @DisplayName("every multiplier stays inside the tier band")
    void specialisedMultipliersStayInBand() {
        for (Assignment a : RegionProfileAssigner.assign(productionRegions(), strategicTier(), COMMON)) {
            for (String material : COMMON) {
                double m = a.multiplierFor(material);
                assertTrue(m >= 0.4 && m <= 2.5, material + " reached " + m);
            }
        }
    }

    @Test
    @DisplayName("no common ores means no specialty rather than a crash")
    void worksWithoutCommonMaterials() {
        for (Assignment a : RegionProfileAssigner.assign(productionRegions(), strategicTier())) {
            assertEquals(a.baseDensity(), a.multiplierFor("coal"), 1e-9);
        }
    }

    @Test
    @DisplayName("the same inputs always give the same answer")
    void isDeterministic() {
        List<Assignment> first = RegionProfileAssigner.assign(productionRegions(), strategicTier());
        List<Assignment> second = RegionProfileAssigner.assign(productionRegions(), strategicTier());
        assertEquals(first, second);
    }

    @Test
    @DisplayName("no regions means no assignments rather than a crash")
    void handlesAnEmptyWorld() {
        assertEquals(List.of(), RegionProfileAssigner.assign(List.of(), strategicTier()));
    }
}
