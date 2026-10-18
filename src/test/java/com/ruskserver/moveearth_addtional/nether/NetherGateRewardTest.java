package com.ruskserver.moveearth_addtional.nether;

import org.junit.jupiter.api.Test;

import static com.ruskserver.moveearth_addtional.nether.NetherGateReward.Recipient.GENERATOR;
import static com.ruskserver.moveearth_addtional.nether.NetherGateReward.Recipient.LAST_ATTACKER;
import static com.ruskserver.moveearth_addtional.nether.NetherGateReward.Recipient.NEAREST_CITIZEN;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NetherGateRewardTest {
    @Test
    void aCitizenOrAllyWhoLandsTheFinalBlowTakesTheShards() {
        assertEquals(LAST_ATTACKER, NetherGateReward.recipient(true, true, true));
        assertEquals(LAST_ATTACKER, NetherGateReward.recipient(true, true, false));
    }

    @Test
    void anOutsiderWhoStealsTheLastKillGetsNothing() {
        assertEquals(NEAREST_CITIZEN, NetherGateReward.recipient(true, false, true));
        assertEquals(GENERATOR, NetherGateReward.recipient(true, false, false));
    }

    @Test
    void withNoKillerTheShardsGoToACitizenOrTheGenerator() {
        assertEquals(NEAREST_CITIZEN, NetherGateReward.recipient(false, false, true));
        assertEquals(GENERATOR, NetherGateReward.recipient(false, false, false));
    }
}
