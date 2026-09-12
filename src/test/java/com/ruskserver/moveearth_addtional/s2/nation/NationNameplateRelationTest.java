package com.ruskserver.moveearth_addtional.s2.nation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NationNameplateRelationTest {
    @Test
    void relationshipPriorityIsStable() {
        assertEquals(NationNameplateRelation.OWN_NATION,
                NationNameplateRelation.resolve(true, true, true));
        assertEquals(NationNameplateRelation.ALLY,
                NationNameplateRelation.resolve(false, true, true));
        assertEquals(NationNameplateRelation.HOSTILE,
                NationNameplateRelation.resolve(false, false, true));
        assertEquals(NationNameplateRelation.NEUTRAL,
                NationNameplateRelation.resolve(false, false, false));
    }
}
