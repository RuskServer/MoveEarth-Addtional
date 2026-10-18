package com.ruskserver.moveearth_addtional.detector;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.ruskserver.moveearth_addtional.detector.DetectorAccessPolicy.Relation.INTRUDER;
import static com.ruskserver.moveearth_addtional.detector.DetectorAccessPolicy.Relation.MEMBER;
import static com.ruskserver.moveearth_addtional.detector.DetectorAccessPolicy.Relation.VISITOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetectorAccessPolicyTest {
    private final UUID nation = UUID.randomUUID();
    private final UUID otherNation = UUID.randomUUID();
    private final UUID placer = UUID.randomUUID();
    private final UUID citizen = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    @Test
    void everyCitizenIsFamilyToTheirNationsDetector() {
        assertEquals(MEMBER, DetectorAccessPolicy.relation(nation, placer, citizen, nation, false));
    }

    @Test
    void thePlacerWhoLeftTheNationIsAnIntruderLikeAnyoneElse() {
        assertEquals(INTRUDER, DetectorAccessPolicy.relation(nation, placer, placer, otherNation, false));
        assertEquals(INTRUDER, DetectorAccessPolicy.relation(nation, placer, placer, null, false));
    }

    @Test
    void alliesAreVisitorsAndOthersIntruders() {
        assertEquals(VISITOR, DetectorAccessPolicy.relation(nation, placer, stranger, otherNation, true));
        assertEquals(INTRUDER, DetectorAccessPolicy.relation(nation, placer, stranger, otherNation, false));
    }

    @Test
    void aPersonalDetectorKnowsOnlyItsOwner() {
        assertEquals(MEMBER, DetectorAccessPolicy.relation(null, placer, placer, null, false));
        assertEquals(INTRUDER, DetectorAccessPolicy.relation(null, placer, stranger, null, false));
    }

    @Test
    void managingANationsDetectorTakesItsRights() {
        assertTrue(DetectorAccessPolicy.mayManage(nation, placer, citizen, nation, true, false));
        assertFalse(DetectorAccessPolicy.mayManage(nation, placer, citizen, nation, false, false));
        assertFalse(DetectorAccessPolicy.mayManage(nation, placer, placer, otherNation, true, false));
        assertTrue(DetectorAccessPolicy.mayManage(nation, placer, stranger, null, false, true));
    }

    @Test
    void managingAPersonalDetectorIsTheOwnersAlone() {
        assertTrue(DetectorAccessPolicy.mayManage(null, placer, placer, null, false, false));
        assertFalse(DetectorAccessPolicy.mayManage(null, placer, stranger, null, false, false));
    }

    @Test
    void placingForANationTakesItsRightsButNotWithoutOne() {
        assertTrue(DetectorAccessPolicy.mayPlace(null, false, false));
        assertTrue(DetectorAccessPolicy.mayPlace(nation, true, false));
        assertFalse(DetectorAccessPolicy.mayPlace(nation, false, false));
        assertTrue(DetectorAccessPolicy.mayPlace(nation, false, true));
    }

    @Test
    void everyCitizenMayReadTheScreen() {
        assertTrue(DetectorAccessPolicy.mayView(nation, placer, citizen, nation));
        assertFalse(DetectorAccessPolicy.mayView(nation, placer, stranger, otherNation));
    }
}
