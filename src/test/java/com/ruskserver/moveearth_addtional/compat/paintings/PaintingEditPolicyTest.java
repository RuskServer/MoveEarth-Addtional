package com.ruskserver.moveearth_addtional.compat.paintings;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaintingEditPolicyTest {
    private static final UUID HOST = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Test
    void onANationsLandOnlyItAndItsAlliesMaySwapAPainting() {
        assertTrue(PaintingEditPolicy.mayEdit(false, HOST, HOST, false));
        assertTrue(PaintingEditPolicy.mayEdit(false, HOST, OTHER, true));
        assertFalse(PaintingEditPolicy.mayEdit(false, HOST, OTHER, false));
        assertFalse(PaintingEditPolicy.mayEdit(false, HOST, null, false));
    }

    @Test
    void noOnesLandIsOpenAndStaffMayAlwaysEdit() {
        assertTrue(PaintingEditPolicy.mayEdit(false, null, null, false));
        assertTrue(PaintingEditPolicy.mayEdit(true, HOST, null, false));
    }
}
