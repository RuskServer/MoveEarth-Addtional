package com.ruskserver.moveearth_addtional.warehouse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarehouseZoneViewTest {
    private static final int MIN_X = 100;
    private static final int MIN_Y = 70;
    private static final int MIN_Z = 200;

    @Test
    void onlyADormantWarehouseDuringOpeningHoursIsArmed() {
        assertEquals(WarehouseZoneView.State.ARMED, WarehouseZoneView.state(true, false, true));
        assertEquals(WarehouseZoneView.State.QUIET, WarehouseZoneView.state(true, false, false));
        assertEquals(WarehouseZoneView.State.FIGHTING, WarehouseZoneView.state(false, true, true));
        assertEquals(WarehouseZoneView.State.FIGHTING, WarehouseZoneView.state(false, true, false));
        // Loot, cooldown and the like: entering does nothing.
        assertEquals(WarehouseZoneView.State.QUIET, WarehouseZoneView.state(false, false, true));
    }

    @Test
    void playersAreWarnedJustOutsideTheFootprintButNotInsideOrFarAway() {
        assertTrue(WarehouseZoneView.approaching(MIN_X, MIN_Y, MIN_Z, MIN_X - 3, MIN_Y, MIN_Z + 5));
        assertTrue(WarehouseZoneView.approaching(MIN_X, MIN_Y, MIN_Z,
                MIN_X + WarehouseSitePolicy.WIDTH - 1 + WarehouseZoneView.WARNING_MARGIN, MIN_Y, MIN_Z));
        assertFalse(WarehouseZoneView.approaching(MIN_X, MIN_Y, MIN_Z, MIN_X + 5, MIN_Y + 1, MIN_Z + 5));
        assertFalse(WarehouseZoneView.approaching(MIN_X, MIN_Y, MIN_Z,
                MIN_X - WarehouseZoneView.WARNING_MARGIN - 1, MIN_Y, MIN_Z));
        assertFalse(WarehouseZoneView.approaching(MIN_X, MIN_Y, MIN_Z, MIN_X - 3, MIN_Y + 60, MIN_Z + 5));
    }

    @Test
    void entryStartsExactlyWhereTheFieldIsDrawn() {
        // The field's walls sit on the footprint edges; one step past an edge is inside.
        assertEquals(0.0D, WarehouseZoneView.distanceToFootprint(MIN_X, MIN_Z, MIN_X + 0.5D, MIN_Z + 0.5D));
        assertTrue(WarehouseSitePolicy.insideStructure(MIN_X, MIN_Y, MIN_Z, MIN_X, MIN_Y, MIN_Z));
        assertFalse(WarehouseSitePolicy.insideStructure(MIN_X, MIN_Y, MIN_Z, MIN_X - 1, MIN_Y, MIN_Z));
        assertEquals(1.0D, WarehouseZoneView.distanceToFootprint(MIN_X, MIN_Z, MIN_X - 1.0D, MIN_Z + 3.0D), 1e-9);
        assertEquals(5.0D, WarehouseZoneView.distanceToFootprint(MIN_X, MIN_Z,
                MIN_X + WarehouseSitePolicy.WIDTH + 3.0D, MIN_Z + WarehouseSitePolicy.LENGTH + 4.0D), 1e-9);
    }

    @Test
    void fieldFadesInWithDistance() {
        assertEquals(1.0F, WarehouseZoneView.fieldAlpha(0.0D, 32.0D));
        assertEquals(0.0F, WarehouseZoneView.fieldAlpha(32.0D, 32.0D));
        assertEquals(0.25F, WarehouseZoneView.fieldAlpha(16.0D, 32.0D), 1e-6F);
        assertTrue(WarehouseZoneView.fieldAlpha(4.0D, 32.0D) > WarehouseZoneView.fieldAlpha(20.0D, 32.0D));
    }
}
