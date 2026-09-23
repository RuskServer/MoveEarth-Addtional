package com.ruskserver.moveearth_addtional.s2.nation;

import com.ruskserver.moveearth_addtional.s2.nation.NationFoundationSite.Reading;
import com.ruskserver.moveearth_addtional.s2.nation.NationFoundationSite.Verdict;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NationFoundationSiteTest {

    /** A plain grassy field: the block gives way, the dirt underneath holds. */
    private static Reading ordinaryGround() {
        return new Reading(true, true, true, true, false, true, false, true, false);
    }

    @Test
    void grassOverSolidGroundIsAValidSite() {
        assertEquals(Verdict.OK, NationFoundationSite.judge(ordinaryGround()));
    }

    @Test
    void eachFaultIsNamedRatherThanLumpedTogether() {
        assertEquals(Verdict.UNLOADED, NationFoundationSite.judge(Reading.unloaded()));
        assertEquals(Verdict.OUT_OF_WORLD, NationFoundationSite.judge(
                new Reading(true, false, true, true, false, true, false, true, false)));
        assertEquals(Verdict.TOO_FAR, NationFoundationSite.judge(
                new Reading(true, true, false, true, false, true, false, true, false)));
        assertEquals(Verdict.NO_SIGHT, NationFoundationSite.judge(
                new Reading(true, true, true, false, false, true, false, true, false)));
        assertEquals(Verdict.ON_VEHICLE, NationFoundationSite.judge(
                new Reading(true, true, true, true, true, true, false, true, false)));
        assertEquals(Verdict.OCCUPIED, NationFoundationSite.judge(
                new Reading(true, true, true, true, false, false, false, true, false)));
        assertEquals(Verdict.FLOODED, NationFoundationSite.judge(
                new Reading(true, true, true, true, false, true, true, true, false)));
        assertEquals(Verdict.NO_SUPPORT, NationFoundationSite.judge(
                new Reading(true, true, true, true, false, true, false, false, false)));
        assertEquals(Verdict.ENTITY_IN_THE_WAY, NationFoundationSite.judge(
                new Reading(true, true, true, true, false, true, false, true, true)));
    }

    @Test
    void whatThePlayerCannotSeeIsReportedBeforeWhatIsInIt() {
        // Everything about the block is wrong as well, but a site out of range or
        // out of sight may not be the block the player thinks they are aiming at,
        // so naming the block's fault first would send them to fix the wrong thing.
        assertEquals(Verdict.TOO_FAR, NationFoundationSite.judge(
                new Reading(true, true, false, false, true, false, true, false, true)));
        assertEquals(Verdict.NO_SIGHT, NationFoundationSite.judge(
                new Reading(true, true, true, false, true, false, true, false, true)));
        assertEquals(Verdict.UNLOADED, NationFoundationSite.judge(
                new Reading(false, true, true, true, false, true, false, true, false)));
    }

    @Test
    void everyRefusalCarriesASentenceAndOkCarriesNone() {
        for (Verdict verdict : Verdict.values()) {
            if (verdict == Verdict.OK) {
                assertTrue(verdict.allowed());
                assertThrows(IllegalStateException.class, verdict::messageKey);
            } else {
                assertFalse(verdict.allowed());
                assertTrue(verdict.messageKey()
                        .startsWith("message.moveearth_addtional.nation.foundation."));
            }
        }
    }

    @Test
    void reachIsTheSameNumberOnBothSidesOfTheSquare() {
        assertEquals(NationFoundationSite.REACH * NationFoundationSite.REACH,
                NationFoundationSite.REACH_SQR);
    }
}
