package com.ruskserver.moveearth_addtional.s2.nation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NationTabOrderTest {
    @Test
    void ownNationThenAlliesNeutralHostileAndNoNationLast() {
        int own = NationTabOrder.group(NationNameplateRelation.OWN_NATION, true);
        int ally = NationTabOrder.group(NationNameplateRelation.ALLY, true);
        int neutral = NationTabOrder.group(NationNameplateRelation.NEUTRAL, true);
        int hostile = NationTabOrder.group(NationNameplateRelation.HOSTILE, true);
        int none = NationTabOrder.group(NationNameplateRelation.NEUTRAL, false);
        assertTrue(own < ally && ally < neutral && neutral < hostile && hostile < none);
    }

    @Test
    void aPlayerWithNoNationIsLastWhateverTheRelation() {
        assertEquals(4, NationTabOrder.group(NationNameplateRelation.HOSTILE, false));
        assertEquals(4, NationTabOrder.group(null, true));
    }
}
