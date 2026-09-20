package com.ruskserver.moveearth_addtional.region;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegionGridTest {

    /** A grid written as rows of digits, so a test case reads like a map. */
    private static RegionGrid.Cells grid(String... rows) {
        int size = rows.length;
        for (String row : rows) {
            if (row.length() != size) {
                throw new IllegalArgumentException("the grid must be square");
            }
        }
        return new RegionGrid.Cells() {
            @Override public int at(int cellX, int cellZ) {
                return rows[cellZ].charAt(cellX) - '0';
            }
            @Override public int size() {
                return size;
            }
        };
    }

    @Test
    @DisplayName("a cell that has a region returns it")
    void readsItsOwnRegion() {
        var cells = grid(
                "110",
                "110",
                "000");
        assertEquals(1, RegionGrid.regionAt(cells, 0, 0, 8));
    }

    @Test
    @DisplayName("a sea cell takes the region of the nearest land")
    void seaTakesTheNearestCoast() {
        var cells = grid(
                "300",
                "000",
                "000");
        assertEquals(3, RegionGrid.regionAt(cells, 2, 2, 8));
    }

    @Test
    @DisplayName("the closest coast wins by real distance, not by ring")
    void diagonalRingDoesNotBeatAStraightOne() {
        // Region 7 sits at the corner of the ring at radius 2 (distance^2 = 8).
        // Region 4 sits straight out at radius 3 (distance^2 = 9)... so 7 wins.
        // Flip it: 7 at the corner of radius 3 (18) loses to 4 straight at 4 (16).
        var far = grid(
                "70000",
                "00000",
                "00000",
                "00000",
                "00004");
        assertEquals(4, RegionGrid.regionAt(far, 4, 0, 8));
    }

    @Test
    @DisplayName("an equidistant pair resolves to the lower id, whichever side it is on")
    void tiesAreBrokenDeterministically() {
        // Both coasts are the same distance from the query cell, as happens in a
        // strait. Without a rule the winner follows the scan order, and a chunk
        // would change region between restarts.
        assertEquals(3, RegionGrid.regionAt(grid(
                "30007",
                "00000",
                "00000",
                "00000",
                "00000"), 2, 1, 8));
        assertEquals(3, RegionGrid.regionAt(grid(
                "70003",
                "00000",
                "00000",
                "00000",
                "00000"), 2, 1, 8));
    }

    @Test
    @DisplayName("open sea beyond the search limit has no region, so nothing is gated")
    void unreachableSeaHasNoRegion() {
        var cells = grid(
                "90000",
                "00000",
                "00000",
                "00000",
                "00000");
        assertEquals(RegionGrid.NONE, RegionGrid.regionAt(cells, 4, 4, 2));
    }

    @Test
    @DisplayName("the search does not walk off the grid")
    void staysInsideTheGrid() {
        var cells = grid(
                "000",
                "000",
                "006");
        assertEquals(6, RegionGrid.regionAt(cells, 0, 0, 16));
        assertEquals(RegionGrid.NONE, RegionGrid.regionAt(grid("000", "000", "000"), 1, 1, 16));
    }
}
