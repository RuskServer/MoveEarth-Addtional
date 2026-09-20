package com.ruskserver.moveearth_addtional.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ruskserver.moveearth_addtional.region.RegionProfileAssigner.Region;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegionSurveyTest {

    private static RegionGrid.Cells grid(String... rows) {
        return new RegionGrid.Cells() {
            @Override public int at(int cellX, int cellZ) {
                return rows[cellZ].charAt(cellX) - '0';
            }
            @Override public int size() {
                return rows.length;
            }
        };
    }

    private static Region find(List<Region> regions, int id) {
        return regions.stream().filter(r -> r.id() == id).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("area, continent and neighbours come off the layers")
    void measuresTheBasics() {
        var regions = grid(
                "1122",
                "1122",
                "3322",
                "3300");
        var continents = grid(
                "1111",
                "1111",
                "1111",
                "1100");
        List<Region> surveyed = RegionSurvey.survey(regions, continents, -1, -1);
        assertEquals(3, surveyed.size());
        assertEquals(4, find(surveyed, 1).areaCells());
        assertEquals(6, find(surveyed, 2).areaCells());
        assertEquals(4, find(surveyed, 3).areaCells());
        assertEquals(1, find(surveyed, 1).continent());
        assertEquals(Set.of(2, 3), find(surveyed, 1).neighbours());
        assertEquals(Set.of(1, 2), find(surveyed, 3).neighbours());
    }

    @Test
    @DisplayName("adjacency is recorded from both sides")
    void adjacencyIsSymmetric() {
        var regions = grid("12", "12");
        var continents = grid("11", "11");
        List<Region> surveyed = RegionSurvey.survey(regions, continents, -1, -1);
        assertTrue(find(surveyed, 1).neighbours().contains(2));
        assertTrue(find(surveyed, 2).neighbours().contains(1));
    }

    @Test
    @DisplayName("a straight ribbon scores low, although it fills its bounding box")
    void compactnessCatchesThinShapes() {
        // The whole point: region 1 is a 1x4 column, which fills its bounding
        // box exactly. Measuring fill would score it a perfect 1 and call the
        // thinnest possible region the most compact one.
        var cells = grid(
                "1022",
                "1022",
                "1000",
                "1000");
        var continents = grid("1111", "1111", "1111", "1111");
        List<Region> surveyed = RegionSurvey.survey(cells, continents, -1, -1);
        assertEquals(0.25, find(surveyed, 1).compactness(), 1e-9);
        assertEquals(1.0, find(surveyed, 2).compactness(), 1e-9, "a square scores 1");
        assertTrue(find(surveyed, 1).compactness() < find(surveyed, 2).compactness());
    }

    @Test
    @DisplayName("the spawn region is flagged, and only that one")
    void spawnIsFlagged() {
        var regions = grid("11", "22");
        var continents = grid("11", "11");
        List<Region> surveyed = RegionSurvey.survey(regions, continents, 0, 1);
        assertFalse(find(surveyed, 1).hasWorldSpawn());
        assertTrue(find(surveyed, 2).hasWorldSpawn());
    }

    @Test
    @DisplayName("a spawn out at sea flags nothing")
    void spawnAtSeaFlagsNothing() {
        var regions = grid("10", "20");
        var continents = grid("10", "10");
        for (Region region : RegionSurvey.survey(regions, continents, 1, 0)) {
            assertFalse(region.hasWorldSpawn());
        }
    }

    @Test
    @DisplayName("an all-sea tile surveys to nothing rather than crashing")
    void handlesAnEmptyTile() {
        assertEquals(List.of(), RegionSurvey.survey(grid("00", "00"), grid("00", "00"), 0, 0));
    }
}
